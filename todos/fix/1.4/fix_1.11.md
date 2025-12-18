这是一个针对 AetherGate v1.11 的修复说明书。

修复概要：

逻辑修正 - 枯竭保留: 祭坛耐久耗尽后不再从内存中移除，而是转为“休眠态 (Dormant)”。休眠的祭坛可以作为传送目的地，但不能作为起点。视觉上仅保留核心实体（静止、无光）。

逻辑修正 - 资源消耗: PearlCostManager 调整为 优先消耗末影珍珠，只有在没有珍珠时才消耗末影锭（并找零）。

逻辑修正 - 激活与充能:

激活: 必须使用 末影锭 右键。触发结构检查。激活后耐久为 0，处于休眠态，需充能后使用。

充能: 使用 矿物块 右键。不检查结构完整性，直接增加耐久。如果祭坛未激活（数据库无记录），提示需先用末影锭激活。

数据层修正: 修改 SQLite 查询，加载所有祭坛（包括耐久为 0 的）。

请将以下文档交付给开发人员。

AetherGate 修复与逻辑重构说明书 (v1.11)
优先级: 紧急 (Logic Fix) 目标版本: Paper 1.21.4

1. 资源消耗优先级修正
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/teleport/PearlCostManager.java

修改: 调整检测顺序，先 consumePearl 再 consumeIngot。

2. 数据库查询修正 (加载休眠祭坛)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/storage/SqliteStorage.java

修改: loadActiveWaypoints 方法移除 WHERE charges <> 0 条件，加载所有祭坛。
package cn.lingnc.aethergate.storage;

import cn.lingnc.aethergate.model.Waypoint;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqliteStorage {

    private final File dbFile;

    public SqliteStorage(File dataFolder) {
        this.dbFile = new File(dataFolder, "aether_gate.db");
    }

    public void init() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS waypoints (" +
                    "uuid TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "x REAL NOT NULL," +
                    "y REAL NOT NULL," +
                    "z REAL NOT NULL," +
                    "owner TEXT NOT NULL," +
                    "charges INTEGER NOT NULL" +
                    ")");
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    public List<Waypoint> loadAllWaypoints() throws SQLException {
        List<Waypoint> list = new ArrayList<>();
        // 修改：移除 WHERE charges <> 0，加载所有锚点以便作为目的地
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM waypoints")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                UUID owner = UUID.fromString(rs.getString("owner"));
                int charges = rs.getInt("charges");
                list.add(new Waypoint(id, name, world, x, y, z, owner, charges));
            }
        }
        return list;
    }

    public Waypoint findByLocation(String world, int x, int y, int z) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM waypoints WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UUID id = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                String w = rs.getString("world");
                double lx = rs.getDouble("x");
                double ly = rs.getDouble("y");
                double lz = rs.getDouble("z");
                UUID owner = UUID.fromString(rs.getString("owner"));
                int charges = rs.getInt("charges");
                return new Waypoint(id, name, w, lx, ly, lz, owner, charges);
            }
        }
        return null;
    }

    public void saveOrUpdateWaypoint(Waypoint waypoint) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO waypoints(uuid,name,world,x,y,z,owner,charges) VALUES(?,?,?,?,?,?,?,?) " +
                             "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, owner=excluded.owner, charges=excluded.charges")) {
            ps.setString(1, waypoint.getId().toString());
            ps.setString(2, waypoint.getName());
            ps.setString(3, waypoint.getWorldName());
            ps.setDouble(4, waypoint.getX());
            ps.setDouble(5, waypoint.getY());
            ps.setDouble(6, waypoint.getZ());
            ps.setString(7, waypoint.getOwner().toString());
            ps.setInt(8, waypoint.getCharges());
            ps.executeUpdate();
        }
    }

    public void deleteWaypoint(UUID id) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM waypoints WHERE uuid=?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    public void deleteWaypointAt(String world, int x, int y, int z) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM waypoints WHERE world=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        }
    }
}

打开

3. 祭坛服务核心逻辑修正 (AltarService)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java

修改重点:

状态分离: 区分 attemptActivation (结构检查, 初始0耐久) 和 recharge (无结构检查, 加耐久)。

视觉分离: 区分 Active (亮, 大核心, 粒子) 和 Dormant (暗, 小核心, 无粒子) 的视觉生成逻辑。

枯竭逻辑: markDormant 不再从 activeAltars 移除，只更新视觉。

