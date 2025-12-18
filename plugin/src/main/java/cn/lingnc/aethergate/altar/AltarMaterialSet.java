package cn.lingnc.aethergate.altar;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class AltarMaterialSet {

    private static final Map<Material, Material> BRICK_TO_STAIRS = new HashMap<>();
    private static final Map<Material, Material> DEFAULT_BRICK_TO_STAIRS = new HashMap<>();
    private static final Set<Material> LIGHT_BLOCKS = EnumSet.noneOf(Material.class);
    private static final Set<Material> DEFAULT_LIGHTS = EnumSet.of(
            Material.LANTERN,
            Material.SOUL_LANTERN,
            Material.GLOWSTONE,
            Material.SEA_LANTERN,
            Material.PEARLESCENT_FROGLIGHT,
            Material.VERDANT_FROGLIGHT,
            Material.OCHRE_FROGLIGHT,
            Material.SHROOMLIGHT,
            Material.END_ROD
    );

    static {
        registerDefault(Material.BRICKS, Material.BRICK_STAIRS);

        registerDefault(Material.STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        registerDefault(Material.MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICK_STAIRS);
        registerDefault(Material.CRACKED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        registerDefault(Material.CHISELED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        registerDefault(Material.INFESTED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        registerDefault(Material.INFESTED_MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICK_STAIRS);
        registerDefault(Material.INFESTED_CRACKED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        registerDefault(Material.INFESTED_CHISELED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);

        registerDefault(Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_STAIRS);
        registerDefault(Material.CRACKED_DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_STAIRS);
        registerDefault(Material.DEEPSLATE_TILES, Material.DEEPSLATE_TILE_STAIRS);
        registerDefault(Material.CRACKED_DEEPSLATE_TILES, Material.DEEPSLATE_TILE_STAIRS);

        registerDefault(Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS);
        registerDefault(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS);

        registerDefault(Material.NETHER_BRICKS, Material.NETHER_BRICK_STAIRS);
        registerDefault(Material.CRACKED_NETHER_BRICKS, Material.NETHER_BRICK_STAIRS);
        registerDefault(Material.CHISELED_NETHER_BRICKS, Material.NETHER_BRICK_STAIRS);
        registerDefault(Material.RED_NETHER_BRICKS, Material.RED_NETHER_BRICK_STAIRS);

        registerDefault(Material.END_STONE_BRICKS, Material.END_STONE_BRICK_STAIRS);

        registerDefault(Material.PRISMARINE_BRICKS, Material.PRISMARINE_BRICK_STAIRS);

        registerDefault(Material.QUARTZ_BRICKS, Material.QUARTZ_STAIRS);

        registerDefault(Material.MUD_BRICKS, Material.MUD_BRICK_STAIRS);

        try {
            registerDefault(Material.TUFF_BRICKS, Material.TUFF_BRICK_STAIRS);
            registerDefault(Material.CHISELED_TUFF_BRICKS, Material.TUFF_BRICK_STAIRS);
        } catch (NoSuchFieldError ignored) {
        }

        try {
            registerDefault(Material.RESIN_BRICKS, Material.RESIN_BRICK_STAIRS);
            registerDefault(Material.CHISELED_RESIN_BRICKS, Material.RESIN_BRICK_STAIRS);
        } catch (NoSuchFieldError ignored) {
        }

        resetToDefaults();
    }

    private static void registerDefault(Material base, Material stair) {
        if (base != null && stair != null) {
            DEFAULT_BRICK_TO_STAIRS.put(base, stair);
        }
    }

    private static void resetToDefaults() {
        BRICK_TO_STAIRS.clear();
        BRICK_TO_STAIRS.putAll(DEFAULT_BRICK_TO_STAIRS);
        LIGHT_BLOCKS.clear();
        LIGHT_BLOCKS.addAll(DEFAULT_LIGHTS);
    }

    private AltarMaterialSet() {
    }

    public static boolean isBaseBrick(Material type) {
        return BRICK_TO_STAIRS.containsKey(type);
    }

    public static Material getStair(Material baseType) {
        return BRICK_TO_STAIRS.get(baseType);
    }

    public static boolean isLight(Material type) {
        return LIGHT_BLOCKS.contains(type);
    }

    public static void reloadFromConfig(FileConfiguration config, Logger logger) {
        resetToDefaults();
        if (config == null) {
            return;
        }

        var lights = config.getStringList("altar-materials.lights");
        if (lights != null && !lights.isEmpty()) {
            LIGHT_BLOCKS.clear();
            for (String entry : lights) {
                Material light = Material.matchMaterial(entry);
                if (light == null) {
                    if (logger != null) {
                        logger.warning("未知光源方块名称: " + entry + "，已跳过。");
                    }
                    continue;
                }
                LIGHT_BLOCKS.add(light);
            }
        }
        if (LIGHT_BLOCKS.isEmpty()) {
            LIGHT_BLOCKS.addAll(DEFAULT_LIGHTS);
        }

        ConfigurationSection blocks = config.getConfigurationSection("altar-materials.blocks");
        if (blocks != null) {
            BRICK_TO_STAIRS.clear();
            for (String key : blocks.getKeys(false)) {
                Material base = Material.matchMaterial(key);
                String stairName = blocks.getString(key);
                Material stair = Material.matchMaterial(stairName == null ? "" : stairName);
                if (base == null || stair == null) {
                    if (logger != null) {
                        logger.warning("无效的砖块/楼梯配置: " + key + " -> " + stairName);
                    }
                    continue;
                }
                BRICK_TO_STAIRS.put(base, stair);
            }
        }
        if (BRICK_TO_STAIRS.isEmpty()) {
            BRICK_TO_STAIRS.putAll(DEFAULT_BRICK_TO_STAIRS);
        }
    }
}
