# AetherGate 粒子特效 2.0 优化计划书

## 1. 核心视觉目标

1. **极速纵向延伸 (Vertical Velocity):** 传送不再局限于玩家周围几格，而是要在视觉上连接“天空”与“地面”。出发时直冲云霄，到达时从天而降。
2. **有机螺旋 (Organic Helix):** 摒弃生硬的“双管”结构，打造“你追我赶”、动态交织、无明显边界的致密光柱。
3. **致密感与致盲 (Density & Blindness):** 增加粒子密度，强化 `ENCHANT`（附魔台粒子）的神秘感，并在传送瞬间用白色粒子填满玩家视野。
4. **完整的生命周期 (Lifecycle):** 增加到达时的“内爆收缩”和取消时的“消散解体”效果。

---

## 2. 参数调整建议 (Configuration)

在 `TeleportService.java` 或常量定义中进行以下微调：

* **HELIX_RADIUS:** 从 `1.0` 缩小至 `0.7` (更紧凑)。
* **VERTICAL_STEP:** 从 `0.2` 减小至 `0.1` (密度翻倍)。
* **SKY_HEIGHT:** 设定为 `40.0` (视觉上的高空，过高玩家看不见粒子)。

---

## 3. 具体实现方案 (Implementation Details)

### 任务一：重构螺旋算法 (The "Organic" Helix)

为了消除“边界感”并增加“纠缠感”，我们需要引入**相位偏移 (Phase Shift)** 和 **随机抖动 (Jitter)**。

**算法逻辑：**

* **主链 (End Rod):** 保持高亮核心，但半径略微波动。
* **副链 (Enchant):** 数量增加，分布在主链周围，且带有轻微的随机位置偏移，制造“星尘包裹”的感觉。

**代码参考 (优化后的 `spawnWarmupParticles`):**

```java
private void spawnWarmupParticles() {
    // 进度控制
    double progress = (double) tick / WARMUP_TICKS;
    // 高度控制：前90%时间缓慢上升至头顶，最后10%时间爆发性冲天
    double currentHeight = (progress < 0.9) ? (2.2 * (progress / 0.9)) : (2.2 + (SKY_HEIGHT * (progress - 0.9) * 10));

    Location center = lockPoint.clone();
    double timeOffset = tick * 0.35; // 加快旋转速度
    double baseRadius = 0.7; // 缩小半径

    // 更加致密的循环步长 (0.1)
    for (double h = 0.0; h <= currentHeight; h += 0.1) {
        // 基础角度
        double angle = h * 3.0 + timeOffset; // h*3.0 增加螺旋密度

        // --- 链条 1: END_ROD (白色核心) ---
        // 这里的半径加入微小的正弦波动，模拟呼吸感
        double r1 = baseRadius + Math.sin(h * 5 + tick * 0.1) * 0.1;
        spawnParticleAt(center, r1, h, angle, Particle.END_ROD, 0);

        // --- 链条 2 & 3: ENCHANT (神秘星尘) ---
        // 生成两个相对的 Enchant 链，且带有随机抖动
        for (int i = 0; i < 2; i++) {
            double offsetAngle = angle + Math.PI + (i * 0.5); // 错开角度
            // 随机扩散：让粒子不完全贴合线条，产生模糊边界
            double jitterX = (random.nextDouble() - 0.5) * 0.2;
            double jitterZ = (random.nextDouble() - 0.5) * 0.2;

            // 粒子数量加倍
            spawnParticleAt(center, baseRadius, h, offsetAngle, Particle.ENCHANT, 0);
            // 在稍微外围再生成一层稀疏的，增加体积感
            if (h % 0.3 < 0.1) {
                 spawnParticleAt(center, baseRadius * 1.5, h, offsetAngle, Particle.ENCHANT, 0);
            }
        }

        // --- 视野遮蔽 (Blindness Effect) ---
        // 当光柱上升到眼部位置 (1.6) 且接近传送完成时
        if (h > 1.5 && h < 1.8 && progress > 0.8) {
            // 在玩家头部位置生成致密云团
            center.getWorld().spawnParticle(Particle.END_ROD,
                center.getX(), center.getY() + 1.6, center.getZ(),
                5, 0.2, 0.2, 0.2, 0.05);
        }
    }
}

```

### 任务二：实现极速天降预览 (Rapid Skyfall Preview)

**逻辑：**

* 在 `PREVIEW_TICKS` 开始时，粒子不是从头顶慢慢长出来，而是从 `Y + 40` 的位置，以极快的速度“砸”向地面。
* 到达地面后，维持一个连接天空和地面的通道，直到传送发生。

**代码参考 (重写 `spawnArrivalPreview`):**

```java
private void spawnArrivalPreview() {
    int remaining = WARMUP_TICKS - tick;
    // 归一化进度 0.0 (开始) -> 1.0 (传送瞬间)
    double progress = 1.0 - ((double) remaining / PREVIEW_TICKS);

    // 极速下落逻辑：前 30% 的时间，光柱从天顶(40格) 冲到 地面(0格)
    // 后 70% 的时间，维持完整光柱
    double dropProgress = Math.min(1.0, progress * 3.0);

    double maxHeight = 40.0;
    double currentBottom = maxHeight * (1.0 - dropProgress); // 从 40 降到 0

    double timeOffset = tick * 0.35;

    // 只需要渲染视距内的高度 (例如只渲染底部 0 到 15格，或者全渲染看性能)
    // 既然要求"连接天空"，我们渲染 0 到 20格 加上 顶部的一些点缀
    for (double h = currentBottom; h <= 20.0; h += 0.2) {
        if (h < 0) continue; // 还没落地的部分不渲染

        double angle = h * 3.0 - timeOffset; // 反向旋转，区别于出发

        // 更加致密的 Enchant
        spawnParticleAt(arrival, 0.7, h, angle, Particle.ENCHANT, 0);
        spawnParticleAt(arrival, 0.7, h, angle + Math.PI, Particle.ENCHANT, 0);

        // 核心 End Rod 稍微稀疏一点，以免在那边太亮
        if (h % 0.4 < 0.1) {
            spawnParticleAt(arrival, 0.7, h, angle + Math.PI / 2, Particle.END_ROD, 0);
        }
    }
}

```

