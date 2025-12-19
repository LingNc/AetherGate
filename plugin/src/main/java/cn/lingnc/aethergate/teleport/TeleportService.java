package cn.lingnc.aethergate.teleport;

import cn.lingnc.aethergate.AetherGatePlugin;
import cn.lingnc.aethergate.altar.AltarService;
import cn.lingnc.aethergate.altar.AltarStructureChecker;
import cn.lingnc.aethergate.altar.AltarValidationResult;
import cn.lingnc.aethergate.model.Waypoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the multi-phase teleport ritual, including locking players, particle playback,
 * and orchestrating pearl/charge consumption at the correct timing milestone.
 */
public class TeleportService {

    private static final int WARMUP_TICKS = 60; // ~3 seconds
    private static final int RECOVERY_TICKS = 40; // ~2 seconds cool down window
    private static final int PREVIEW_TICKS = 30; // ticks before teleport to show arrival beam
    private static final double HELIX_RADIUS = 0.7;
    private static final double VERTICAL_STEP = 0.1;
    private static final double SKY_HEIGHT = 40.0;

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
        Entity playerRoot = findRootVehicle(player);
        UUID playerRootId = playerRoot.getUniqueId();
        if (globalLockedEntities.contains(playerRootId)) {
            player.sendMessage("§c该实体正在被另一个传送占用。");
            return false;
        }
        if (playerRoot.getPassengers().stream().anyMatch(p -> p instanceof Player && !((Player) p).getUniqueId().equals(uuid))) {
            player.sendMessage("§c无法传送：你的载具上有其他玩家。");
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
        List<Entity> targets = collectTargets(player, originBlock.getLocation(), playerRoot);
        int paidCost = computePaidCost(player, targets);
        int totalCost = 1 + paidCost; // player base cost + paid entities
        if (!costManager.hasEnoughPearls(originBlock.getLocation(), player, totalCost)) {
            player.sendMessage("§c传送失败：需要 " + totalCost + " 份末影能量。");
            return false;
        }
        TeleportTask task = new TeleportTask(player, originBlock.getLocation(), originWaypoint,
            liveDestination, arrivalSpot, targets, totalCost);
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

    public void handleMove(PlayerMoveEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        TeleportTask task = activeTasks.get(uuid);
        if (task == null) {
            return;
        }
        // Movement no longer interrupts the ritual; keep listener to retain future extensibility.
        if (task.hasPerformedTeleport()) {
            return;
        }
    }

    public boolean isPlayerLocked(UUID uuid) {
        return activeTasks.containsKey(uuid);
    }

    public int getRemainingWarmupTicks(UUID uuid) {
        TeleportTask task = activeTasks.get(uuid);
        return task == null ? -1 : task.getRemainingWarmupTicks();
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

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
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

    private List<Entity> collectTargets(Player player, Location originLoc, Entity playerRoot) {
        List<Entity> roots = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        if (playerRoot != null && seen.add(playerRoot.getUniqueId())) {
            roots.add(playerRoot);
        }
        World world = originLoc.getWorld();
        if (world == null) {
            return roots;
        }
        double range = Math.max(2.5, plugin.getPluginConfig().getInteractionRadius() + 0.5);
        Collection<Entity> nearby = world.getNearbyEntities(originLoc, range, 3.0, range);
        for (Entity entity : nearby) {
            Entity root = findRootVehicle(entity);
            if (root == null || !seen.add(root.getUniqueId())) {
                continue;
            }
            if (globalLockedEntities.contains(root.getUniqueId())) {
                continue;
            }
            if (root instanceof Player other && !other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (root instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() != null
                    && !tameable.getOwner().getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (root.getPassengers().stream().anyMatch(p -> p instanceof Player && !((Player) p).getUniqueId().equals(player.getUniqueId()))) {
                continue;
            }
            if (root instanceof Interaction || root instanceof Marker || root instanceof ItemDisplay || root instanceof BlockDisplay) {
                continue;
            }
            roots.add(root);
        }
        return roots;
    }

    private int computePaidCost(Player player, List<Entity> targets) {
        int paid = 0;
        UUID playerId = player.getUniqueId();
        for (Entity target : targets) {
            if (target == null) {
                continue;
            }
            if (target.getUniqueId().equals(playerId)) {
                continue; // player base cost handled separately
            }
            if (target instanceof Item) {
                continue; // loose items are free
            }
            if (target instanceof Tameable tameable && tameable.isTamed()
                    && tameable.getOwner() != null
                    && tameable.getOwner().getUniqueId().equals(playerId)) {
                continue; // owned pets are free
            }
            paid++;
        }
        return paid;
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
        private final List<Entity> targets = new ArrayList<>();
        private final Set<UUID> lockedRoots = new HashSet<>();
        private final Map<UUID, List<UUID>> rideRelations = new HashMap<>();
        private final Map<UUID, Vector> arrivalOffsets = new HashMap<>();
        private final int totalCost;
        private boolean performedTeleport = false;
        private boolean internalTeleporting = false;
        private int tick = 0;

        TeleportTask(Player player, Location originBlockLoc, Waypoint origin,
                     Waypoint destination, Location arrival, List<Entity> targets, int totalCost) {
            this.player = player;
            this.originBlockLoc = originBlockLoc.clone();
            this.destination = destination;
            this.arrival = arrival.clone();
            this.lockPoint = player.getLocation().clone();
            this.prevInvulnerable = player.isInvulnerable();
            this.prevWalkSpeed = player.getWalkSpeed();
            this.prevFlySpeed = player.getFlySpeed();
            this.totalCost = totalCost;
            Set<UUID> seen = new HashSet<>();
            for (Entity target : targets) {
                addTargetWithPassengers(target, seen);
            }
            addTargetWithPassengers(player, seen);
            for (Entity target : this.targets) {
                if (target != null) {
                    UUID id = target.getUniqueId();
                    lockedRoots.add(id);
                    globalLockedEntities.add(id);
                    arrivalOffsets.put(id, computeArrivalOffset(target));
                }
            }
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, WARMUP_TICKS + 20, 1, true, false, false));
            player.setWalkSpeed(0f);
            player.setFlySpeed(0f);
            player.setSprinting(false);
        }

        private boolean consumeResources() {
            Block anchorBlock = originBlockLoc.getBlock();
            if (anchorBlock.getType() != Material.LODESTONE || !altarService.isAnchorBlock(anchorBlock)) {
                return false;
            }
            if (!costManager.consumePearls(originBlockLoc, player, totalCost)) {
                return false;
            }
            return altarService.consumeCharge(originBlockLoc);
        }

        private void executeTeleport() {
            performedTeleport = true;
            captureRideRelations();
            dismountAll();
            World world = originBlockLoc.getWorld();
            if (world != null) {
                world.strikeLightningEffect(originBlockLoc);
                spawnDepartureBurst(world);
                scheduleThunder(world, originBlockLoc.clone().add(0.5, 0.0, 0.5));
            }
            teleportTargets();
            World arrivalWorld = arrival.getWorld();
            if (arrivalWorld != null) {
                Set<UUID> safeIds = new HashSet<>();
                safeIds.add(player.getUniqueId());
                for (Entity target : targets) {
                    if (target != null) {
                        safeIds.add(target.getUniqueId());
                    }
                }

                arrivalWorld.strikeLightningEffect(arrival);
                spawnArrivalBurst(arrivalWorld);
                spawnArrivalShockwave(arrivalWorld, arrival);
                knockbackNearby(arrivalWorld, safeIds);
                scheduleThunder(arrivalWorld, arrival.clone());
            }
            plugin.getAchievementService().handleTeleportComplete(player);
            player.playSound(arrival, Sound.ENTITY_ENDERMAN_STARE, 0.8f, 1.4f);
            player.sendMessage("§b空间折跃完成，正在重构形体……");
        }

        private void teleportTargets() {
            World destWorld = arrival.getWorld();
            if (destWorld == null) {
                return;
            }
            destWorld.getChunkAt(arrival).load();
            List<Entity> nonPlayers = new ArrayList<>();
            List<Player> players = new ArrayList<>();
            for (Entity target : targets) {
                if (target == null) {
                    continue;
                }
                if (target instanceof Player p) {
                    players.add(p);
                } else {
                    nonPlayers.add(target);
                }
            }
            for (Entity target : nonPlayers) {
                teleportEntity(target);
            }
            for (Player p : players) {
                teleportEntity(p);
            }
            remountLater();
        }

        private void teleportEntity(Entity entity) {
            if (entity == null || !entity.isValid()) {
                return;
            }
            Location dest = arrival.clone();
            Vector offset = arrivalOffsets.get(entity.getUniqueId());
            if (offset != null) {
                dest.add(offset);
            }
            dest = ensureSafeLanding(entity, dest);
            if (entity instanceof Player p) {
                internalTeleporting = true;
                p.teleport(dest);
                internalTeleporting = false;
            } else {
                entity.teleport(dest);
                if (entity instanceof LivingEntity living) {
                    living.setNoDamageTicks(40);
                }
            }
        }

        private void remountLater() {
            Bukkit.getScheduler().runTaskLater(plugin, this::reapplyMounts, 2L);
        }

        private void reapplyMounts() {
            for (Map.Entry<UUID, List<UUID>> entry : rideRelations.entrySet()) {
                Entity vehicle = findEntity(entry.getKey());
                if (vehicle == null || !vehicle.isValid()) {
                    continue;
                }
                for (UUID passengerId : entry.getValue()) {
                    Entity passenger = findEntity(passengerId);
                    if (passenger == null || !passenger.isValid()) {
                        continue;
                    }
                    vehicle.addPassenger(passenger);
                }
            }
        }

        private Entity findEntity(UUID id) {
            for (Entity target : targets) {
                if (target != null && target.getUniqueId().equals(id)) {
                    return target;
                }
            }
            return Bukkit.getEntity(id);
        }

        private void dismountAll() {
            for (Entity entity : targets) {
                if (entity == null || !entity.isValid()) {
                    continue;
                }
                List<Entity> passengers = new ArrayList<>(entity.getPassengers());
                for (Entity passenger : passengers) {
                    entity.removePassenger(passenger);
                }
                entity.eject();
            }
        }

        private void captureRideRelations() {
            rideRelations.clear();
            for (Entity vehicle : targets) {
                if (vehicle == null || !vehicle.isValid()) {
                    continue;
                }
                List<Entity> passengers = vehicle.getPassengers();
                if (passengers.isEmpty()) {
                    continue;
                }
                List<UUID> ids = new ArrayList<>();
                for (Entity passenger : passengers) {
                    ids.add(passenger.getUniqueId());
                }
                rideRelations.put(vehicle.getUniqueId(), ids);
            }
        }

        private Vector computeArrivalOffset(Entity entity) {
            if (entity instanceof Player) {
                return new Vector(0, 0, 0);
            }
            double offsetX = (random.nextDouble() - 0.5) * 0.8;
            double offsetZ = (random.nextDouble() - 0.5) * 0.8;
            return new Vector(offsetX, 0.0, offsetZ);
        }

        private Location ensureSafeLanding(Entity entity, Location dest) {
            if (dest == null || dest.getWorld() == null || entity == null) {
                return dest;
            }
            Location candidate = dest.clone();
            if (isAreaPassable(candidate, entity)) {
                return candidate;
            }
            Location center = arrival.clone();
            if (isAreaPassable(center, entity)) {
                return center;
            }
            Location above = candidate.clone().add(0, 1, 0);
            if (isAreaPassable(above, entity)) {
                return above;
            }
            return center;
        }

        private boolean isAreaPassable(Location loc, Entity entity) {
            World world = loc.getWorld();
            if (world == null) {
                return false;
            }
            int heightBlocks = Math.max(1, (int) Math.ceil(Math.max(1.0, entity.getBoundingBox().getHeight())));
            for (int y = 0; y <= heightBlocks; y++) {
                Block block = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + y, loc.getBlockZ());
                if (!block.isPassable()) {
                    return false;
                }
            }
            return true;
        }

        private void addTargetWithPassengers(Entity entity, Set<UUID> seen) {
            if (entity == null || !seen.add(entity.getUniqueId())) {
                return;
            }
            targets.add(entity);
            for (Entity passenger : entity.getPassengers()) {
                addTargetWithPassengers(passenger, seen);
            }
        }

        private void spawnWarmupParticles() {
            if (lockPoint.getWorld() == null) {
                return;
            }
            double progress = Math.min(1.0, (double) tick / WARMUP_TICKS);
            double currentHeight = (progress < 0.9)
                    ? (2.2 * (progress / 0.9))
                    : (2.2 + (SKY_HEIGHT * (progress - 0.9) * 10));

            Location center = lockPoint.clone();
            double timeOffset = tick * 0.35;
            double baseRadius = HELIX_RADIUS;

            for (double h = 0.0; h <= currentHeight; h += VERTICAL_STEP) {
                double angle = h * 3.0 + timeOffset;

                double r1 = baseRadius + Math.sin(h * 5 + tick * 0.1) * 0.1;
                spawnParticleAt(center, r1, h, angle, Particle.END_ROD);

                for (int i = 0; i < 2; i++) {
                    double offsetAngle = angle + Math.PI + (i * 0.5);
                    spawnParticleAt(center, baseRadius, h, offsetAngle, Particle.ENCHANT, 0.2);
                    if (h % 0.3 < 0.1) {
                        spawnParticleAt(center, baseRadius * 1.5, h, offsetAngle, Particle.ENCHANT, 0.2);
                    }
                }

                if (h > 1.5 && h < 1.8 && progress > 0.8) {
                    center.getWorld().spawnParticle(Particle.END_ROD,
                            center.getX(), center.getY() + 1.6, center.getZ(),
                            5, 0.2, 0.2, 0.2, 0.05);
                }
            }

            for (Entity entity : targets) {
                if (entity == null || !entity.isValid() || entity.getWorld() == null) {
                    continue;
                }
                if (entity.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                Location compCenter = entity.getLocation().toCenterLocation();
                double height = Math.max(1.0, entity.getBoundingBox().getHeight());
                double maxHeight = Math.min(height + 0.5, currentHeight);
                double companionRadius = Math.max(0.5, baseRadius * 0.8);
                for (double h = 0.0; h <= maxHeight; h += VERTICAL_STEP) {
                    double angle = h * 3.0 + timeOffset;
                    spawnParticleAt(compCenter, companionRadius, h, angle, Particle.END_ROD);
                    spawnParticleAt(compCenter, companionRadius, h, angle + Math.PI, Particle.WITCH, 0.15);
                }
            }
        }

        private void spawnArrivalPreview() {
            if (arrival.getWorld() == null) {
                return;
            }
            int remaining = WARMUP_TICKS - tick;
            double progress = 1.0 - (Math.max(0, remaining) / (double) PREVIEW_TICKS);
            progress = Math.min(1.0, Math.max(0.0, progress));

            double dropProgress = Math.min(1.0, progress * 3.0);
            double currentBottom = SKY_HEIGHT * (1.0 - dropProgress);
            double timeOffset = tick * 0.35;

            for (double h = currentBottom; h <= 20.0; h += 0.2) {
                if (h < 0.0) {
                    continue;
                }
                double angle = h * 3.0 - timeOffset;
                spawnParticleAt(arrival, HELIX_RADIUS, h, angle, Particle.ENCHANT, 0.1);
                spawnParticleAt(arrival, HELIX_RADIUS, h, angle + Math.PI, Particle.ENCHANT, 0.1);
                if (h % 0.4 < 0.1) {
                    spawnParticleAt(arrival, HELIX_RADIUS, h, angle + Math.PI / 2.0, Particle.END_ROD);
                }
            }

            for (Entity entity : targets) {
                if (entity == null || !entity.isValid()) {
                    continue;
                }
                if (entity.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                Location dest = arrival.clone();
                Vector offset = arrivalOffsets.get(entity.getUniqueId());
                if (offset != null) {
                    dest.add(offset);
                }
                double companionRadius = Math.max(0.6, HELIX_RADIUS * 0.9);
                double height = Math.max(1.0, entity.getBoundingBox().getHeight());
                double maxHeight = Math.min(15.0, height + 1.0);
                for (double h = currentBottom; h <= maxHeight; h += 0.2) {
                    if (h < 0.0) {
                        continue;
                    }
                    double angle = h * 3.0 - timeOffset;
                    spawnParticleAt(dest, companionRadius, h, angle, Particle.ENCHANT, 0.1);
                    if (h % 0.5 < 0.12) {
                        spawnParticleAt(dest, companionRadius, h, angle + Math.PI, Particle.END_ROD);
                    }
                }
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
            Location base = originBlockLoc.clone().add(0.5, 0.0, 0.5);
            for (int i = 0; i < 50; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = 0.5 + random.nextDouble() * 0.5;
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;
                world.spawnParticle(Particle.END_ROD,
                        base.getX() + x, base.getY(), base.getZ() + z,
                        0, 0.0, 3.0 + random.nextDouble(), 0.0, 1.0);
            }
            world.spawnParticle(Particle.ENCHANT, base.clone().add(0, 1, 0), 100, 0.5, 5.0, 0.5, 0.1);
            world.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
        }

        private void spawnArrivalBurst(World world) {
            Location center = arrival.clone().add(0, 1.0, 0);
            for (int i = 0; i < 60; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = 3.0;
                double xOffset = Math.cos(angle) * r;
                double zOffset = Math.sin(angle) * r;
                double yOffset = (random.nextDouble() - 0.5) * 2.0;
                world.spawnParticle(Particle.END_ROD,
                        center.getX() + xOffset, center.getY() + yOffset, center.getZ() + zOffset,
                        0, -xOffset * 0.15, -yOffset * 0.15, -zOffset * 0.15, 1.0);
            }
            world.spawnParticle(Particle.ENCHANT, center, 100, 2.0, 2.0, 2.0, 1.0);
            world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }

        private void spawnCancelParticles() {
            World world = lockPoint.getWorld();
            if (world == null) {
                return;
            }
            world.spawnParticle(Particle.CLOUD, lockPoint.clone().add(0, 1, 0), 30, 0.5, 1.0, 0.5, 0.05);

            if (tick >= WARMUP_TICKS - PREVIEW_TICKS && arrival.getWorld() != null) {
                for (double h = 0.0; h < 5.0; h += 0.5) {
                    arrival.getWorld().spawnParticle(Particle.ENCHANT, arrival.clone().add(0, h, 0),
                            10, 0.5, 0.0, 0.5, 0.2);
                        arrival.getWorld().spawnParticle(Particle.SMOKE, arrival.clone().add(0, h, 0),
                            5, 0.2, 0.0, 0.2, 0.05);
                }
            }
            world.playSound(lockPoint, Sound.BLOCK_CANDLE_EXTINGUISH, 1.0f, 1.0f);
        }

        private void spawnArrivalShockwave(World world, Location center) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
            world.spawnParticle(Particle.EXPLOSION, center, 40, 2.0, 0.5, 2.0, 0.05);
            world.spawnParticle(Particle.FLASH, center, 1);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        }

        private void spawnParticleAt(Location center, double radius, double y, double angle, Particle particle) {
            spawnParticleAt(center, radius, y, angle, particle, 0.0);
        }

        private void spawnParticleAt(Location center, double radius, double y, double angle, Particle particle, double jitter) {
            if (center == null || center.getWorld() == null) {
                return;
            }
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            if (jitter > 0.0) {
                x += (random.nextDouble() - 0.5) * jitter;
                z += (random.nextDouble() - 0.5) * jitter;
            }
            center.getWorld().spawnParticle(particle, x, center.getY() + y, z, 1, 0, 0, 0, 0);
        }

        private void knockbackNearby(World world, Set<UUID> safeEntities) {
            Collection<Entity> nearby = world.getNearbyEntities(arrival, 5.0, 3.0, 5.0);
            for (Entity entity : nearby) {
                if (entity == null) {
                    continue;
                }
                if (safeEntities.contains(entity.getUniqueId())) {
                    continue;
                }
                if (entity instanceof Tameable tameable && tameable.isTamed() && tameable.getOwner() != null
                        && safeEntities.contains(tameable.getOwner().getUniqueId())) {
                    continue;
                }
                if (!(entity instanceof LivingEntity living)) {
                    continue;
                }
                Vector push = entity.getLocation().toVector().subtract(arrival.toVector());
                if (push.lengthSquared() < 0.0001) {
                    push = new Vector(0, 0.5, 0);
                } else {
                    push.normalize().multiply(0.8).setY(0.35);
                }
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
            if (!performedTeleport) {
                spawnCancelParticles();
            }
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
            lockedRoots.forEach(globalLockedEntities::remove);
        }

        Location getLockPoint() {
            return lockPoint.clone();
        }

        boolean isInternalTeleport() {
            return internalTeleporting;
        }

        int getRemainingWarmupTicks() {
            return Math.max(0, WARMUP_TICKS - tick);
        }

        boolean hasPerformedTeleport() {
            return performedTeleport;
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
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION);
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
