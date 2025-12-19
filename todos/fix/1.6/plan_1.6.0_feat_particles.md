这是一个为您准备的详细开发计划书，旨在交付给开发者以实现您预期的“双螺旋升降”传送特效。

此计划书基于您提供的 `plugin_context.md` 中的 `TeleportService.java` 代码结构进行设计。

---

# AetherGate 粒子特效重构计划书

## 1. 项目目标

将原有的光圈扩散式传送特效，重构为**动态双螺旋（Double Helix）**视觉效果。

**具体视觉阶段需求：**

1. **预热阶段 (Departure Warmup):** 粒子从脚底双螺旋向上升起，直到头顶。
2. **传送出发 (Departure execute):** 粒子流瞬间向天空冲出（双螺旋冲天），玩家消失。
3. **到达预览 (Arrival Preview):** 在玩家到达前 1.5秒 (30 ticks)，粒子从高空双螺旋向下延伸，接触地面。
4. **传送到达 (Arrival Execute):** 粒子流接触地面的瞬间玩家出现，粒子向内收缩（Implosion）并消失。
5. **取消传送 (Cancellation):** 粒子瞬间向外炸开/消散。

---

## 2. 技术参数调整

**文件:** `src/main/java/cn/lingnc/aethergate/teleport/TeleportService.java`

需修改 `TeleportService` 类中的静态常量以配合新的动画时序：

* **WARMUP_TICKS:** 保持 `60` (约3秒) 或根据需要调整。
* **PREVIEW_TICKS:** 修改为 `30` (约1.5秒)，以便有足够时间展示从天而降的效果。

```java
// 建议修改
private static final int PREVIEW_TICKS = 30; // 提前 1.5秒 开始播放到达动画

```

---

## 3. 核心算法逻辑 (Math Implementation)

开发者需要在 `TeleportTask` 内部或作为工具方法实现双螺旋算法。

### 3.1 螺旋方程

* : 半径 (0.8 - 1.2)
* : 随高度变化的旋转角 (实现螺旋)
* : 时间偏移量 (实现旋转动画)

### 3.2 粒子分配

* **Strand A (链1):** 使用 `Particle.END_ROD` (偏移 0度)
* **Strand B (链2):** 使用 `Particle.ENCHANT` (偏移 180度 /  弧度)

---

## 4. 开发任务清单 (Implementation Tasks)

### 任务一：重写 `spawnWarmupParticles` (出发预热)

**逻辑：**
不再生成圆环，而是根据当前的 `tick` 进度，计算粒子上升的高度。

* **高度 (y):** 从 0 增长到 2.0 (玩家高度)。
* **动画:** 随 `tick` 增加，整体螺旋进行旋转。

**代码参考 (伪代码/逻辑):**

```java
private void spawnWarmupParticles() {
    double progress = (double) tick / WARMUP_TICKS;
    double currentHeight = 2.2 * progress; // 逐渐上升到头顶稍高处
    double radius = 1.0;

    // 旋转速度
    double timeOffset = tick * 0.2;

    // 生成当前高度的一个点，或者为了连贯性生成一段
    for (double h = 0; h <= currentHeight; h += 0.2) {
        double angle = h * 2.0 + timeOffset; // h * 2.0 决定螺旋密度

        // 链 1: END_ROD
        spawnParticleAt(origin, radius, h, angle, Particle.END_ROD);

        // 链 2: ENCHANT (角度 + PI)
        spawnParticleAt(origin, radius, h, angle + Math.PI, Particle.ENCHANT);
    }
}

```

### 任务二：重写 `spawnArrivalPreview` (到达预览)

**逻辑：**
在 `tick >= (WARMUP_TICKS - PREVIEW_TICKS)` 时触发。

* **方向:** 从高空 (例如 y+5) 向下生长到 y=0。
* **同步:** 当 `tick == WARMUP_TICKS` 时，螺旋刚好接触地面。

**代码参考:**

```java
private void spawnArrivalPreview() {
    int remaining = WARMUP_TICKS - tick;
    // 进度：从 0 (开始预览) 到 1 (传送瞬间)
    double progress = 1.0 - ((double) remaining / PREVIEW_TICKS);

    double startHeight = 5.0; // 从5格高空降落
    double currentBottom = startHeight * (1.0 - progress); // 底部逐渐降低至0

    double timeOffset = tick * 0.2;

    // 只渲染“底部”到“顶部”之间的螺旋，营造降落感
    for (double h = currentBottom; h <= startHeight; h += 0.2) {
         double angle = h * 2.0 + timeOffset;
         spawnParticleAt(arrival, 1.0, h, angle, Particle.END_ROD);
         spawnParticleAt(arrival, 1.0, h, angle + Math.PI, Particle.ENCHANT);
    }
}

```