### 任务三：出发冲天与到达内爆 (Burst & Implosion)

**1. 出发：光柱直冲云霄 (Departure Zoom):**
在 `executeTeleport` 中调用。不再是生成静态柱子，而是生成带有极大向上速度的粒子。

```java
private void spawnDepartureBurst(World world) {
    // 产生大量向上冲的粒子
    for (int i = 0; i < 50; i++) {
        double angle = random.nextDouble() * Math.PI * 2;
        double r = 0.5 + random.nextDouble() * 0.5;
        double x = Math.cos(angle) * r;
        double z = Math.sin(angle) * r;

        // y velocity = 2.0 ~ 4.0 (极快向上)
        world.spawnParticle(Particle.END_ROD,
            originBlockLoc.getX() + 0.5 + x, originBlockLoc.getY(), originBlockLoc.getZ() + 0.5 + z,
            0, 0, 3.0 + random.nextDouble(), 0, 1);
    }
    // 伴随大量 Enchant 粒子残留
    world.spawnParticle(Particle.ENCHANT, originBlockLoc.clone().add(0.5, 1, 0.5), 100, 0.5, 5.0, 0.5, 0.1);
}

```

**2. 到达：能量内吸 (Arrival Implosion):**
当玩家落地时，粒子应该从周围向身体中心汇聚，然后消失。

```java
private void spawnArrivalBurst(World world) {
    Location center = arrival.clone().add(0, 1.0, 0); // 身体中心

    // 生成一个半径 3.0 的球体/圆环上的粒子
    for (int i = 0; i < 60; i++) {
        double angle = random.nextDouble() * Math.PI * 2;
        double r = 3.0; // 起始半径大一点
        double xOffset = Math.cos(angle) * r;
        double zOffset = Math.sin(angle) * r;
        double yOffset = (random.nextDouble() - 0.5) * 2.0;

        // 关键点：设置 count=0，后面的 xyz 变为 速度向量
        // 速度向量指向中心 (-x, -y, -z)
        world.spawnParticle(Particle.END_ROD,
            center.getX() + xOffset, center.getY() + yOffset, center.getZ() + zOffset,
            0, -xOffset * 0.15, -yOffset * 0.15, -zOffset * 0.15, 1);
            // 0.15 是吸入速度，越大越快
    }
    // 增加一圈 Enchant 粒子向内收缩
    world.spawnParticle(Particle.ENCHANT, center, 100, 2.0, 2.0, 2.0, 1.0); // 利用高 speed 让它乱飞或反向
}

```

### 任务四：取消时的消散 (Cancellation Dispersion)

**逻辑：**
当玩家取消时，已经生成的预览粒子（如果已经在播放）需要向外炸开并消失。
需要在 `TeleportTask` 中记录 `spawnArrivalPreview` 是否已经触发。

```java
private void spawnCancelParticles() {
    // 1. 本地玩家位置：向外缓慢飘散的烟雾
    lockPoint.getWorld().spawnParticle(Particle.CLOUD, lockPoint.clone().add(0,1,0), 30, 0.5, 1.0, 0.5, 0.05);

    // 2. 目标位置（如果已经开始预览）：粒子失去束缚，向四周散开
    if (tick >= WARMUP_TICKS - PREVIEW_TICKS && arrival.getWorld() != null) {
        // 在目标光柱的位置生成向外扩散的粒子
        for (double h = 0; h < 5.0; h+=0.5) {
             arrival.getWorld().spawnParticle(Particle.ENCHANT,
                 arrival.clone().add(0, h, 0),
                 10, 0.5, 0.0, 0.5, 0.2); // speed 0.2 让它稍微散开

             arrival.getWorld().spawnParticle(Particle.SMOKE,
                 arrival.clone().add(0, h, 0),
                 5, 0.2, 0.0, 0.2, 0.05);
        }
    }
}

```

---

## 4. 辅助函数更新 (Updated Helper)

为了支持部分粒子需要随机偏移（Jitter），建议更新辅助函数：

```java
private void spawnParticleAt(Location center, double radius, double y, double angle, Particle particle, double jitter) {
    if (center == null || center.getWorld() == null) return;

    double x = center.getX() + Math.cos(angle) * radius;
    double z = center.getZ() + Math.sin(angle) * radius;

    if (jitter > 0) {
        x += (random.nextDouble() - 0.5) * jitter;
        z += (random.nextDouble() - 0.5) * jitter;
    }

    // count=1, extra=0 保证粒子在原地生成，ENCHANT 粒子自带微弱的向上漂浮特性，非常适合
    center.getWorld().spawnParticle(particle, x, center.getY() + y, z, 1, 0, 0, 0, 0);
}

```

## 5. 总结

这份计划书的改动重点在于：

1. **加密度**：步长由 0.2 改为 0.1，增加粒子数量。
2. **加动态**：利用 `Math.sin` 给半径加呼吸效果，利用随机数给位置加抖动，消除死板的线条感。
3. **改时序**：到达预览改为“光速下坠”，传送瞬间改为“急速升空”。
4. **增交互**：传送结束时的内爆效果（反向速度）和传送中的致盲效果（眼部粒子）。