package cn.lingnc.aethergate.teleport;

import cn.lingnc.aethergate.AetherGatePlugin;
import cn.lingnc.aethergate.altar.AltarService;
import cn.lingnc.aethergate.altar.AltarStructureChecker;
import cn.lingnc.aethergate.altar.AltarValidationResult;
import cn.lingnc.aethergate.model.Waypoint;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the multi-phase teleport ritual, including locking players, particle playback,
 * and orchestrating pearl/charge consumption at the correct timing milestone.
 */
public class TeleportService {

    private static final int WARMUP_TICKS = 60; // ~3 seconds
    private static final int RECOVERY_TICKS = 40; // ~2 seconds cool down window
    private static final int PREVIEW_TICKS = 10; // ticks before teleport to show arrival beam

    private final AetherGatePlugin plugin;
    private final AltarService altarService;
    private final PearlCostManager costManager;
    private final Map<UUID, TeleportTask> activeTasks = new HashMap<>();
    private final Set<UUID> globalLockedEntities = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

    public TeleportService(AetherGatePlugin plugin, AltarService altarService) {
        this.plugin = plugin;
        this.altarService = altarService;
        this.costManager = new PearlCostManager();
    }

    public boolean beginTeleport(Player player, Block originBlock, Waypoint destinationRequest) {
        if (player == null || originBlock == null || destinationRequest == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (activeTasks.containsKey(uuid)) {
            player.sendMessage("§c你已经处于传送过程当中。");
            return false;
        }
        if (originBlock.getType() != Material.LODESTONE || !altarService.isAnchorBlock(originBlock)) {
            player.sendMessage("§c附近没有可用的世界锚点。");
            return false;
        }
        if (!altarService.isWithinInteractionRange(player.getLocation(), originBlock)) {
            player.sendMessage("§c请在祭坛附近启动传送。");
            return false;
        }
        if (!ensureStructureIntegrity(originBlock, player, true)) {
            return false;
        }
        Waypoint originWaypoint = altarService.getActiveWaypoint(originBlock.getLocation());
        if (originWaypoint == null) {
            player.sendMessage("§c这个世界锚点尚未激活或已枯竭。");
            return false;
        }
        if (!originWaypoint.isInfinite() && originWaypoint.getCharges() <= 0) {
            player.sendMessage("§c祭坛处于休眠状态，请先重新充能。");
            return false;
        }
        Entity rootEntity = findRootVehicle(player);
        UUID rootId = rootEntity.getUniqueId();
        if (globalLockedEntities.contains(rootId)) {
            player.sendMessage("§c该实体正在被另一个传送占用。");
            return false;
        }
        Location destinationBlockLoc = getBlockLocation(destinationRequest);
        if (destinationBlockLoc == null) {
            player.sendMessage("§c目标锚点不存在于世界中。");
            return false;
        }
        Waypoint liveDestination = altarService.getActiveWaypoint(destinationBlockLoc);
        if (liveDestination == null) {
            player.sendMessage("§c目标锚点尚未激活或已枯竭。");
            return false;
        }
        if (sameBlock(originWaypoint, liveDestination)) {
            player.sendMessage("§c无法传送到同一个世界锚点。");
            return false;
        }
        Location arrivalSpot = findArrivalSpot(liveDestination);
        if (arrivalSpot == null) {
            player.sendMessage("§c目标祭坛附近没有安全的落点。");
            return false;
        }
        TeleportTask task = new TeleportTask(player, originBlock.getLocation(), originWaypoint,
                liveDestination, arrivalSpot, rootId);
        activeTasks.put(uuid, task);
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    public void cancelTeleport(Player player, String reason) {
        if (player == null) {
            return;
        }
        TeleportTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.abort(reason);
        }
    }

    public boolean isPlayerLocked(UUID uuid) {
        return activeTasks.containsKey(uuid);
    }

    public Location getLockLocation(UUID uuid) {
        TeleportTask task = activeTasks.get(uuid);
        return task != null ? task.getLockPoint() : null;
    }

    public boolean isInternalTeleport(UUID uuid) {
        TeleportTask task = activeTasks.get(uuid);
        return task != null && task.isInternalTeleport();
    }

    public void handlePlayerQuit(UUID uuid) {
        TeleportTask task = activeTasks.remove(uuid);
        if (task != null) {
            task.abort(null);
        }
    }

    private Location getBlockLocation(Waypoint waypoint) {
        World world = Bukkit.getWorld(waypoint.getWorldName());
        if (world == null) {
            return null;
        }
        return new Location(world, waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ());
    }

    private boolean sameBlock(Waypoint a, Waypoint b) {
        return a.getWorldName().equalsIgnoreCase(b.getWorldName())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private Entity findRootVehicle(Entity entity) {
        Entity current = entity;
        while (current.getVehicle() != null) {
            current = current.getVehicle();
        }
        return current;
    }

    private Location findArrivalSpot(Waypoint destination) {
        World world = Bukkit.getWorld(destination.getWorldName());
        if (world == null) {
            return null;
        }
        int anchorX = destination.getBlockX();
        int anchorY = destination.getBlockY();
        int anchorZ = destination.getBlockZ();
        int radius = Math.max(1, plugin.getPluginConfig().getArrivalRadius());
        List<Location> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int yOffset = -3; yOffset <= 1; yOffset++) {
                    int testY = anchorY + yOffset;
                    Location solidLoc = new Location(world, anchorX + dx, testY, anchorZ + dz);
                    Location feet = solidLoc.clone().add(0, 1, 0);
                    Location head = solidLoc.clone().add(0, 2, 0);
                    if (!solidLoc.getBlock().getType().isSolid()) {
                        continue;
                    }
                    if (!feet.getBlock().isPassable() || !head.getBlock().isPassable()) {
                        continue;
                    }
                    candidates.add(new Location(world,
                            solidLoc.getBlockX() + 0.5,
                            solidLoc.getBlockY() + 1,
                            solidLoc.getBlockZ() + 0.5));
                    break;
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private class TeleportTask extends BukkitRunnable {

        private final Player player;
        private final Location originBlockLoc;
        private final Waypoint destination;
        private final Location arrival;
        private final Location lockPoint;
        private final boolean prevInvulnerable;
        private final float prevWalkSpeed;
        private final float prevFlySpeed;
        private final UUID lockedRoot;
        private boolean performedTeleport = false;
        private boolean internalTeleporting = false;
        private int tick = 0;

        TeleportTask(Player player, Location originBlockLoc, Waypoint origin,
                     Waypoint destination, Location arrival, UUID lockedRoot) {
            this.player = player;
            this.originBlockLoc = originBlockLoc.clone();
            this.destination = destination;
            this.arrival = arrival.clone();
            this.lockPoint = player.getLocation().clone();
            this.prevInvulnerable = player.isInvulnerable();
            this.prevWalkSpeed = player.getWalkSpeed();
            this.prevFlySpeed = player.getFlySpeed();
            this.lockedRoot = lockedRoot;
            globalLockedEntities.add(lockedRoot);
            applyLock();
            player.sendMessage("§f>> §b以太能量锁定，保持冷静……");
            player.playSound(lockPoint, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.8f, 1.1f);
        }

        @Override
        public void run() {
            if (!player.isOnline()) {
                cleanup();
                cancel();
                return;
            }
            tick++;
            spawnWarmupParticles();
            if (tick >= WARMUP_TICKS - PREVIEW_TICKS && !performedTeleport) {
                spawnArrivalPreview();
            }
            if (tick == WARMUP_TICKS && !performedTeleport) {
                if (!ensureStructureIntegrity(originBlockLoc.getBlock(), player, true)) {
                    abort(null);
                    return;
                }
                if (!consumeResources()) {
                    abort("§c传送失败：能量不足或结构不完整。");
                    return;
                }
                executeTeleport();
            } else if (tick > WARMUP_TICKS && performedTeleport) {
                spawnRecoveryParticles();
            }
            if (performedTeleport && tick >= WARMUP_TICKS + RECOVERY_TICKS) {
                finish();
            }
        }

        @Override
        public synchronized void cancel() throws IllegalStateException {
            super.cancel();
        }

        private void applyLock() {
            player.setInvulnerable(true);
            player.addPotionEffect(PotionUtil.noJumpEffect());
            player.addPotionEffect(PotionUtil.blindnessEffect());
            player.setWalkSpeed(0f);
            player.setFlySpeed(0f);
            player.setSprinting(false);
        }

        private boolean consumeResources() {
            Block anchorBlock = originBlockLoc.getBlock();
            if (anchorBlock.getType() != Material.LODESTONE || !altarService.isAnchorBlock(anchorBlock)) {
                return false;
            }
            if (!costManager.consumePearls(originBlockLoc, player, 1)) {
                return false;
            }
            return altarService.consumeCharge(originBlockLoc);
        }

        private void executeTeleport() {
            performedTeleport = true;
            World world = originBlockLoc.getWorld();
            if (world != null) {
                world.strikeLightningEffect(originBlockLoc);
                spawnDepartureBurst(world);
                scheduleThunder(world, originBlockLoc.clone().add(0.5, 0.0, 0.5));
            }
            internalTeleporting = true;
            player.teleport(arrival);
            internalTeleporting = false;
            World arrivalWorld = arrival.getWorld();
            if (arrivalWorld != null) {
                arrivalWorld.strikeLightningEffect(arrival);
                spawnArrivalBurst(arrivalWorld);
                spawnArrivalShockwave(arrivalWorld, arrival);
                knockbackNearby(arrivalWorld);
                scheduleThunder(arrivalWorld, arrival.clone());
            }
            plugin.getAchievementService().handleTeleportComplete(player);
            player.playSound(arrival, Sound.ENTITY_ENDERMAN_STARE, 0.8f, 1.4f);
            player.sendMessage("§b空间折跃完成，正在重构形体……");
        }

        private void spawnWarmupParticles() {
            Location base = lockPoint.clone();
            if (base.getWorld() == null) {
                return;
            }
            double phase = tick / 10.0;
            double radius = Math.min(2.5, 0.5 + tick * 0.03);
            for (int i = 0; i < 12; i++) {
                double angle = phase + (i / 12.0) * Math.PI * 2;
                double x = base.getX() + Math.cos(angle) * radius;
                double y = base.getY() + 0.2 + (tick * 0.02) + (i * 0.05);
                double z = base.getZ() + Math.sin(angle) * radius;
                base.getWorld().spawnParticle(Particle.ENCHANT, x, y, z, 1, 0, 0, 0, 0);
            }
            base.getWorld().spawnParticle(Particle.END_ROD, base.clone().add(0, 1.2, 0), 6, 0.4, 0.4, 0.4, 0.0);
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(240, 240, 255), 1.2f);
            base.getWorld().spawnParticle(Particle.DUST, base, 8, radius / 4.0, 0.1, radius / 4.0, 0, dust);
        }

        private void spawnArrivalPreview() {
            World arrivalWorld = arrival.getWorld();
            if (arrivalWorld == null) {
                return;
            }
            double height = 4.0;
            for (double y = 0; y <= height; y += 0.5) {
                arrivalWorld.spawnParticle(Particle.ENCHANT, arrival.getX(), arrival.getY() + y, arrival.getZ(),
                        4, 0.3, 0.0, 0.3, 0.0);
            }
        }

        private void spawnRecoveryParticles() {
            World arrivalWorld = arrival.getWorld();
            if (arrivalWorld == null) {
                return;
            }
            double progress = (tick - WARMUP_TICKS) / (double) RECOVERY_TICKS;
            double radius = 2.5 * (1.0 - progress);
            for (int i = 0; i < 10; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double x = arrival.getX() + Math.cos(angle) * radius;
                double y = arrival.getY() + 0.2 + random.nextDouble();
                double z = arrival.getZ() + Math.sin(angle) * radius;
                arrivalWorld.spawnParticle(Particle.ENCHANT, x, y, z, 1, 0, 0, 0, 0);
            }
            arrivalWorld.spawnParticle(Particle.END_ROD, arrival, 4, 0.4, 0.6, 0.4, 0.0);
        }

        private void spawnDepartureBurst(World world) {
            for (int i = 0; i < 3; i++) {
                double radius = 0.6 + i * 0.5;
                for (int j = 0; j < 40; j++) {
                    double angle = (j / 40.0) * Math.PI * 2;
                    double x = originBlockLoc.getX() + 0.5 + Math.cos(angle) * radius;
                    double z = originBlockLoc.getZ() + 0.5 + Math.sin(angle) * radius;
                    double y = originBlockLoc.getY() + 0.5 + i * 0.3;
                    world.spawnParticle(Particle.ENCHANT, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }

        private void spawnArrivalBurst(World world) {
            for (int ring = 0; ring < 3; ring++) {
                double radius = 0.5 + ring * 0.7;
                for (int i = 0; i < 60; i++) {
                    double angle = (i / 60.0) * Math.PI * 2;
                    double x = arrival.getX() + Math.cos(angle) * radius;
                    double z = arrival.getZ() + Math.sin(angle) * radius;
                    double y = arrival.getY() + 0.2 + ring * 0.2;
                    world.spawnParticle(Particle.ENCHANT, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }

        private void spawnArrivalShockwave(World world, Location center) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
            world.spawnParticle(Particle.EXPLOSION, center, 40, 2.0, 0.5, 2.0, 0.05);
            world.spawnParticle(Particle.FLASH, center, 1);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        }

        private void knockbackNearby(World world) {
            Collection<Entity> nearby = world.getNearbyEntities(arrival, 5.0, 3.0, 5.0);
            for (Entity entity : nearby) {
                if (entity.equals(player)) {
                    continue;
                }
                if (entity instanceof Tameable tameable && tameable.isTamed()) {
                    continue;
                }
                if (!(entity instanceof LivingEntity living)) {
                    continue;
                }
                Vector push = entity.getLocation().toVector().subtract(arrival.toVector()).normalize().multiply(0.8);
                push.setY(0.35);
                living.damage(2.0, player);
                living.setVelocity(push);
            }
        }

        private void finish() {
            player.sendMessage("§a传送完成，祝你旅途顺利。");
            cleanup();
            activeTasks.remove(player.getUniqueId());
            cancel();
        }

        private void abort(String message) {
            if (message != null) {
                player.sendMessage(message);
            }
            cleanup();
            activeTasks.remove(player.getUniqueId());
            cancel();
        }

        private void cleanup() {
            if (!prevInvulnerable) {
                player.setInvulnerable(false);
            }
            player.setWalkSpeed(prevWalkSpeed);
            player.setFlySpeed(prevFlySpeed);
            PotionUtil.clearLockEffects(player);
            if (lockedRoot != null) {
                globalLockedEntities.remove(lockedRoot);
            }
        }

        Location getLockPoint() {
            return lockPoint.clone();
        }

        boolean isInternalTeleport() {
            return internalTeleporting;
        }
    }

    private static final class PotionUtil {
        private PotionUtil() {}

        static org.bukkit.potion.PotionEffect noJumpEffect() {
            return new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, 200, 250, true, false, false);
        }

        static org.bukkit.potion.PotionEffect blindnessEffect() {
            return new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 200, 0, true, false, false);
        }

        static void clearLockEffects(Player player) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        }
    }

    private void scheduleThunder(World world, Location location) {
        if (world == null || location == null) {
            return;
        }
        Location soundLoc = location.clone();
        int[] delays = {0, 5, 10};
        for (int delay : delays) {
            float pitch = 0.8f + random.nextFloat() * 0.4f;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World w = soundLoc.getWorld();
                if (w != null) {
                    w.playSound(soundLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 5.0f, pitch);
                }
            }, delay);
        }
    }

    private boolean ensureStructureIntegrity(Block anchorBlock, Player player, boolean notifyPlayer) {
        if (anchorBlock == null) {
            return false;
        }
        AltarValidationResult result = AltarStructureChecker.validate(anchorBlock);
        if (!result.isValid()) {
            altarService.triggerBackfire(anchorBlock, player, result);
            if (notifyPlayer && player != null) {
                player.sendMessage("§c传送仪式因结构不稳定而崩塌！");
            }
            return false;
        }
        return true;
    }
}
