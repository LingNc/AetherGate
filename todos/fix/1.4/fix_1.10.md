AetherGate 修复与优化说明书 (v1.10)
优先级: 中 (Visual & Logic) 目标版本: Paper 1.21.4

1. AltarService.java (核心视觉与逻辑)
此文件修改较多，建议按方法逐个更新。

1.1 修改 spawnOrReplaceCoreVisual (高度提升 & 尺寸放大)
目标:

将核心高度从 y+1.5 提升到 y+2.0。

将模型尺寸放大 (Scale 1.5倍)。

光源位置跟随调整。

Java

    private void spawnOrReplaceCoreVisual(Location blockLoc) {
        World world = blockLoc.getWorld();
        if (world == null) {
            return;
        }
        removeCoreVisual(blockLoc); // 确保移除旧的（包括休眠态的）

        // 修改 1: 高度从 1.5 提升到 2.0
        Location displayLoc = blockLoc.clone().add(0.5, 2.0, 0.5);

        ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(org.bukkit.Material.CONDUIT));
            d.setBillboard(Display.Billboard.CENTER);
            // 修改 2: 初始尺寸放大到 1.5 倍
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    d.getTransformation().getLeftRotation(),
                    new Vector3f(1.5f, 1.5f, 1.5f), // Scale 1.5
                    d.getTransformation().getRightRotation()));
        });

        String mapKey = key(world.getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        coreDisplays.put(mapKey, display.getUniqueId());

        placeCoreLight(mapKey, blockLoc);
        startCoreEffects(mapKey, display.getUniqueId(), displayLoc.clone());
    }
1.2 修改 placeCoreLight (光源对齐)
目标: 核心抬高后，光源方块也需要相应调整位置，确保发光点与核心重合。

Java

    private void placeCoreLight(String mapKey, Location blockLoc) {
        // 修改: 光源位置从 y+1 调整到 y+2 (与核心实体位置 y+2.0 对应)
        Block lightBlock = blockLoc.clone().add(0, 2, 0).getBlock();

        if (lightBlock.getType() != Material.AIR && lightBlock.getType() != Material.LIGHT) {
            return;
        }
        lightBlock.setType(Material.LIGHT, false);
        if (lightBlock.getBlockData() instanceof Light lightData) {
            lightData.setLevel(15);
            lightBlock.setBlockData(lightData, false);
        }
        coreLights.put(mapKey, lightBlock.getLocation());
    }
1.3 修改 animateDisplay (动画适配尺寸)
目标: 确保浮动动画的缩放基础值与初始生成的 1.5倍 保持一致。

Java

    private void animateDisplay(ItemDisplay display, double phase) {
        display.setRotation(display.getYaw() + 2.5f, display.getPitch());
        Transformation transform = display.getTransformation();

        // 修改: 基础 Scale 改为 1.5，浮动范围 1.5 ~ 1.6
        float scale = (float) (1.5 + 0.1 * Math.sin(phase));

        display.setInterpolationDuration(5);
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(transform.getTranslation(),
                transform.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                transform.getRightRotation()));
    }
1.4 修改 markDormant (枯竭保留实体)
目标: 次数耗尽时，不移除核心实体，只停止特效和移除光源。
我们可以获取实体并将其设置为"死寂"状态（停止特效，移除光源变小、停止旋转）。
Java

    private void markDormant(Waypoint waypoint) {
        Location loc = waypoint.toLocation();
        if (loc == null) {
            return;
        }
        Waypoint updated = new Waypoint(waypoint.getId(), waypoint.getName(), waypoint.getWorldName(),
                waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.getOwner(), 0);
        try {
            storage.saveOrUpdateWaypoint(updated);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to mark waypoint dormant: " + e.getMessage());
        }

        String mapKey = key(waypoint.getWorldName(), waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ());

        // 修改逻辑:
        // 1. 移除 Active 列表 (停止传送功能)
        activeAltars.remove(mapKey);

        // 2. 停止粒子特效
        cancelCoreEffects(mapKey);

        // 3. 移除光源 (变暗)
        removeCoreLight(mapKey);

        // 4. 【关键】不再调用 removeCoreVisual(loc)，保留实体
        // 但我们需要把 mapKey 从 coreDisplays 中移除吗？
        // 不，如果不移除，下次服务器重启 reload 时可能会重复生成。
        // 为了安全起见，我们还是保留它在 coreDisplays 里，
        // 或者我们可以获取实体并将其设置为"死寂"状态（比如变小、停止旋转）。

        UUID displayId = coreDisplays.get(mapKey);
        if (displayId != null) {
            World world = loc.getWorld();
            if (world != null) {
                Entity entity = world.getEntity(displayId);
                if (entity instanceof ItemDisplay display) {
                    // 设置为暗淡/静止状态
                    display.setInterpolationDuration(20);
                    display.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        display.getTransformation().getLeftRotation(),
                        new Vector3f(1.0f, 1.0f, 1.0f), // 缩小一点表示失去能量
                        display.getTransformation().getRightRotation()
                    ));
                    // 还可以改成灰色玻璃等，这里暂时只缩小并停止动画
                }
            }
            // 注意：不要从 coreDisplays 移除，以便后续 destroy 时能找到它
        }

        breakCornerLights(loc);
        playCollapseEffects(loc);
    }
