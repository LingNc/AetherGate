package cn.lingnc.aethergate.altar;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;

import java.util.ArrayList;
import java.util.List;

public final class AltarStructureChecker {

    private AltarStructureChecker() {
    }

    public static boolean isValidAltar(Block lodestoneBlock) {
        return validate(lodestoneBlock).isValid();
    }

    public static AltarValidationResult validate(Block lodestoneBlock) {
        List<String> errors = new ArrayList<>();
        if (lodestoneBlock == null) {
            errors.add("未找到世界锚点方块");
            return AltarValidationResult.failure(errors);
        }
        if (lodestoneBlock.getType() != Material.LODESTONE) {
            errors.add("核心方块必须是世界锚点 (LODESTONE)");
            return AltarValidationResult.failure(errors);
        }

        Location origin = lodestoneBlock.getLocation();

        Block centerBelow2 = origin.clone().add(0, -2, 0).getBlock();
        Material baseType = centerBelow2.getType();
        if (!AltarMaterialSet.isBaseBrick(baseType)) {
            errors.add(String.format("y-2 的中心方块类型应为许可砖块，当前为 %s", baseType));
            return AltarValidationResult.failure(errors);
        }
        // Base layer y-2: 5x5 of same brick type A
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block b = origin.clone().add(dx, -2, dz).getBlock();
                if (b.getType() != baseType) {
                    errors.add(String.format("y-2 坐标 (%d,%d,%d) 应为 %s，当前为 %s",
                            b.getX(), b.getY(), b.getZ(), baseType, b.getType()));
                }
            }
        }

        // Container layer y-1 center barrel
        Block barrelBlock = origin.clone().add(0, -1, 0).getBlock();
        if (barrelBlock.getType() != Material.BARREL) {
            errors.add(String.format("核心下方 (y-1) 应为 BARREL，当前为 %s", barrelBlock.getType()));
        }

        Material stairType = getStairForBase(baseType);
        if (stairType == Material.AIR) {
            errors.add("未找到与所选底座砖匹配的楼梯类型。");
        } else {
            checkStair(origin, -1, -2, stairType, BlockFace.EAST, errors);
            checkStair(origin, 1, -2, stairType, BlockFace.WEST, errors);
            checkStair(origin, -2, -1, stairType, BlockFace.SOUTH, errors);
            checkStair(origin, 2, -1, stairType, BlockFace.SOUTH, errors);
            checkStair(origin, -2, 1, stairType, BlockFace.NORTH, errors);
            checkStair(origin, 2, 1, stairType, BlockFace.NORTH, errors);
            checkStair(origin, -1, 2, stairType, BlockFace.EAST, errors);
            checkStair(origin, 1, 2, stairType, BlockFace.WEST, errors);
        }

        // Corners at layer -1 must be base bricks (support pillars)
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                Block pillarBase = origin.clone().add(dx, -1, dz).getBlock();
                if (pillarBase.getType() != baseType) {
                    errors.add(String.format("y-1 角落 (%d,%d,%d) 应为 %s，当前为 %s",
                            pillarBase.getX(), pillarBase.getY(), pillarBase.getZ(), baseType, pillarBase.getType()));
                }
            }
        }

        // Pillar tops with lights at y+2 corners
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                Block lightBlock = origin.clone().add(dx, 2, dz).getBlock();
                Material type = lightBlock.getType();
                if (!AltarMaterialSet.isLight(type)) {
                    errors.add(String.format("y+2 角落 (%d,%d,%d) 需要放置光源方块，当前为 %s",
                            lightBlock.getX(), lightBlock.getY(), lightBlock.getZ(), type));
                }
            }
        }

        if (errors.isEmpty()) {
            return AltarValidationResult.success();
        }
        return AltarValidationResult.failure(errors);
    }

    private static void checkStair(Location origin, int dx, int dz, Material expectedType, BlockFace facing,
                                   List<String> errors) {
        Block block = origin.clone().add(dx, -1, dz).getBlock();
        if (block.getType() != expectedType) {
            errors.add(String.format("y-1 坐标 (%d,%d,%d) 应为 %s，当前为 %s",
                    block.getX(), block.getY(), block.getZ(), expectedType, block.getType()));
            return;
        }
        if (!(block.getBlockData() instanceof Directional directional)) {
            errors.add(String.format("y-1 坐标 (%d,%d,%d) 楼梯缺少朝向数据",
                    block.getX(), block.getY(), block.getZ()));
            return;
        }
        BlockFace expectedFacing = invertHorizontal(facing);
        if (directional.getFacing() != expectedFacing) {
            errors.add(String.format("y-1 坐标 (%d,%d,%d) 楼梯朝向应为 %s，当前为 %s",
                    block.getX(), block.getY(), block.getZ(), expectedFacing, directional.getFacing()));
        }
    }

    private static BlockFace invertHorizontal(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
            default -> face;
        };
    }

    private static Material getStairForBase(Material base) {
        return switch (base) {
            case QUARTZ_BRICKS -> Material.QUARTZ_STAIRS;
            case POLISHED_BLACKSTONE_BRICKS -> Material.POLISHED_BLACKSTONE_BRICK_STAIRS;
            case NETHER_BRICKS -> Material.NETHER_BRICK_STAIRS;
            case RED_NETHER_BRICKS -> Material.RED_NETHER_BRICK_STAIRS;
            case DEEPSLATE_BRICKS -> Material.DEEPSLATE_BRICK_STAIRS;
            case DEEPSLATE_TILES -> Material.DEEPSLATE_TILE_STAIRS;
            default -> Material.AIR;
        };
    }
}
