package cn.lingnc.aethergate.altar;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AltarMaterialSet {

    private static final Map<Material, Material> BRICK_TO_STAIRS = new HashMap<>();

    private static final Set<Material> LIGHT_BLOCKS = EnumSet.of(
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
        register(Material.BRICKS, Material.BRICK_STAIRS);

        register(Material.STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        register(Material.MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICK_STAIRS);
        register(Material.CRACKED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        register(Material.CHISELED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        register(Material.INFESTED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        register(Material.INFESTED_MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICK_STAIRS);
        register(Material.INFESTED_CRACKED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        register(Material.INFESTED_CHISELED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);

        register(Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_STAIRS);
        register(Material.CRACKED_DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_STAIRS);
        register(Material.DEEPSLATE_TILES, Material.DEEPSLATE_TILE_STAIRS);
        register(Material.CRACKED_DEEPSLATE_TILES, Material.DEEPSLATE_TILE_STAIRS);

        register(Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS);
        register(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS);

        register(Material.NETHER_BRICKS, Material.NETHER_BRICK_STAIRS);
        register(Material.CRACKED_NETHER_BRICKS, Material.NETHER_BRICK_STAIRS);
        register(Material.CHISELED_NETHER_BRICKS, Material.NETHER_BRICK_STAIRS);
        register(Material.RED_NETHER_BRICKS, Material.RED_NETHER_BRICK_STAIRS);

        register(Material.END_STONE_BRICKS, Material.END_STONE_BRICK_STAIRS);

        register(Material.PRISMARINE_BRICKS, Material.PRISMARINE_BRICK_STAIRS);

        register(Material.QUARTZ_BRICKS, Material.QUARTZ_STAIRS);

        register(Material.MUD_BRICKS, Material.MUD_BRICK_STAIRS);

        try {
            register(Material.TUFF_BRICKS, Material.TUFF_BRICK_STAIRS);
            register(Material.CHISELED_TUFF_BRICKS, Material.TUFF_BRICK_STAIRS);
        } catch (NoSuchFieldError ignored) {
        }

        try {
            register(Material.RESIN_BRICKS, Material.RESIN_BRICK_STAIRS);
            register(Material.CHISELED_RESIN_BRICKS, Material.RESIN_BRICK_STAIRS);
        } catch (NoSuchFieldError ignored) {
        }
    }

    private static void register(Material base, Material stair) {
        if (base != null && stair != null) {
            BRICK_TO_STAIRS.put(base, stair);
        }
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
}
