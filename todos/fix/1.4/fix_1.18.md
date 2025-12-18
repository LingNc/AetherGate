问题：还有最后一点小bug就是我发现现在的情况下激活成功之后然后在有充能状态下那个潮涌核心在大的状态下上面有粒子效果的圆环这个圆环应该是在有耐久情况下一直都有的但是现在当服务器重启之后还有隔一段时好像是间就会出现这个上面粒子效果没有了并且打掉世界锚点之后实体依然存在的问题但是在之前版本中创建的就没有这个问题重启之后还能正常使用就是新的有这个问题为什么如何解决
解决：
这是一个非常典型的 **实体持久化（Entity Persistence）与插件内存状态不同步** 导致的问题。

### 核心原因分析

1.  **持久化冲突（幽灵实体产生原因）：**

      * Minecraft 默认会将世界中的实体（包括 `ItemDisplay` 和 `Interaction`）保存到磁盘（区块文件）中。
      * **当服务器重启时**：
        1.  Minecraft 从磁盘加载了旧的“悬浮核心”实体。
        2.  插件的 `coreDisplays` Map 被清空了（因为它是内存数据）。
        3.  插件在 `onEnable` -\> `loadExistingAltars` 中运行，发现这里有个祭坛，于是**新生成**了一个 `ItemDisplay` 实体。
      * **结果**：现在同一个位置有两个重叠的实体。
          * **实体 A (旧)**：由 Minecraft 加载，插件**不认识**它（没有它的 UUID）。它没有被分配粒子效果任务，所以它没有光环。
          * **实体 B (新)**：由插件刚刚生成，插件管理着它，它有粒子效果。

2.  **为何粒子效果消失/打不掉：**

      * 因为重叠，你看到的可能是 **实体 A**（旧的），它遮挡了 **实体 B**。
      * 当你打破锚点时，插件根据内存里的 UUID 删除了 **实体 B**。但是 **实体 A** 依然存在（因为插件不知道它的 UUID），变成了“幽灵方块”，永远留在那里。

### 1.18 修复计划

我们需要修改 `AltarService.java`，做两件事：

1.  **禁止保存**：告诉 Bukkit 不要把我们的视觉实体保存到磁盘（`setPersistent(false)`），这样服务器重启它们就会自动消失，完全由插件在启动时重建。
2.  **清理旧数据**：在生成新实体前，强制检查该位置是否已经有残留的实体，如果有，先杀掉。

请修改 `plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java`。

#### 修改 1：更新 `updateVisualState` 方法

找到该方法，替换为以下内容。重点是增加了 `clearOldEntities` 调用和 `d.setPersistent(false)`。

```java
    private void updateVisualState(Location blockLoc, boolean isActive) {
        World world = blockLoc.getWorld();
        if (world == null) {
            return;
        }
        // 先尝试通过 UUID 移除（正常流程）
        removeVisuals(blockLoc);

        double height = isActive ? 2.0 : 1.6;
        Location displayLoc = blockLoc.clone().add(0.5, height, 0.5);

        // [修复关键步 1]：在生成新实体前，清理该位置可能残留的“幽灵”实体
        // 防止重启后出现双重实体
        clearOldEntities(displayLoc, ItemDisplay.class);

        ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(org.bukkit.Material.CONDUIT));
            d.setBillboard(Display.Billboard.CENTER);
            // [修复关键步 2]：设置为不持久化。
            // 这样服务器关闭/重启时，实体会自动消失，不会保存到区块文件中。
            d.setPersistent(false);

            float scale = isActive ? 1.5f : 0.9f;
            d.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f),
                d.getTransformation().getLeftRotation(),
                new Vector3f(scale, scale, scale),
                d.getTransformation().getRightRotation()));
        });

        String mapKey = key(world.getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        coreDisplays.put(mapKey, display.getUniqueId());

        if (isActive) {
            placeCoreLight(mapKey, blockLoc);
            startCoreEffects(mapKey, display.getUniqueId(), displayLoc.clone());
        }
        spawnInteractionEntity(mapKey, displayLoc.clone(), isActive);
    }
```

#### 修改 2：更新 `spawnInteractionEntity` 方法

同样需要禁止交互实体的持久化，并清理旧的。

```java
    private void spawnInteractionEntity(String mapKey, Location center, boolean isActive) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        // [修复关键步 1]：清理残留的交互实体
        clearOldEntities(center, Interaction.class);

        float scale = isActive ? 1.5f : 0.9f;
        float width = Math.max(0.6f, 0.8f * scale);
        float height = Math.max(1.0f, 1.2f * scale);

        Interaction interaction = world.spawn(center, Interaction.class, entity -> {
            entity.setInteractionWidth(width);
            entity.setInteractionHeight(height);
            entity.setResponsive(true);
            // [修复关键步 2]：禁止保存到磁盘
            entity.setPersistent(false);
        });

        UUID uuid = interaction.getUniqueId();
        coreInteractions.put(mapKey, uuid);
        interactionAnchors.put(uuid, mapKey);
    }
```

#### 修改 3：添加辅助方法 `clearOldEntities`

在 `AltarService` 类中（建议放在 `spawnInteractionEntity` 下面）添加这个新方法，用于清除以前版本遗留的 BUG 实体：

```java
    /**
     * 清理指定位置附近的特定类型实体，防止重叠。
     * 用于修复因服务器重启导致的实体残留问题。
     */
    private void clearOldEntities(Location location, Class<? extends Entity> clazz) {
        if (location.getWorld() == null) return;
        // 搜索半径设为 0.5 足够覆盖同一个坐标的实体
        Collection<Entity> nearby = location.getWorld().getNearbyEntities(location, 0.5, 0.5, 0.5);
        for (Entity entity : nearby) {
            // 如果实体类型匹配，且是我们插件生成的（可以通过类型判断，或者如果有Tag更好）
            // 在这里我们主要通过精确的位置和类型来判断
            if (clazz.isInstance(entity)) {
                entity.remove();
            }
        }
    }
```

### 总结

做了这些修改后：

1.  **`setPersistent(false)`**：确保从今以后，无论服务器怎么重启，这些视觉实体都会随服务器关闭而消失，`onEnable` 时插件会重新生成它们，永远保持 1 个实体。
2.  **`clearOldEntities(...)`**：当你上传新插件并重启服务器时，这个方法会自动扫描并清除掉你之前遇到的那些“删不掉的幽灵实体”，自动修复现有的 BUG 现场。