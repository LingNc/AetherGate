AetherGate 修复说明书 (v1.14)
优先级: 中 (Logic Polish) 目标版本: Paper 1.21.4
当前是只是水平方向内，我低一点就用不了了。

1. 祭坛服务更新 (AltarService.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java

修改目标: 统一范围判定逻辑。无论是查找最近锚点 (findNearestActiveAnchor) 还是校验当前距离 (isWithinInteractionRange)，都应使用以核心为中心、半径为 X 的立方体区域（X 为配置项 interaction-radius）。

修改代码:

1.1 修改 findNearestActiveAnchor 方法
Java

    public Waypoint findNearestActiveAnchor(Location playerLoc) {
        World world = playerLoc.getWorld();
        if (world == null) return null;

        int radius = plugin.getPluginConfig().getInteractionRadius();
        String worldName = world.getName();
        double pX = playerLoc.getX();
        double pY = playerLoc.getY();
        double pZ = playerLoc.getZ();

        Waypoint bestMatch = null;
        double minDistSq = Double.MAX_VALUE;

        for (Waypoint wp : activeAltars.values()) {
            if (!wp.getWorldName().equals(worldName)) continue;
            if (wp.getCharges() == 0) continue; // Dormant 祭坛不作为自动吸附的起点

            // 祭坛核心坐标
            double coreX = wp.getX() + 0.5;
            double coreY = wp.getY(); // Block Y
            double coreZ = wp.getZ() + 0.5;

            // 范围检查: 全向立方体判定 (5x5x5 if radius=2)
            if (Math.abs(pX - coreX) > radius + 0.5) continue;
            if (Math.abs(pZ - coreZ) > radius + 0.5) continue;

            // 关键修改: 高度也使用 radius 判定 (上下各 radius 格)
            // 加上 0.5 是为了容错 (玩家头部/脚部位置)
            if (Math.abs(pY - coreY) > radius + 1.0) continue;

            double distSq = playerLoc.distanceSquared(new Location(world, coreX, coreY, coreZ));
            if (distSq < minDistSq) {
                minDistSq = distSq;
                bestMatch = wp;
            }
        }
        return bestMatch;
    }
1.2 修改 isWithinInteractionRange 方法
Java

    private boolean isWithinInteractionRange(Location origin, Block anchorBlock, int radius) {
        if (origin == null || anchorBlock == null) {
            return false;
        }
        World playerWorld = origin.getWorld();
        if (playerWorld == null || !playerWorld.equals(anchorBlock.getWorld())) {
            return false;
        }
        Location anchorLoc = anchorBlock.getLocation();

        // 水平距离判定
        int dx = Math.abs(origin.getBlockX() - anchorLoc.getBlockX());
        int dz = Math.abs(origin.getBlockZ() - anchorLoc.getBlockZ());
        if (dx > radius || dz > radius) {
            return false;
        }

        // 关键修改: 垂直距离判定
        // 使用绝对值判断上下范围，不再硬编码 +5
        // 核心 y=69, radius=2 => 有效范围 [67, 71]
        int dy = Math.abs(origin.getBlockY() - anchorLoc.getBlockY());
        return dy <= radius;
    }
2. 检查清单
[ ] AltarService.java: 确认 findNearestActiveAnchor 中的高度判定逻辑已改为 Math.abs(pY - coreY) <= radius + ...。

[ ] AltarService.java: 确认 isWithinInteractionRange 中的高度判定逻辑已改为 Math.abs(dy) <= radius。

完成此修改后，判定区域将严格遵循配置文件中的半径设置（默认为 2，即核心周围 5x5x5 空间）。玩家站在祭坛下方的底座（y-2）或跳到祭坛上方（y+2）均可正常交互。