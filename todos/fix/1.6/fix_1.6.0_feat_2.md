[15:28:00 WARN]: [AetherGate] Task #24 for AetherGate v1.5.0 generated an exception
java.lang.IllegalArgumentException: x not finite
        at org.bukkit.util.NumberConversions.checkFinite(NumberConversions.java:118) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at org.bukkit.util.Vector.checkFinite(Vector.java:872) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at org.bukkit.craftbukkit.entity.CraftEntity.setVelocity(CraftEntity.java:201) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at AetherGate_Plugin_1.5.0_develop_d5f9e72.jar/cn.lingnc.aethergate.teleport.TeleportService$TeleportTask.knockbackNearby(TeleportService.java:562) ~[AetherGate_Plugin_1.5.0_develop_d5f9e72.jar:?]
        at AetherGate_Plugin_1.5.0_develop_d5f9e72.jar/cn.lingnc.aethergate.teleport.TeleportService$TeleportTask.executeTeleport(TeleportService.java:433) ~[AetherGate_Plugin_1.5.0_develop_d5f9e72.jar:?]
        at AetherGate_Plugin_1.5.0_develop_d5f9e72.jar/cn.lingnc.aethergate.teleport.TeleportService$TeleportTask.run(TeleportService.java:385) ~[AetherGate_Plugin_1.5.0_develop_d5f9e72.jar:?]
        at org.bukkit.craftbukkit.scheduler.CraftTask.run(CraftTask.java:78) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at org.bukkit.craftbukkit.scheduler.CraftScheduler.mainThreadHeartbeat(CraftScheduler.java:474) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.tickChildren(MinecraftServer.java:1662) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1530) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1252) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.lambda$spin$2(MinecraftServer.java:310) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at java.base/java.lang.Thread.run(Thread.java:1474) ~[?:?]
这是一个针对 AetherGate v1.6.0 (Develop) 版本的修复计划书。请将此文档移交给开发者进行修补。

---

# AetherGate v1.6.0-Fix2 修复与调整计划书

**版本目标**：修复 `IllegalArgumentException` 报错，回退并分离玩家与实体的视觉特效，优化实体落点安全判定，调整移动限制策略。

## 1. 严重 Bug 修复：非法向量异常 (Critical Fix)

### 问题描述

后台报错 `java.lang.IllegalArgumentException: x not finite`。
**原因**：在 `TeleportService.java` 的 `knockbackNearby` 方法中，当实体位置与爆炸中心点位置完全重合时（距离为0），`vector.normalize()` 会因除以零而抛出异常。

### 修复方案

**文件**：`TeleportService.java`
**位置**：`knockbackNearby` 方法
**逻辑**：在执行 `normalize()` 前检查向量长度。

```java
// 伪代码示例
Vector push = living.getLocation().toVector().subtract(center.toVector());
if (push.lengthSquared() < 0.0001) { // 检查是否重合
    push = new Vector(0, 0.5, 0);    // 重合时直接向上推
} else {
    push.normalize().multiply(1.5).setY(0.5); // 正常击退
}
living.setVelocity(push);

```

## 2. 视觉特效分离与回退 (Visual Separation)

### 问题描述

1. 玩家身上出现了本应属于随从生物的紫色粒子。
2. 玩家原本的“预热”效果（蓝色螺旋+光柱）和“预告”效果（目的地光柱）丢失或变样。
3. 随从实体在目的地没有“预告”效果。

### 修复方案

**文件**：`TeleportTask` (内部类)
**逻辑**：严格区分 **玩家 (Initiator)** 和 **随从目标 (Targets)** 的渲染逻辑。

#### A. 修改 `spawnWarmupParticles()`

* **玩家特效（回退旧版）**：
* 保留原本的 `base` (lockPoint) 周围的 `Particle.ENCHANT` (蓝色) 螺旋。
* 保留原本的 `Particle.END_ROD` 和 `Particle.DUST`。
* **关键**：确保这些特效只渲染一次（针对 `lockPoint`），不要对列表中的每个实体都渲染。


* **随从特效（新增紫色）**：
* 遍历 `targets` 列表。
* **过滤**：`if (entity.getUniqueId().equals(player.getUniqueId())) continue;` (跳过发起者玩家)。
* 渲染逻辑：使用 `Particle.WITCH` (紫色) 或 `PORTAL`。
* 高度适配：读取 `entity.getHeight()` 动态调整螺旋高度。



#### B. 修改 `spawnArrivalPreview()`

* **玩家预告**：
* 仅对 `arrival` (玩家落点) 生成原本的 `ENCHANT` (蓝色) 光柱。


* **随从预告**：
* 遍历 `targets` (跳过玩家)。
* 计算随从的落点 `Location dest = arrival.clone().add(arrivalOffsets.get(entity.getUniqueId()))`。
* 在 `dest` 生成 `Particle.WITCH` (紫色) 光柱。



## 3. 实体落点安全优化 (Safety Logic)

### 问题描述

随从生物（如马、猪）传送后可能会窒息（卡在方块里），原本的 `findArrivalSpot` 只针对玩家（2格高）判定，且未考虑偏移量带来的碰撞。

### 修复方案

**文件**：`TeleportService.java`
**逻辑**：增强落点检查，特别是针对带有偏移量的随从。

1. **修改 `teleportEntity()**`：
* 在执行传送前，再次检查最终坐标 `dest` 的安全性。
* `dest` 是 `arrival + offset`。如果 `offset` 导致实体进入墙壁，需要修正。
* **简单修正逻辑**：检测 `dest` 所在的方块是否是 `Solid`。如果是，尝试向 `arrival` 中心回缩，或者向上 `add(0, 1, 0)` 寻找空气。
* **防窒息**：确保 `dest` 及其上方（根据实体高度 `entity.getHeight()`）都是 `Passable` 的。如果不满足，强制将该实体的落点重置为 `arrival` (玩家的落点)，虽然会拥挤但比窒息好。



## 4. 机制调整 (Mechanics Adjustment)

### 问题描述

1. 用户希望彻底移除移动打断机制（因为已有取消按钮）。
2. 传送蓄力期间需要给予玩家生命恢复。
3. 但是保留传送时候不能自主移动。

### 修复方案

**文件**：`TeleportService.java`

#### A. 移除移动打断

* **修改 `handleMove(PlayerMoveEvent event)**`：
* 直接清空该方法的内容，或者直接 `return;`。
* (可选) 保留该方法但仅用于检测“跨世界”或“跨区块”导致的异常，不再检测距离。
* **建议**：代码中删除对距离的判断逻辑（虽然玩家被药水效果定身，但被推或者跳跃不再打断）。



#### B. 增加生命恢复

* **修改 `TeleportTask.applyLock()**`：
* 新增：`player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, WARMUP_TICKS + 20, 1));`
* 给予玩家生命恢复 II (Amplifier 1)，持续时间覆盖蓄力期。


* **修改 `PotionUtil.clearLockEffects()**`：
* 确保清理时移除 `REGENERATION` 效果（如果需要提前移除）。