package cn.lingnc.aethergate.altar;

import cn.lingnc.aethergate.AetherGatePlugin;
import cn.lingnc.aethergate.model.Waypoint;
import cn.lingnc.aethergate.storage.SqliteStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class AltarService {

    private final AetherGatePlugin plugin;
    private final SqliteStorage storage;
    private final Map<String, UUID> coreDisplays = new HashMap<>();
    private final Map<String, BukkitRunnable> coreEffects = new HashMap<>();
    private final Map<String, Location> coreLights = new HashMap<>();
    // activeAltars 现在存储所有祭坛，包括 dormant 的
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
            // 加载所有祭坛，包括 charges=0 的
            List<Waypoint> all = storage.loadAllWaypoints();
            for (Waypoint waypoint : all) {
                World world = Bukkit.getWorld(waypoint.getWorldName());
                if (world == null) {
                    continue;
                }
                Location blockLoc = new Location(world, waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ());
                activeAltars.put(key(world.getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ()), waypoint);
                updateVisualState(blockLoc, waypoint.getCharges() != 0);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load existing altars: " + e.getMessage());
        }
    }

    public boolean isAnchorBlock(Block block) {
        if (block == null) return false;
        Location loc = block.getLocation();
        World world = loc.getWorld();
        if (world == null) return false;
        return activeAltars.containsKey(key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    // 新增：激活逻辑 (需要结构检查，初始耐久为0)
    public void attemptActivation(Player player, Block lodestone) {
        Location loc = lodestone.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // 检查是否已存在
        if (getActiveWaypoint(loc) != null) {
            player.sendMessage("§e该世界锚点已经激活过了。");
            return;
        }

        // 结构检查
        AltarValidationResult validation = AltarStructureChecker.validate(lodestone);
        if (!validation.isValid()) {
            triggerBackfire(lodestone, player, validation);
            player.sendMessage("§c结构不完整，激活失败导致能量反噬！");
            return;
        }

        // 创建数据 (初始耐久 0)
        UUID owner = player.getUniqueId();
        Waypoint target = new Waypoint(UUID.randomUUID(),
                "Anchor (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")",
                world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), owner, 0);

        try {
            storage.saveOrUpdateWaypoint(target);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save new waypoint: " + e.getMessage());
            player.sendMessage("§c数据库错误。");
            return;
        }

        activeAltars.put(key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), target);

        // 播放激活音效和特效 (无伤爆炸)
        world.createExplosion(loc.clone().add(0.5, 1, 0.5), 0F, false); // 纯音效和少量烟雾
        playActivationEffects(world, loc, true, player);

        // 生成视觉 (Dormant 状态)
        updateVisualState(loc, false);

        player.sendMessage("§a世界锚点激活成功！请使用矿物块为其充能。");
    }

    // 新增：充能逻辑 (无结构检查)
    public void recharge(Player player, Block lodestone, int amount) {
        Location loc = lodestone.getLocation();
        Waypoint existing = getActiveWaypoint(loc);

        if (existing == null) {
            player.sendMessage("§c该锚点尚未激活，请先使用 §b末影锭 §c进行激活。");
            return;
        }

        boolean wasDormant = existing.getCharges() == 0;
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
            plugin.getLogger().severe("Failed to update charges: " + e.getMessage());
            return;
        }

        activeAltars.put(key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), updated);

        // 如果是从 dormant 变为 active，更新视觉
        if (wasDormant && newCharges != 0) {
            updateVisualState(loc, true);
            playActivationEffects(loc.getWorld(), loc, false, player); // 播放充能音效
        }

        player.sendMessage("§a充能成功！当前剩余: " + (updated.isInfinite() ? "∞" : updated.getCharges()));
    }

    public void triggerBackfire(Block lodestone, Player trigger, AltarValidationResult debugInfo) {
        if (lodestone == null) return;
        World world = lodestone.getWorld();
        if (world == null) return;

        Location loc = lodestone.getLocation();
        world.createExplosion(loc, 6.0f, false, false);
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 8, 4.0, 4.0, 4.0, 0.0);
        world.spawnParticle(Particle.EXPLOSION, center, 300, 8.0, 4.0, 8.0, 0.1);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 200, 6.0, 3.0, 6.0, 0.05);
        world.spawnParticle(Particle.LAVA, center, 150, 4.0, 2.0, 4.0, 0.05);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);

        Block block = loc.getBlock();
        block.setType(Material.AIR);
        world.dropItemNaturally(loc, new ItemStack(Material.DIAMOND, 4));
        world.dropItemNaturally(loc, new ItemStack(Material.CRYING_OBSIDIAN, 1));

        removeVisuals(loc);
        activeAltars.remove(key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        try {
            storage.deleteWaypointAt(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete waypoint after backfire: " + e.getMessage());
        }

        if (trigger != null && debugInfo != null && !debugInfo.getErrors().isEmpty() && plugin.isDebugEnabled(trigger.getUniqueId())) {
            trigger.sendMessage("§c[调试] 结构错误:");
            debugInfo.getErrors().stream().limit(6).forEach(msg -> trigger.sendMessage("§7- " + msg));
        }
    }

    private void updateVisualState(Location blockLoc, boolean isActive) {
        World world = blockLoc.getWorld();
        if (world == null) return;

        // 清理旧的
        removeVisuals(blockLoc);

        Location displayLoc = blockLoc.clone().add(0.5, 2.0, 0.5);
        ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.CONDUIT));
            d.setBillboard(Display.Billboard.CENTER);
            float scale = isActive ? 1.5f : 1.0f; // 激活时大，休眠时小
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    d.getTransformation().getLeftRotation(),
                    new Vector3f(scale, scale, scale),
                    d.getTransformation().getRightRotation()));
        });

        String mapKey = key(world.getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
        coreDisplays.put(mapKey, display.getUniqueId());

        if (isActive) {
            placeCoreLight(mapKey, blockLoc);
            startCoreEffects(mapKey, display.getUniqueId(), displayLoc);
        }
    }

    public void removeVisuals(Location blockLoc) {
        World world = blockLoc.getWorld();
        if (world == null) return;
        String mapKey = key(world.getName(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());

        UUID uuid = coreDisplays.remove(mapKey);
        if (uuid != null) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) entity.remove();
        }
        cancelCoreEffects(mapKey);
        removeCoreLight(mapKey);
    }

    public void handleAnchorBreak(Block block) {
        if (block == null) return;
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
                if (entity != null) entity.remove();
            }
        });
        coreDisplays.clear();
        coreEffects.values().forEach(BukkitRunnable::cancel);
        coreEffects.clear();
        coreLights.values().forEach(loc -> {
            if (loc == null) return;
            Block block = loc.getBlock();
            if (block.getType() == Material.LIGHT) block.setType(Material.AIR, false);
        });
        coreLights.clear();
        activeAltars.clear();
    }

    public Waypoint getActiveWaypoint(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;
        return activeAltars.get(key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public boolean consumeCharge(Location loc) {
        Waypoint waypoint = getActiveWaypoint(loc);
        if (waypoint == null) return false;
        if (waypoint.isInfinite()) return true;

        int newCharges = waypoint.getCharges() - 1;
        if (newCharges < 0) return false; // 防止负数

        Waypoint updated = new Waypoint(waypoint.getId(), waypoint.getName(), waypoint.getWorldName(),
                waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.getOwner(), newCharges);

        try {
            storage.saveOrUpdateWaypoint(updated);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update charges: " + e.getMessage());
            return false;
        }

        activeAltars.put(key(waypoint.getWorldName(), waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ()), updated);

        if (newCharges == 0) {
            markDormant(updated);
        }
        return true;
    }

    private void markDormant(Waypoint waypoint) {
        Location loc = waypoint.toLocation();
        if (loc == null) return;

        // 不再移除 activeAltars 记录，只更新视觉
        updateVisualState(loc, false);
        breakCornerLights(loc);
        playCollapseEffects(loc);
    }

    // Getters and Helpers
    public Collection<Waypoint> getActiveAltars() {
        return Collections.unmodifiableCollection(new ArrayList<>(activeAltars.values()));
    }

    public Waypoint findActiveByName(String name) {
        if (name == null) return null;
        return activeAltars.values().stream()
                .filter(waypoint -> waypoint.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public Waypoint findActiveById(UUID id) {
        if (id == null) return null;
        return activeAltars.values().stream()
                .filter(waypoint -> waypoint.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public boolean renameWaypoint(Location loc, String newName, Player player) {
        Waypoint waypoint = getActiveWaypoint(loc);
        if (waypoint == null) {
            player.sendMessage("§c未找到对应的世界锚点数据。");
            return false;
        }
        String trimmed = newName.strip();
        if (trimmed.isEmpty() || trimmed.length() > 32) return false;

        Waypoint updated = new Waypoint(waypoint.getId(), trimmed, waypoint.getWorldName(),
                waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.getOwner(), waypoint.getCharges());
        try {
            storage.saveOrUpdateWaypoint(updated);
        } catch (SQLException e) {
            return false;
        }
        activeAltars.put(key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), updated);
        return true;
    }

    // Visual Helpers
    private void placeCoreLight(String mapKey, Location blockLoc) {
        Block lightBlock = blockLoc.clone().add(0, 2, 0).getBlock();
        if (lightBlock.getType() != Material.AIR && lightBlock.getType() != Material.LIGHT) return;
        lightBlock.setType(Material.LIGHT, false);
        if (lightBlock.getBlockData() instanceof Light lightData) {
            lightData.setLevel(15);
            lightBlock.setBlockData(lightData, false);
        }
        coreLights.put(mapKey, lightBlock.getLocation());
    }

    private void removeCoreLight(String mapKey) {
        Location loc = coreLights.remove(mapKey);
        if (loc != null && loc.getBlock().getType() == Material.LIGHT) {
            loc.getBlock().setType(Material.AIR, false);
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
                if (world == null) { cancel(); coreEffects.remove(mapKey); return; }
                Entity entity = world.getEntity(displayId);
                if (!(entity instanceof ItemDisplay itemDisplay) || entity.isDead()) {
                    cancel(); coreEffects.remove(mapKey); return;
                }
                rotation += Math.PI / 48;
                bobPhase += 0.12;

                // Active Animation
                itemDisplay.setRotation(itemDisplay.getYaw() + 2.5f, itemDisplay.getPitch());
                float scale = (float) (1.5 + 0.1 * Math.sin(bobPhase));
                itemDisplay.setInterpolationDuration(5);
                itemDisplay.setTransformation(new Transformation(
                        itemDisplay.getTransformation().getTranslation(),
                        itemDisplay.getTransformation().getLeftRotation(),
                        new Vector3f(scale, scale, scale),
                        itemDisplay.getTransformation().getRightRotation()));

                spawnCoreParticles(world, center, rotation);
            }
        };
        task.runTaskTimer(plugin, 0L, 2L);
        coreEffects.put(mapKey, task);
    }

    private void cancelCoreEffects(String mapKey) {
        BukkitRunnable task = coreEffects.remove(mapKey);
        if (task != null) task.cancel();
    }

    private void breakCornerLights(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                Block lightBlock = world.getBlockAt(center.getBlockX() + dx, center.getBlockY() + 2, center.getBlockZ() + dz);
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
        Location visualLoc = center.clone().add(0.5, 2.0, 0.5);
        world.spawnParticle(Particle.ENCHANT, visualLoc, 120, 1.2, 1.0, 1.2, 0.2);
        world.spawnParticle(Particle.END_ROD, visualLoc, 60, 0.6, 1.0, 0.6, 0.01);
        world.playSound(center, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.4f, 0.5f);
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
    }

    private void playActivationEffects(World world, Location loc, boolean firstActivation, Player trigger) {
        if (world == null) return;
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        Sound sound = firstActivation ? Sound.BLOCK_END_PORTAL_SPAWN : Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
        float pitch = firstActivation ? 0.85f : 1.15f;
        world.playSound(center, sound, 10.0f, pitch);

        for (int ring = 0; ring <= 15; ring++) {
            if (ring == 0) continue;
            double radius = ring;
            int points = Math.max(18, ring * 12);
            Particle particle = ring < 8 ? Particle.END_ROD : Particle.FIREWORK;
            for (int i = 0; i < points; i++) {
                double angle = (i / (double) points) * Math.PI * 2;
                double x = center.getX() + Math.cos(angle + particleRandom.nextDouble() * 0.1) * radius;
                double z = center.getZ() + Math.sin(angle + particleRandom.nextDouble() * 0.1) * radius;
                double y = center.getY() + 0.8;
                world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    // 只有 registerPlacedAnchor 需要保留以备不时之需，但其实不保存数据
    public void registerPlacedAnchor(Location loc, Player player) {
        // 空实现，或提示玩家用锭激活
        if (player != null) player.sendMessage("§7放置成功。请使用 §b末影锭 §7激活祭坛。");
    }
}

打开
4. 监听器逻辑修正 (WorldAnchorListener)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/listener/WorldAnchorListener.java

修改:

激活判断: 检查是否持有 ENDER_INGOT。如果是，调用 attemptActivation。

充能判断: 检查是否持有矿物块。如果是，调用 recharge。

菜单: 如果什么都没拿，或拿的不是上述物品，打开菜单。

package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.altar.AltarService;
import cn.lingnc.aethergate.item.CustomItems;
import cn.lingnc.aethergate.teleport.TeleportMenuService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class WorldAnchorListener implements Listener {

    private final AltarService altarService;
    private final TeleportMenuService menuService;

    public WorldAnchorListener(AltarService altarService, TeleportMenuService menuService) {
        this.altarService = altarService;
        this.menuService = menuService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.LODESTONE) return;
        if (!CustomItems.isWorldAnchor(event.getItemInHand())) return;
        // 放置时不再注册到数据库，仅提示
        altarService.registerPlacedAnchor(block.getLocation(), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClickAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LODESTONE) return;
        // 注意：isAnchorBlock 现在对 Dormant 祭坛也返回 true

        Player player = event.getPlayer();
        ItemStack held = event.getItem();

        // 1. 命名牌重命名
        if (held != null && held.getType() == Material.NAME_TAG) {
            if (altarService.isAnchorBlock(block)) {
                ItemMeta meta = held.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    event.setCancelled(true);
                    if (altarService.renameWaypoint(block.getLocation(), meta.getDisplayName(), player)) {
                        consumeItem(player, held);
                    }
                } else {
                    player.sendMessage("§c使用前请先为命名牌设置名称。");
                }
            }
            return;
        }

        // 2. 尝试激活 (必须是末影锭)
        if (held != null && CustomItems.isEnderIngot(held)) {
            event.setCancelled(true);
            // 只有未激活的才能激活
            if (!altarService.isAnchorBlock(block)) {
                altarService.attemptActivation(player, block);
                consumeItem(player, held);
            } else {
                player.sendMessage("§e该祭坛已经激活过了。");
            }
            return;
        }

        // 3. 尝试充能 (矿物块)
        int addedCharges = getChargeAmount(held);
        if (addedCharges != 0) {
            event.setCancelled(true);
            if (altarService.isAnchorBlock(block)) {
                altarService.recharge(player, block, addedCharges);
                consumeItem(player, held);
            } else {
                player.sendMessage("§c该祭坛尚未激活，请先使用 §b末影锭 §c进行激活。");
            }
            return;
        }

        // 4. 打开菜单 (仅当祭坛已激活时)
        if (altarService.isAnchorBlock(block)) {
            event.setCancelled(true);
            if (!menuService.openMenu(player, block)) {
                player.sendMessage("§7请手持矿物块充能。");
            }
        }
    }

    private int getChargeAmount(ItemStack stack) {
        if (stack == null) return 0;
        return switch (stack.getType()) {
            case COPPER_BLOCK -> 3;
            case IRON_BLOCK -> 6;
            case GOLD_BLOCK -> 12;
            case LAPIS_BLOCK -> 20;
            case DIAMOND_BLOCK -> 30;
            case NETHERITE_BLOCK -> 100;
            case NETHER_STAR -> -1;
            default -> 0;
        };
    }

    private void consumeItem(Player player, ItemStack stack) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        stack.setAmount(stack.getAmount() - 1);
        if (stack.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInMainHand(stack);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnchorBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.LODESTONE) return;
        if (altarService.isAnchorBlock(block)) {
            altarService.handleAnchorBreak(block);
        }
    }
}
```

## 5. 检查清单

1.  [ ] **`SqliteStorage.java`**: 确认 SQL 查询已移除 `WHERE charges <> 0`。
2.  [ ] **`AltarService.java`**: 确认 `markDormant` 不移除记录，确认 `attemptActivation` 和 `recharge` 分离。
3.  [ ] **`PearlCostManager.java`**: 确认珍珠消耗在末影锭之前。
4.  [ ] **`WorldAnchorListener.java`**: 确认逻辑为：锭->激活，矿->充能，其他->菜单。

完成这些修改后，祭坛的生命周期和资源消耗将符合你的最新需求。

5. 修复如果站在祭坛外边也可以传送的问题