注意: 相应的，在 removeCoreVisual 方法中，不需要改动。当祭坛彻底被破坏(handleAnchorBreak)或炸膛(backfire)时，会调用 removeCoreVisual，那时才真正删除实体。

1.5 修改 backfire (增强爆炸范围)
目标: 炸膛特效范围扩大至 8-10 格。

Java

    private void backfire(World world, Location loc, Player trigger, AltarValidationResult debugInfo) {
        world.createExplosion(loc, 6.0f, false, false);
        Location center = loc.clone().add(0.5, 0.5, 0.5);

        // 修改: 范围参数 (offsetX, offsetY, offsetZ) 扩大到 8.0
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 8, 4.0, 4.0, 4.0, 0.0);
        world.spawnParticle(Particle.EXPLOSION, center, 300, 8.0, 4.0, 8.0, 0.1);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 200, 6.0, 3.0, 6.0, 0.05);
        world.spawnParticle(Particle.LAVA, center, 150, 4.0, 2.0, 4.0, 0.05);

        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);

        // ... (后续逻辑保持不变)
    }
2. TeleportService.java (传送抵达特效)
2.1 修改 executeTeleport (增加无伤爆炸)
目标: 在玩家抵达时播放视觉爆炸。

Java

        private void executeTeleport() {
            // ... (前置代码不变)

            internalTeleporting = true;
            player.teleport(arrival);
            internalTeleporting = false;

            World arrivalWorld = arrival.getWorld();
            if (arrivalWorld != null) {
                arrivalWorld.strikeLightningEffect(arrival);
                spawnArrivalBurst(arrivalWorld);

                // 新增: 视觉爆炸冲击波
                spawnArrivalShockwave(arrivalWorld, arrival);

                knockbackNearby(arrivalWorld);
                scheduleThunder(arrivalWorld, arrival.clone());
            }

            // ... (后续代码不变)
        }
2.2 新增 spawnArrivalShockwave 方法
添加此新方法到 TeleportTask 类中。

Java

        private void spawnArrivalShockwave(World world, Location center) {
            // 纯视觉爆炸，无伤害
            // 核心爆炸云
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);

            // 向外扩散的冲击波环 (半径 3 格)
            world.spawnParticle(Particle.EXPLOSION, center, 40, 2.0, 0.5, 2.0, 0.05);
            world.spawnParticle(Particle.FLASH, center, 1);

            // 音效
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        }
3. 检查清单
[ ] AltarService.java: 确认 spawnOrReplaceCoreVisual 高度为 y+2.0，Scale 为 1.5。

[ ] AltarService.java: 确认 placeCoreLight 位置为 y+2。

[ ] AltarService.java: 确认 markDormant 移除了 removeCoreVisual 调用，改为停止特效和光照。

[ ] AltarService.java: 确认 backfire 粒子范围已扩大到 8.0+。

[ ] TeleportService.java: 确认增加了 spawnArrivalShockwave 视觉爆炸逻辑。

完成修改后，祭坛的视觉表现将更宏大，且符合“能量耗尽但核心物质残留”的设定。