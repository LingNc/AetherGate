这是一个非常经典的问题。既然我们已经将实体设置为 `setPersistent(false)`（不保存到磁盘），那么**当区块卸载（Chunk Unload）时，这些实体就会直接消失**。

**问题成因：**

1.  **传送离开时：** 祭坛所在的区块被卸载 -\> 因为 `persistent=false`，实体被删除 -\> 粒子效果任务检测到实体消失，任务自动终止。
2.  **传送回来/服务器重启时：** 区块重新加载 -\> **但是没有代码去负责“重建”这些视觉实体** -\> 所以你只看到了方块，看不到悬浮核心和粒子。

**解决方案：**
我们需要实现\*\*“懒加载”机制\*\*。

1.  **启动时**：如果区块没加载，就不生成实体（节省资源，也防止出错）。
2.  **区块加载时**：监听 `ChunkLoadEvent`，一旦祭坛所在的区块被加载，立刻重生视觉实体和粒子效果。

请按顺序修改以下两个文件：

### 1\. 修改 `AltarService.java`

我们需要修改 `loadExistingAltars` 让它只处理已加载的区域，并添加一个新的 `onChunkLoad` 方法来处理动态加载。

在 `plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java` 中：

**第一步：修改 `loadExistingAltars` 方法**
找到该方法，将其替换为：

```java
    public void loadExistingAltars() {
        try {
            List<Waypoint> waypoints = storage.loadAllWaypoints();
            for (Waypoint waypoint : waypoints) {
                World world = Bukkit.getWorld(waypoint.getWorldName());
                if (world == null) {
                    continue;
                }
                Location blockLoc = new Location(world, waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ());
                String mapKey = key(world.getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
                activeAltars.put(mapKey, waypoint);

                // [修改]：只有当区块已经加载时，才生成视觉效果
                // 否则等待 ChunkLoadEvent 触发
                if (world.isChunkLoaded(blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4)) {
                    if (waypoint.isActivated()) {
                        boolean active = waypoint.isInfinite() || waypoint.getCharges() != 0;
                        updateVisualState(blockLoc, active);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load existing altars: " + e.getMessage());
        }
    }
```

**第二步：添加 `onChunkLoad` 方法**
在 `AltarService` 类中添加此方法（建议放在 `loadExistingAltars` 下面）：

```java
    // [新增] 用于响应区块加载事件，重建该区块内的祭坛视觉效果
    public void onChunkLoad(org.bukkit.Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        // 遍历所有活跃的祭坛，检查是否位于当前加载的区块内
        // 注意：这里简单的遍历效率尚可，因为祭坛数量通常不会极大。
        // 如果服务器有数千个祭坛，建议优化 activeAltars 的数据结构为 ChunkKey -> List<Waypoint>
        for (Waypoint waypoint : activeAltars.values()) {
            if (!waypoint.getWorldName().equals(worldName)) {
                continue;
            }
            if (!waypoint.isActivated()) {
                continue;
            }

            // 检查祭坛坐标是否在当前 Chunk 范围内
            int chunkX = waypoint.getBlockX() >> 4;
            int chunkZ = waypoint.getBlockZ() >> 4;

            if (chunkX == chunk.getX() && chunkZ == chunk.getZ()) {
                Location loc = waypoint.toLocation();
                if (loc != null) {
                    boolean active = waypoint.isInfinite() || waypoint.getCharges() != 0;
                    // 重建视觉实体和粒子任务
                    updateVisualState(loc, active);
                }
            }
        }
    }
```

-----

### 2\. 修改 `WorldAnchorListener.java`

我们需要监听 `ChunkLoadEvent` 并调用上面写好的方法。

在 `plugin/src/main/java/cn/lingnc/aethergate/listener/WorldAnchorListener.java` 中：

**第一步：导入 ChunkLoadEvent**
在文件头部添加导入：

```java
import org.bukkit.event.world.ChunkLoadEvent;
```

**第二步：添加事件监听方法**
在类中添加以下方法：

```java
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // 当区块加载时，通知 Service 检查是否有需要恢复的祭坛
        altarService.onChunkLoad(event.getChunk());
    }
```

-----

### 3\. 原理总结

做了这两个修改后，逻辑变成了这样：

1.  **服务器启动**：`loadExistingAltars` 运行。如果出生点区块是加载的，祭坛立刻显示。如果是远处的区块，只加载数据到内存 (`activeAltars`)，**不生成实体**。
2.  **玩家传送离开**：远处的区块卸载 -\> 实体因为 `setPersistent(false)` 自动消失 -\> 粒子任务因检测到实体消失而停止。这很干净，没有残留。
3.  **玩家传送回来**：
      * 触发 `ChunkLoadEvent`。
      * `WorldAnchorListener` 调用 `altarService.onChunkLoad`。
      * 插件发现这个区块里有个激活的祭坛。
      * 调用 `updateVisualState` -\> **生成新的实体** -\> **启动新的粒子任务**。
      * 玩家看到祭坛正常运作。

这样就完美解决了“重启消失”和“传送回来消失”的问题，同时保持了系统的整洁（没有幽灵实体）。