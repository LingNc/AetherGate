package cn.lingnc.aethergate.altar;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

public final class AltarMaterialSet {

        private static final Set<Material> BASE_BRICKS = EnumSet.of(
            Material.QUARTZ_BRICKS,
            Material.POLISHED_BLACKSTONE_BRICKS,
            Material.NETHER_BRICKS,
            Material.RED_NETHER_BRICKS,
            Material.DEEPSLATE_BRICKS,
            Material.DEEPSLATE_TILES,
            Material.STONE_BRICKS,
            Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS,
            Material.CHISELED_STONE_BRICKS,
            Material.CRACKED_DEEPSLATE_BRICKS,
            Material.CRACKED_DEEPSLATE_TILES
        );

        private static final Set<Material> LIGHT_BLOCKS = EnumSet.of(
            Material.LANTERN,
            Material.SOUL_LANTERN,
            Material.GLOWSTONE,
            Material.SEA_LANTERN,
            Material.PEARLESCENT_FROGLIGHT,
            Material.VERDANT_FROGLIGHT,
            Material.OCHRE_FROGLIGHT,
            Material.SHROOMLIGHT
    );

    private AltarMaterialSet() {
    }

    public static boolean isBaseBrick(Material type) {
        return BASE_BRICKS.contains(type);
    }

    public static boolean isLight(Material type) {
        return LIGHT_BLOCKS.contains(type);
    }
}
