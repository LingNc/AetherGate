### 计划概览

1. **重写粒子特效逻辑 (`TeleportService.java`)**：
* **出发（扫描上升 + 悬停）**：将 Warmup 阶段分为“扫描期”和“等待期”。粒子圈从脚底升到头顶后，停留在头顶旋转，直到传送发生。
* **到达（预加载 + 扫描下降）**：增加 `PREVIEW_TICKS`（如 40 tick，即 2秒）。粒子圈在目标地点从头顶高度生成，缓慢下降到脚底。粒子触底时，玩家传送发生。
* **中断（扩散消失）**：在 `abort` 逻辑中，如果到达地的预览特效已经开始，则在到达地播放一个向外扩散并消失的粒子圈。

### 第一步：修复粒子特效逻辑

修改文件：`src/main/java/cn/lingnc/aethergate/teleport/TeleportService.java`

我们需要调整时间常量，并完全重写 `TeleportTask` 中的粒子部分。

```java
public class TeleportService {

    // 调整时间配置
    private static final int WARMUP_TICKS = 100; // 增加出发时间，让扫描效果更明显 (约5秒)
    private static final int PREVIEW_TICKS = 40; // 到达预览时间 (2秒)
    private static final int RECOVERY_TICKS = 40;

    // ... 其他字段保持不变 ...

    // 在 TeleportTask 内部类中修改
    private class TeleportTask extends BukkitRunnable {
        // ... 字段保持不变 ...

        @Override
        public void run() {
            if (!player.isOnline()) {
                cleanup();
                cancel();
                return;
            }
            tick++;

            // 1. 出发特效 (全程播放)
            spawnDepartureParticles();

            // 2. 到达预览 (最后 PREVIEW_TICKS 开始)
            if (tick >= WARMUP_TICKS - PREVIEW_TICKS && !performedTeleport) {
                spawnArrivalPreview();
            }

            // 3. 执行传送
            if (tick == WARMUP_TICKS && !performedTeleport) {
                // ... 结构检查和资源消耗逻辑不变 ...
                executeTeleport();
            }
            // 4. 恢复期特效
            else if (tick > WARMUP_TICKS && performedTeleport) {
                spawnRecoveryParticles();
            }

            // 5. 结束
            if (performedTeleport && tick >= WARMUP_TICKS + RECOVERY_TICKS) {
                finish();
            }
        }

        // --- 修复点：出发特效 (从下向上扫描，然后悬停) ---
        private void spawnDepartureParticles() {
            if (lockPoint.getWorld() == null) return;

            // 扫描阶段占总 Warmup 的 70%
            double scanDuration = WARMUP_TICKS * 0.7;
            // 进度 0.0 -> 1.0 (如果超过1.0则保持1.0，即悬停)
            double progress = Math.min(1.0, tick / scanDuration);

            for (Entity entity : targets) {
                if (entity == null || !entity.isValid()) continue;

                double height = entity.getHeight();
                double width = entity.getWidth();
                double radius = Math.max(0.7, width * 1.2);

                // 计算当前的 Y 轴偏移量
                double currentY = height * progress;

                Location center = entity.getLocation();

                // 绘制光环
                int points = 12;
                double angleStep = (Math.PI * 2) / points;
                // 让光环旋转
                double rotationOffset = (tick * 0.2);

                for (int i = 0; i < points; i++) {
                    double angle = angleStep * i + rotationOffset;
                    double x = center.getX() + Math.cos(angle) * radius;
                    double z = center.getZ() + Math.sin(angle) * radius;
                    double y = center.getY() + currentY;

                    // 粒子颜色：如果在扫描中是紫色，悬停等待时变金色/白色
                    Particle particle = (progress < 1.0) ? Particle.WITCH : Particle.END_ROD;
                    entity.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
                }

                // 扫描时的上升流
                if (progress < 1.0) {
                     entity.getWorld().spawnParticle(Particle.PORTAL, center.getX(), center.getY() + currentY, center.getZ(), 5, 0.2, 0.1, 0.2, 0.1);
                }
            }
        }

        // --- 修复点：到达预览 (从上向下扫描，到底触发) ---
        private void spawnArrivalPreview() {
            World world = arrival.getWorld();
            if (world == null) return;

            // 剩余 tick
            int remaining = WARMUP_TICKS - tick;
            // 进度 0.0 (开始) -> 1.0 (结束/触底)
            double progress = 1.0 - ((double) remaining / PREVIEW_TICKS);

            for (Entity entity : targets) {
                if (entity == null || !entity.isValid()) continue;

                // 计算目标位置
                Location dest = arrival.clone();
                Vector offset = arrivalOffsets.get(entity.getUniqueId());
                if (offset != null) dest.add(offset);

                double height = entity.getHeight();
                // 从高空 (height + 0.5) 降落到 地面 (0)
                // Y = MaxHeight * (1 - progress)
                double currentY = (height + 0.5) * (1.0 - progress);

                // 绘制光环
                double radius = Math.max(0.7, entity.getWidth() * 1.2);
                int points = 12;
                double rotationOffset = -(tick * 0.2); // 反向旋转

                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2 * i / points) + rotationOffset;
                    double x = dest.getX() + Math.cos(angle) * radius;
                    double z = dest.getZ() + Math.sin(angle) * radius;
                    double y = dest.getY() + currentY;

                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, x, y, z, 1, 0, 0, 0, 0);
                }

                // 投影光柱 (可选，增强视觉定位)
                if (tick % 5 == 0) {
                    world.spawnParticle(Particle.DRAGON_BREATH, dest.getX(), dest.getY(), dest.getZ(), 1, 0.1, 0, 0.1, 0.02);
                }
            }
        }

        // --- 修复点：中断时的特效 (扩散消失) ---
        private void abort(String message) {
            if (message != null) player.sendMessage(message);

            // 如果已经开始了到达预览，需要在目标点播放“中断扩散”动画
            if (tick >= WARMUP_TICKS - PREVIEW_TICKS && !performedTeleport) {
                World destWorld = arrival.getWorld();
                if (destWorld != null) {
                    // 在目标点生成一个快速扩散并消失的圈
                    for (Entity entity : targets) {
                        if (entity == null) continue;
                        Location dest = arrival.clone();
                        Vector offset = arrivalOffsets.get(entity.getUniqueId());
                        if (offset != null) dest.add(offset);

                        // 播放一次性的扩散圈
                        new BukkitRunnable() {
                            double r = 0.5;
                            @Override
                            public void run() {
                                r += 0.5;
                                if (r > 3.0) { cancel(); return; }
                                for (int i = 0; i < 20; i++) {
                                    double angle = Math.PI * 2 * i / 20;
                                    double x = dest.getX() + Math.cos(angle) * r;
                                    double z = dest.getZ() + Math.sin(angle) * r;
                                    // 粒子使用烟雾，表示失败
                                    destWorld.spawnParticle(Particle.SMOKE, x, dest.getY() + 1, z, 1, 0, 0, 0, 0);
                                }
                            }
                        }.runTaskTimer(plugin, 0, 2);
                    }
                }
            }

            cleanup();
            activeTasks.remove(player.getUniqueId());
            cancel();
        }

        // ... 其他方法保持不变 ...
    }
}

```

### 总结
1. **粒子扫描**：
* 出发：`currentY = height * progress`，限制最大值为 height，实现了“扫描完悬停在头顶”的效果。
* 到达：`currentY = (height + 0.5) * (1.0 - progress)`，从上向下扫，到底部（`progress=1.0`）时正好对应 `tick = WARMUP_TICKS`，此时触发 `executeTeleport`，时机完美衔接。


3. **中断反馈**：在 `abort` 中增加了一个临时的 `BukkitRunnable` 来播放目标地点的烟雾扩散圈。