经过对你提供的代码（`plugin_context.md`）的详细审查，我找到了问题的核心原因。

### ❌ 为什么依然显示“爆炸了”？

根本原因在于你的 **`DeathMessageListener.java` 判断条件太严格了**。

你现在的代码逻辑是：

> “只有当玩家被标记了，**并且** 死因是 `BLOCK_EXPLOSION` (方块爆炸) / `FIRE` (火) 时，才修改消息。”

**但在实际游戏中发生了什么？**

1. 祭坛爆炸。
2. 玩家被巨大的冲击力**击飞**。
3. 玩家撞到了墙壁、天花板，或者重重地**摔在地上**。
4. 玩家死亡。
5. 此时，系统判定的死因是 **`FALL` (摔死)** 或 **`CONTACT` (撞死)**，而不是 `BLOCK_EXPLOSION`。
6. 你的代码检测到死因是 `FALL`，**不符合 `if` 条件**，于是跳过了修改，系统输出了原版的“xxx 落地过猛”或“xxx 爆炸了”（取决于谁造成了最后一下伤害）。

---

### ✅ 解决方案（直接发给开发者）

我们既然已经给玩家打上了 `sacrificial_victim`（献祭受害者）的标签，那么 **在这 3 秒内，无论他是怎么死的（被炸死、摔死、撞死、甚至被吓死），都应该算作“被献祭”**。

#### 1. 修改 `DeathMessageListener.java` (移除死因检查)

请让开发者将代码修改为如下（去掉了那层 `if (cause == ...)` 的判断）：

```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();

    // 只要有这个标签，无论死因是什么（摔死、炸死、撞死），统统算作献祭
    if (player.hasMetadata("sacrificial_victim")) {

        String deathMsg = plugin.getConfig().getString("death-messages.sacrifice", "%player% was sacrificed.");
        deathMsg = deathMsg.replace("%player%", player.getName());

        // 兼容旧版
        event.setDeathMessage(deathMsg);

        // 【建议】如果你是 Paper 1.20+，最好加上这一行以支持新的聊天组件系统
        // event.deathMessage(net.kyori.adventure.text.Component.text(deathMsg));

        // 清理标签
        player.removeMetadata("sacrificial_victim", plugin);
    }
}

```

#### 2. 修改 `AltarService.java` (修复伤害不稳定的问题)

你提到的“站在正上方不掉血”和“下蹲伤害低”，是因为爆炸点被**世界锚点方块**挡住了。必须在爆炸前把方块变成空气。

请让开发者修改 `triggerBackfire` 方法：

```java
// 在 AltarService.java 中

private void triggerBackfire(Location location) {
    // 1. 先给周围玩家打标签 (保持不变)
    double radius = 5.0;
    for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
        if (entity instanceof Player) {
            entity.setMetadata("sacrificial_victim", new FixedMetadataValue(plugin, true));
            // ... (定时清理任务保持不变)
        }
    }

    // 2. 【新增关键步骤】移除遮挡物！
    // 必须先把中心的世界锚点（以及下面的桶）变成空气，否则它们会阻挡爆炸射线
    location.getBlock().setType(Material.AIR);
    // 如果需要，把下面的桶也清了，防止产生掉落物或遮挡下方
    // location.getBlock().getRelative(BlockFace.DOWN).setType(Material.AIR);

    // 3. 然后再生成爆炸 (保持不变)
    // 4.0F 是 TNT 的威力
    location.getWorld().createExplosion(location, 4.0F, true);
}

```

### 总结

1. **监听器修改**：只要有标签就修改死亡信息，不要管 `DamageCause` 是什么。
2. **服务类修改**：爆炸前执行 `block.setType(Material.AIR)`，解决“站在上面炸不死”的物理遮挡 bug。

这样修改后，你就能 100% 看到“被献祭了”的提示，而且爆炸伤害会非常真实且致命。