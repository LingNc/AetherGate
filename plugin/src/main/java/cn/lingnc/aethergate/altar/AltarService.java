package cn.lingnc.aethergate.altar;

import cn.lingnc.aethergate.AetherGatePlugin;
import cn.lingnc.aethergate.altar.AltarMaterialSet;
import cn.lingnc.aethergate.model.Waypoint;
import cn.lingnc.aethergate.storage.SqliteStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.block.data.type.Light;
import org.bukkit.util.Vector;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.joml.Vector3f;

public class AltarService {

    private final AetherGatePlugin plugin;
    private final SqliteStorage storage;
    private final Map<String, UUID> coreDisplays = new HashMap<>();
    private final Map<String, BukkitRunnable> coreEffects = new HashMap<>();
    private final Map<String, Location> coreLights = new HashMap<>();
    private final Map<String, Waypoint> activeAltars = new HashMap<>();
    private final Random particleRandom = new Random();

    public AltarService(AetherGatePlugin plugin) {
        this.plugin = plugin;
        this.storage = plugin.getStorage();
    }

    private String key(String world, int x, int y, int z) {
        return world + ':' + x + ':' + y + ':' + z;
    }

    public void loadExistingAltars() {
        try {
            List<Waypoint> waypoints = storage.loadAllWaypoints();
            for (Waypoint waypoint : waypoints) {
                World world = Bukkit.getWorld(waypoint.getWorldName());
                if (world == null) {
                    continue;
                }
                Location blockLoc = new Location(world, waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ());
                boolean active = waypoint.isInfinite() || waypoint.getCharges() != 0;
                String mapKey = key(world.getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
                activeAltars.put(mapKey, waypoint);
                updateVisualState(blockLoc, active);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load existing altars: " + e.getMessage());
        }
    }

    public void registerPlacedAnchor(Location loc, Player player) {
        if (player != null) {
            player.sendMessage("§7世界锚点已安放，使用末影锭激活它。");
        }
    }

    public boolean isAnchorBlock(Block block) {
        if (block == null) {
            return false;
        }
        Location loc = block.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        String mapKey = key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return activeAltars.containsKey(mapKey);
    }

    public void attemptActivation(Player player, Block lodestone) {
        if (lodestone == null) {
            return;
        }
        World world = lodestone.getWorld();
        if (world == null) {
            return;
        }
        Location loc = lodestone.getLocation();
        String mapKey = key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (activeAltars.containsKey(mapKey)) {
            if (player != null) {
                player.sendMessage("§e该祭坛已激活，可使用矿物块为其充能。");
            }
            return;
        }

        AltarValidationResult validation = AltarStructureChecker.validate(lodestone);
        if (!validation.isValid()) {
            backfire(world, loc, player, validation);
            if (player != null) {
                player.sendMessage("§c结构不完整，激活失败！");
            }
            return;
        }

        UUID owner = player != null ? player.getUniqueId() : UUID.randomUUID();
        Waypoint waypoint = new Waypoint(UUID.randomUUID(),
                "Altar (" + lodestone.getX() + "," + lodestone.getY() + "," + lodestone.getZ() + ")",
                world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), owner, 0);
        try {
            storage.saveOrUpdateWaypoint(waypoint);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save waypoint: " + e.getMessage());
            if (player != null) {
                player.sendMessage("§c数据库错误，激活失败。");
            }
            return;
        }

        activeAltars.put(mapKey, waypoint);
        updateVisualState(loc, false);
        playActivationEffects(world, loc, true, player);
        if (player != null) {
            player.sendMessage("§a激活成功！现在使用矿物块为祭坛充能。");
        }
    }

    public void recharge(Player player, Block lodestone, int amount) {
        if (lodestone == null || amount == 0) {
            return;
        }
        World world = lodestone.getWorld();
        if (world == null) {
            return;
        }
        Location loc = lodestone.getLocation();
        Waypoint existing = fetchWaypoint(loc);
        if (existing == null) {
            if (player != null) {
                player.sendMessage("§c请先使用末影锭激活祭坛。");
            }
            return;
        }

        int newCharges;
        if (existing.isInfinite() || amount < 0) {
            newCharges = -1;
        } else {
            newCharges = existing.getCharges() + amount;
        }
        Waypoint updated = new Waypoint(existing.getId(), existing.getName(), existing.getWorldName(),
                existing.getBlockX(), existing.getBlockY(), existing.getBlockZ(), existing.getOwner(), newCharges);
        try {
            storage.saveOrUpdateWaypoint(updated);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update waypoint: " + e.getMessage());
            if (player != null) {
                player.sendMessage("§c数据库错误，充能失败。");
            }
            return;
        }

        String mapKey = key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Waypoint previous = activeAltars.put(mapKey, updated);
        boolean revived = (previous == null || previous.getCharges() == 0) && newCharges != 0;
        updateVisualState(loc, newCharges != 0);
        if (revived) {
            playActivationEffects(world, loc, false, player);
        }
        if (player != null) {
            player.sendMessage("§a祭坛充能成功，当前剩余 " + (updated.isInfinite() ? "∞" : updated.getCharges()) + " 次。");
        }
    }

    public void triggerBackfire(Block lodestone, Player trigger, AltarValidationResult debugInfo) {
        if (lodestone == null) {
            return;
        }
        World world = lodestone.getWorld();
        if (world == null) {
            return;
        }
        backfire(world, lodestone.getLocation(), trigger, debugInfo);
    }

    private void backfire(World world, Location loc, Player trigger, AltarValidationResult debugInfo) {
        float power = (float) plugin.getPluginConfig().getBackfirePower();
        world.createExplosion(loc, power, false, false);
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 8, 4.0, 4.0, 4.0, 0.0);
        world.spawnParticle(Particle.EXPLOSION, center, 300, 8.0, 4.0, 8.0, 0.1);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 200, 6.0, 3.0, 6.0, 0.05);
        world.spawnParticle(Particle.LAVA, center, 150, 4.0, 2.0, 4.0, 0.05);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        Block block = loc.getBlock();
        block.setType(org.bukkit.Material.AIR);
        world.dropItemNaturally(loc, new ItemStack(org.bukkit.Material.DIAMOND, 4));
        world.dropItemNaturally(loc, new ItemStack(org.bukkit.Material.CRYING_OBSIDIAN, 1));
        removeVisuals(loc);
        activeAltars.remove(key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        try {
            storage.deleteWaypointAt(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete waypoint after backfire: " + e.getMessage());
        }
        if (trigger != null && debugInfo != null && !debugInfo.getErrors().isEmpty()
                && plugin.isDebugEnabled(trigger.getUniqueId())) {
            trigger.sendMessage("§c[调试] 结构不符合以下要求:");
            debugInfo.getErrors().stream().limit(6).forEach(msg -> trigger.sendMessage("§7- " + msg));
            if (debugInfo.getErrors().size() > 6) {
                trigger.sendMessage("§7(仅显示前 6 条，更多内容请检查结构)");
            }
        }
    }

    private void updateVisualState(Location blockLoc, boolean isActive) {
        World world = blockLoc.getWorld();
        if (world == null) {
            return;
        }
        removeVisuals(blockLoc);
        double height = isActive ? 2.0 : 1.6;
        Location displayLoc = blockLoc.clone().add(0.5, height, 0.5);
        ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(org.bukkit.Material.CONDUIT));
            d.setBillboard(Display.Billboard.CENTER);
            float scale = isActive ? 1.5f : 0.9f;
            d.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f),
                d.getTransformation().getLeftRotation(),
                new Vector3f(scale, scale, scale),
                d.getTransformation().getRightRotation()));
        });
        String mapKey = key(world.getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        coreDisplays.put(mapKey, display.getUniqueId());
        if (isActive) {
            placeCoreLight(mapKey, blockLoc);
            startCoreEffects(mapKey, display.getUniqueId(), displayLoc.clone());
        }
    }

    private void removeVisuals(Location blockLoc) {
        World world = blockLoc.getWorld();
        if (world == null) {
            return;
        }
        String mapKey = key(world.getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        UUID uuid = coreDisplays.remove(mapKey);
        if (uuid == null) {
            cancelCoreEffects(mapKey);
            removeCoreLight(mapKey);
            return;
        }
        Entity entity = world.getEntity(uuid);
        if (entity != null) {
            entity.remove();
        }
        cancelCoreEffects(mapKey);
        removeCoreLight(mapKey);
    }

    public void handleAnchorBreak(Block block) {
        if (block == null) {
            return;
        }
        Location loc = block.getLocation();
        try {
            storage.deleteWaypointAt(block.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete waypoint on break: " + e.getMessage());
        }
        removeVisuals(loc);
        activeAltars.remove(key(block.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public void clearVisuals() {
        coreDisplays.values().forEach(uuid -> {
            for (World world : Bukkit.getWorlds()) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            }
        });
        coreDisplays.clear();
        coreEffects.values().forEach(BukkitRunnable::cancel);
        coreEffects.clear();
        coreLights.values().forEach(loc -> {
            if (loc == null) return;
            Block block = loc.getBlock();
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.AIR, false);
            }
        });
        coreLights.clear();
        activeAltars.clear();
    }

    public Waypoint getActiveWaypoint(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;
        return activeAltars.get(key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public Block findNearestActiveAnchor(Location origin) {
        if (origin == null) {
            return null;
        }
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        int radius = Math.max(0, plugin.getPluginConfig().getInteractionRadius());
        Block bestBlock = null;
        double bestDistance = Double.MAX_VALUE;
        for (Waypoint waypoint : activeAltars.values()) {
            if (!world.getName().equalsIgnoreCase(waypoint.getWorldName())) {
                continue;
            }
            Block block = world.getBlockAt(waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ());
            if (block.getType() != Material.LODESTONE) {
                continue;
            }
            if (!isWithinInteractionRange(origin, block, radius)) {
                continue;
            }
            double dx = (block.getX() + 0.5) - origin.getX();
            double dy = (block.getY() + 0.5) - origin.getY();
            double dz = (block.getZ() + 0.5) - origin.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistance) {
                bestDistance = distSq;
                bestBlock = block;
            }
        }
        return bestBlock;
    }

    public boolean isWithinInteractionRange(Location origin, Block anchorBlock) {
        if (origin == null || anchorBlock == null) {
            return false;
        }
        return isWithinInteractionRange(origin, anchorBlock, Math.max(0, plugin.getPluginConfig().getInteractionRadius()));
    }

    private boolean isWithinInteractionRange(Location origin, Block anchorBlock, int radius) {
        if (origin == null || anchorBlock == null) {
            return false;
        }
        World playerWorld = origin.getWorld();
        if (playerWorld == null || !playerWorld.equals(anchorBlock.getWorld())) {
            return false;
        }
        Location anchorLoc = anchorBlock.getLocation();
        int dx = Math.abs(origin.getBlockX() - anchorLoc.getBlockX());
        int dz = Math.abs(origin.getBlockZ() - anchorLoc.getBlockZ());
        if (dx > radius || dz > radius) {
            return false;
        }
        int playerY = origin.getBlockY();
        int baseY = anchorLoc.getBlockY();
        return playerY >= baseY && playerY <= baseY + 5;
    }

    public boolean consumeCharge(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        Waypoint waypoint = fetchWaypoint(loc);
        if (waypoint == null) {
            return false;
        }
        if (waypoint.isInfinite()) {
            return true;
        }
        if (waypoint.getCharges() <= 0) {
            return false;
        }
        int newCharges = waypoint.getCharges() - 1;
        Waypoint updated = new Waypoint(waypoint.getId(), waypoint.getName(), waypoint.getWorldName(),
                waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.getOwner(), newCharges);
        try {
            storage.saveOrUpdateWaypoint(updated);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update waypoint charges: " + e.getMessage());
        }
        String mapKey = key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        activeAltars.put(mapKey, updated);
        if (newCharges == 0) {
            markDormant(updated);
        } else {
            updateVisualState(loc, true);
        }
        return true;
    }

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
        activeAltars.put(mapKey, updated);
        updateVisualState(new Location(loc.getWorld(), waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ()), false);
        breakCornerLights(loc);
        playCollapseEffects(loc);
    }

    public Collection<Waypoint> getActiveAltars() {
        return Collections.unmodifiableCollection(new ArrayList<>(activeAltars.values()));
    }

    public Waypoint findActiveByName(String name) {
        if (name == null) {
            return null;
        }
        return activeAltars.values().stream()
                .filter(waypoint -> waypoint.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public Waypoint findActiveById(UUID id) {
        if (id == null) {
            return null;
        }
        return activeAltars.values().stream()
                .filter(waypoint -> waypoint.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public Waypoint findWaypoint(Location loc) {
        return fetchWaypoint(loc);
    }

    public boolean renameWaypoint(Location loc, String newName, Player player) {
        if (newName == null) {
            return false;
        }
        String trimmed = newName.strip();
        if (trimmed.isEmpty()) {
            player.sendMessage("§c命名牌没有名称，无法重命名。");
            return false;
        }
        if (trimmed.length() > 32) {
            trimmed = trimmed.substring(0, 32);
        }
        Waypoint waypoint = fetchWaypoint(loc);
        if (waypoint == null) {
            player.sendMessage("§c未找到对应的世界锚点数据。");
            return false;
        }
        Waypoint updated = new Waypoint(waypoint.getId(), trimmed, waypoint.getWorldName(),
                waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.getOwner(), waypoint.getCharges());
        try {
            storage.saveOrUpdateWaypoint(updated);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to rename waypoint: " + e.getMessage());
            player.sendMessage("§c数据库错误，重命名失败。");
            return false;
        }
        World world = loc.getWorld();
        if (world != null) {
            activeAltars.put(key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), updated);
            updateVisualState(loc, updated.getCharges() != 0);
        }
        player.sendMessage("§a已将世界锚点命名为 " + trimmed + "。");
        return true;
    }

    private Waypoint fetchWaypoint(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        String mapKey = key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Waypoint waypoint = activeAltars.get(mapKey);
        if (waypoint != null) {
            return waypoint;
        }
        try {
            return storage.findByLocation(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load waypoint: " + e.getMessage());
            return null;
        }
    }

    private void breakCornerLights(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                Block lightBlock = world.getBlockAt(baseX + dx, baseY + 2, baseZ + dz);
                if (AltarMaterialSet.isLight(lightBlock.getType())) {
                    world.playSound(lightBlock.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);
                    lightBlock.setType(Material.AIR, false);
                }
            }
        }
    }

    private void playCollapseEffects(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        Location visualLoc = center.clone().add(0.5, 1.5, 0.5);
        world.spawnParticle(Particle.ENCHANT, visualLoc, 120, 1.2, 1.0, 1.2, 0.2);
        world.spawnParticle(Particle.END_ROD, visualLoc, 60, 0.6, 1.0, 0.6, 0.01);
        world.playSound(center, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.4f, 0.5f);
    }

    private void placeCoreLight(String mapKey, Location blockLoc) {
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

    private void removeCoreLight(String mapKey) {
        Location loc = coreLights.remove(mapKey);
        if (loc == null) {
            return;
        }
        Block block = loc.getBlock();
        if (block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
    }

    private void startCoreEffects(String mapKey, UUID displayId, Location center) {
        cancelCoreEffects(mapKey);
        BukkitRunnable task = new BukkitRunnable() {
            private double rotation = 0;
            private double bobPhase = 0;

            @Override
            public void run() {
                World world = center.getWorld();
                if (world == null) {
                    cancel();
                    coreEffects.remove(mapKey);
                    return;
                }
                Entity entity = world.getEntity(displayId);
                if (!(entity instanceof ItemDisplay itemDisplay) || entity.isDead()) {
                    cancel();
                    coreEffects.remove(mapKey);
                    return;
                }
                rotation += Math.PI / 48;
                bobPhase += 0.12;
                animateDisplay(itemDisplay, bobPhase);
                spawnCoreParticles(world, center, rotation);
            }
        };
        task.runTaskTimer(plugin, 0L, 2L);
        coreEffects.put(mapKey, task);
    }

    private void cancelCoreEffects(String mapKey) {
        BukkitRunnable task = coreEffects.remove(mapKey);
        if (task != null) {
            task.cancel();
        }
    }

    private void animateDisplay(ItemDisplay display, double phase) {
        display.setRotation(display.getYaw() + 2.5f, display.getPitch());
        Transformation transform = display.getTransformation();
        float scale = (float) (1.5 + 0.1 * Math.sin(phase));
        display.setInterpolationDuration(5);
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(transform.getTranslation(),
                transform.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                transform.getRightRotation()));
    }

    private void spawnCoreParticles(World world, Location center, double baseAngle) {
        for (int layer = 0; layer < 3; layer++) {
            double radius = 1.0 + layer * 0.4;
            double height = 0.8 + layer * 0.3;
            int points = 24 + layer * 8;
            for (int i = 0; i < points; i++) {
                double angle = baseAngle + (i / (double) points) * Math.PI * 2;
                double x = center.getX() + Math.cos(angle) * radius;
                double z = center.getZ() + Math.sin(angle) * radius;
                double y = center.getY() + height + Math.sin(baseAngle + layer) * 0.05;
                world.spawnParticle(Particle.ENCHANT, x, y, z, 1, 0, 0, 0, 0);
            }
        }
        if (particleRandom.nextDouble() < 0.2) {
            double angle = particleRandom.nextDouble() * Math.PI * 2;
            double speed = 0.15 + particleRandom.nextDouble() * 0.05;
            world.spawnParticle(Particle.END_ROD,
                    center.getX(),
                    center.getY() + 0.5,
                    center.getZ(),
                    0,
                    Math.cos(angle) * speed,
                    0.08,
                    Math.sin(angle) * speed,
                    0.0);
        }
    }

    private void playActivationEffects(World world, Location loc, boolean firstActivation, Player trigger) {
        if (world == null) {
            return;
        }
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        Sound sound = firstActivation ? Sound.BLOCK_END_PORTAL_SPAWN : Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
        float pitch = firstActivation ? 0.85f : 1.15f;
        world.playSound(center, sound, 10.0f, pitch);
        spawnActivationWave(world, center);
        knockbackActivation(world, center, trigger);
    }

    private void spawnActivationWave(World world, Location center) {
        for (int ring = 0; ring <= 15; ring++) {
            if (ring == 0) {
                world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.2, 0), 40, 0.4, 0.6, 0.4, 0.02);
                continue;
            }
            double radius = ring;
            int points = Math.max(18, ring * 12);
            Particle particle = ring < 8 ? Particle.END_ROD : Particle.FIREWORK;
            for (int i = 0; i < points; i++) {
                double angle = (i / (double) points) * Math.PI * 2;
                double drop = Math.max(0.1, 1.2 - ring / 12.0);
                double x = center.getX() + Math.cos(angle + particleRandom.nextDouble() * 0.1) * radius;
                double z = center.getZ() + Math.sin(angle + particleRandom.nextDouble() * 0.1) * radius;
                double y = center.getY() + 0.8 + Math.sin(angle * 2) * drop;
                world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void knockbackActivation(World world, Location center, Player trigger) {
        Collection<Entity> nearby = world.getNearbyEntities(center, 20.0, 10.0, 20.0);
        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (trigger != null && entity.getUniqueId().equals(trigger.getUniqueId())) {
                continue;
            }
            Vector push = living.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() == 0) {
                push = new Vector(0, 0.5, 0);
            } else {
                push.normalize().multiply(1.5).setY(0.5);
            }
            living.setVelocity(push);
        }
    }
}
