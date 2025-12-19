# AetherGate 传送保护机制修复计划书

## 1. 问题描述

当前 `TeleportService.java` 中的到达逻辑存在友军误伤问题。

* **现象：** 当玩家及其随行实体（坐骑、宠物）到达目标点时，触发的 `spawnArrivalShockwave` 和 `knockbackNearby` 效果会作用于所有“附近实体”。
* **原因：** `knockbackNearby` 方法目前仅硬编码排除了主玩家 (`player`)，但并未排除一同传送过来的 `targets` 列表（如马、猪、其他乘客）。且由于检测是在传送发生后立即执行的，这些实体此时正好处于检测范围内。
* **目标：** 建立“白名单豁免机制”，确保传送组内的所有成员在落地瞬间免疫自身的冲击波。

---

## 2. 修改方案概述

我们需要在 `TeleportTask` 类中修改两个部分：

1. **修改 `knockbackNearby` 方法签名**：使其接受一个 `Set<UUID>` 类型的豁免名单。
2. **更新 `executeTeleport` 逻辑**：在触发冲击波前，将所有参与传送的实体 ID 加入豁免名单。

---

## 3. 代码实施细节

**文件:** `src/main/java/cn/lingnc/aethergate/teleport/TeleportService.java`

### 步骤一：更新 `knockbackNearby` 方法

我们需要将原有的简单排除逻辑替换为基于 UUID 集合的过滤逻辑。

**旧代码：**

```java
private void knockbackNearby(World world) {
    Collection<Entity> nearby = world.getNearbyEntities(arrival, 5.0, 3.0, 5.0);
    for (Entity entity : nearby) {
        if (entity.equals(player)) { // 只保护了玩家自己
            continue;
        }
        // ...

```

**新代码 (建议替换)：**

```java
/**
 * 击退周围实体，但豁免白名单内的实体
 * @param world 所在世界
 * @param safeEntities 豁免名单（传送组全员）
 */
private void knockbackNearby(World world, Set<UUID> safeEntities) {
    // 范围保持不变
    Collection<Entity> nearby = world.getNearbyEntities(arrival, 5.0, 3.0, 5.0);

    for (Entity entity : nearby) {
        // 1. 核心修复：检查 UUID 是否在豁免名单中
        if (safeEntities.contains(entity.getUniqueId())) {
            continue;
        }

        // 2. 额外保护：已驯服的宠物如果主人在豁免名单中，也尝试保护（可选，视需求而定）
        if (entity instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() != null) {
            if (safeEntities.contains(tameable.getOwner().getUniqueId())) {
                continue;
            }
        }

        if (!(entity instanceof LivingEntity living)) {
            continue;
        }

        // 3. 施加击退和伤害 (逻辑保持不变)
        Vector push = entity.getLocation().toVector().subtract(arrival.toVector());
        if (push.lengthSquared() < 0.0001) {
            push = new Vector(0, 0.5, 0);
        } else {
            push.normalize().multiply(0.8).setY(0.35);
        }

        // 伤害来源归属给玩家，但不会伤及豁免目标
        living.damage(2.0, player);
        living.setVelocity(push);
    }
}

```

### 步骤二：在 `executeTeleport` 中构建豁免名单

在执行传送逻辑的主方法中，收集所有“自己人”的 ID。

**修改位置：** `TeleportTask` 类内部的 `executeTeleport` 方法。

**修改后代码：**

```java
private void executeTeleport() {
    performedTeleport = true;
    captureRideRelations();
    dismountAll(); // 先下马

    // ... (出发地的特效代码保持不变) ...
    World world = originBlockLoc.getWorld();
    if (world != null) {
        world.strikeLightningEffect(originBlockLoc);
        spawnDepartureBurst(world);
        scheduleThunder(world, originBlockLoc.clone().add(0.5, 0.0, 0.5));
    }

    // --- 执行传送 ---
    teleportTargets();

    // --- 到达后处理 ---
    World arrivalWorld = arrival.getWorld();
    if (arrivalWorld != null) {
        // 1. 构建豁免名单 (Safe List)
        Set<UUID> safeIds = new HashSet<>();
        safeIds.add(player.getUniqueId()); // 添加玩家
        for (Entity target : targets) {
            if (target != null) {
                safeIds.add(target.getUniqueId()); // 添加所有坐骑、乘客、宠物
            }
        }

        // 2. 播放视觉特效 (不受影响)
        arrivalWorld.strikeLightningEffect(arrival);
        spawnArrivalBurst(arrivalWorld);
        spawnArrivalShockwave(arrivalWorld, arrival);

        // 3. 执行物理击退 (传入豁免名单)
        knockbackNearby(arrivalWorld, safeIds);

        scheduleThunder(arrivalWorld, arrival.clone());
    }

    plugin.getAchievementService().handleTeleportComplete(player);
    player.playSound(arrival, Sound.ENTITY_ENDERMAN_STARE, 0.8f, 1.4f);
    player.sendMessage("§b空间折跃完成，正在重构形体……");
}

```

---

## 4. 补充优化建议 (无敌帧)

为了双重保险，防止网络延迟导致客户端未及时同步位置判定，建议在 `TeleportTask` 的 `teleportEntity` 方法中，给予非玩家实体短暂的无敌时间。

**修改 `teleportEntity` 方法:**

```java
private void teleportEntity(Entity entity) {
    if (entity == null || !entity.isValid()) {
        return;
    }
    // ... (位置计算逻辑保持不变) ...

    // 执行传送
    if (entity instanceof Player p) {
        internalTeleporting = true;
        p.teleport(dest);
        internalTeleporting = false;
        // 玩家已有 applyLock 的无敌效果，无需额外处理
    } else {
        entity.teleport(dest);
        // [新增] 给随行实体 2秒 (40 ticks) 的无敌时间，防止落地意外伤害或卡墙伤害
        if (entity instanceof LivingEntity living) {
            living.setNoDamageTicks(40);
        }
    }
}

```

## 5. 验收标准

1. **玩家测试：** 玩家骑马传送。
* **预期：** 到达瞬间，周围产生爆炸特效和击退波。
* **结果：** 马匹没有受到伤害（变红），没有被弹飞；玩家也没有受到伤害。


2. **旁观测试：** 在目标点放置一只僵尸或另外一名玩家。
* **预期：** 当传送者落地时，该僵尸/旁观玩家应被击退并受到伤害。


3. **宠物测试：** 携带多只驯服的狼传送。
* **结果：** 所有狼落地后聚集在玩家身旁，未受到冲击波影响。