### 任务三：实现 `spawnDepartureBurst` (冲天效果)

**逻辑：**
在传送发生的瞬间 (`executeTeleport`) 调用。

* **动作:** 生成一个快速上升的完整螺旋，一直延伸到高空 (y=10+)。
* **效果:** 看起来像玩家化作光束飞走了。

**修改建议:**

```java
private void spawnDepartureBurst(World world) {
    // 瞬间生成高耸的螺旋
    for (double h = 0; h < 15.0; h += 0.5) {
        double angle = h * 1.5;
        // 稍微扩大一点半径表示能量爆发
        spawnParticleAt(originBlockLoc, 1.2, h, angle, Particle.END_ROD);
        spawnParticleAt(originBlockLoc, 1.2, h, angle + Math.PI, Particle.ENCHANT);
    }
    world.playSound(originBlockLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
}

```

### 任务四：实现 `spawnArrivalBurst` (收缩/着陆效果)

**逻辑：**
在玩家到达目标点后调用。

* **动作:** 不再是向外爆炸，而是向内收缩 (Implosion)。
* **实现:** 在半径 1.5 处生成粒子，设置 velocity 指向中心。

**修改建议:**

```java
private void spawnArrivalBurst(World world) {
    Location center = arrival.clone().add(0, 1, 0);
    // 向内收缩效果
    for (int i = 0; i < 20; i++) {
        double angle = (Math.PI * 2 * i) / 20;
        double x = Math.cos(angle) * 2.0;
        double z = Math.sin(angle) * 2.0;
        // 粒子产生在外部，速度矢量指向中心 (负数)
        world.spawnParticle(Particle.END_ROD, center.getX() + x, center.getY(), center.getZ(),
            0, -x * 0.2, 0, -z * 0.2, 0.5); // speed 控制收缩速度
    }
    world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
}

```

### 任务五：新增 `spawnCancelParticles` (取消耗散)

**逻辑：**
当 `cancelTeleport` 被调用时触发。

* **动作:** 简单的烟雾或云朵向外飘散，表示能量失控或消散。

**代码参考:**

```java
private void spawnCancelParticles() {
    World world = lockPoint.getWorld();
    if (world != null) {
        world.spawnParticle(Particle.CLOUD, lockPoint.clone().add(0, 1, 0), 20, 0.5, 1.0, 0.5, 0.1);
        world.playSound(lockPoint, Sound.BLOCK_CANDLE_EXTINGUISH, 1.0f, 1.0f);
    }
}

```

---

## 5. 辅助函数 (Helper Method)

建议在 `TeleportTask` 中添加此辅助函数以简化代码：

```java
private void spawnParticleAt(Location center, double radius, double y, double angle, Particle particle) {
    if (center == null || center.getWorld() == null) return;
    double x = center.getX() + Math.cos(angle) * radius;
    double z = center.getZ() + Math.sin(angle) * radius;
    // 0,0,0,0 表示这是一个固定位置的粒子，没有初速度
    center.getWorld().spawnParticle(particle, x, center.getY() + y, z, 1, 0, 0, 0, 0);
}

```

## 6. 实体处理 (Entities)

对于携带的生物/实体（`targets` 列表），保持原有的逻辑，但调用上述相同的粒子方法。

* 如果是 `Player`，主粒子是 `END_ROD` + `ENCHANT`。
* 如果是其他实体（如宠物），可以将 `ENCHANT` 替换为 `WITCH` 或 `CRIT` 以示区分，但保持“双螺旋”的形态结构一致。

---

## 7. 验收标准

1. **启动时：** 玩家看到脚下升起两条不同颜色的光带，螺旋交织上升。
2. **进行中：** 光带持续旋转并上升至头顶。
3. **到达前：** 目标地点提前看到天空降下螺旋光柱。
4. **传送瞬时：** * 起点：光柱猛烈冲向天空。
* 终点：光柱触地，瞬间收缩成一点，玩家出现。


5. **取消时：** 之前的螺旋化为烟雾消散。