为了支持所有类型的砖块及其变种（包括 1.21 新增的凝灰岩和树脂砖块），我们需要重构 `AltarMaterialSet` 来建立底座方块与楼梯的映射关系，并简化 `AltarStructureChecker` 的校验逻辑。

这里是修改方案：

### 1\. 修改 `AltarMaterialSet.java`

我们需要将原本的 `Set` 改为一个 `Map`，用来存储 **[底座方块] -\> [对应的楼梯方块]** 的关系。这样不仅定义了哪些方块是合法的底座，还直接定义了它们应该匹配什么楼梯。

请将 `plugin/src/main/java/cn/lingnc/aethergate/altar/AltarMaterialSet.java` 的内容替换为：

```java
package cn.lingnc.aethergate.altar;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AltarMaterialSet {

    // 存储 底座方块 -> 对应楼梯 的映射
    private static final Map<Material, Material> BRICK_TO_STAIRS = new HashMap<>();

    // 光源方块保持不变
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

    static {
        // --- 砖块 (Bricks) ---
        register(Material.BRICKS, Material.BRICK_STAIRS);

        // --- 石砖系列 (Stone Bricks) ---
        // 原版、苔石、裂纹、雕纹 统一对应 石砖楼梯 (苔石有自己的楼梯，这里你可以选择统一或者分开，通常建议分开匹配)
        register(Material.STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        register(Material.MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICK_STAIRS);
        register(Material.CRACKED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        register(Material.CHISELED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        // 虫蚀版本 (Infested) - 虽然通常不建议用于建筑，但既然你要求支持
        register(Material.INFESTED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        register(Material.INFESTED_MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICK_STAIRS);
        register(Material.INFESTED_CRACKED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);
        register(Material.INFESTED_CHISELED_STONE_BRICKS, Material.STONE_BRICK_STAIRS);

        // --- 深板岩系列 (Deepslate) ---
        register(Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_STAIRS);
        register(Material.CRACKED_DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_STAIRS);
        register(Material.DEEPSLATE_TILES, Material.DEEPSLATE_TILE_STAIRS);
        register(Material.CRACKED_DEEPSLATE_TILES, Material.DEEPSLATE_TILE_STAIRS);

        // --- 黑石系列 (Blackstone) ---
        register(Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS);
        register(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS);

        // --- 下界砖系列 (Nether Bricks) ---
        register(Material.NETHER_BRICKS, Material.NETHER_BRICK_STAIRS);
        register(Material.CRACKED_NETHER_BRICKS, Material.NETHER_BRICK_STAIRS);
        register(Material.CHISELED_NETHER_BRICKS, Material.NETHER_BRICK_STAIRS);
        register(Material.RED_NETHER_BRICKS, Material.RED_NETHER_BRICK_STAIRS);

        // --- 末地石砖 (End Stone) ---
        register(Material.END_STONE_BRICKS, Material.END_STONE_BRICK_STAIRS);

        // --- 海晶石砖 (Prismarine) ---
        register(Material.PRISMARINE_BRICKS, Material.PRISMARINE_BRICK_STAIRS);

        // --- 石英砖 (Quartz) ---
        // 石英砖没有专门的"石英砖楼梯"，通常搭配普通石英楼梯
        register(Material.QUARTZ_BRICKS, Material.QUARTZ_STAIRS);

        // --- 泥砖 (Mud) ---
        register(Material.MUD_BRICKS, Material.MUD_BRICK_STAIRS);

        // --- 1.21 新增: 凝灰岩 (Tuff) ---
        // 确保服务器版本支持，否则会报错 (Paper 1.21.4 已支持)
        try {
            register(Material.TUFF_BRICKS, Material.TUFF_BRICK_STAIRS);
            register(Material.CHISELED_TUFF_BRICKS, Material.TUFF_BRICK_STAIRS);
        } catch (NoSuchFieldError ignored) {} // 兼容低版本

        // --- 1.21 新增: 树脂 (Resin) ---
        try {
            register(Material.RESIN_BRICKS, Material.RESIN_BRICK_STAIRS);
            register(Material.CHISELED_RESIN_BRICKS, Material.RESIN_BRICK_STAIRS);
        } catch (NoSuchFieldError ignored) {} // 兼容低版本
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
```

-----

### 2\. 修改 `AltarStructureChecker.java`

现在我们可以移除 `AltarStructureChecker` 中那个巨大的 `switch` 语句，直接调用 `AltarMaterialSet` 的方法。

修改 `plugin/src/main/java/cn/lingnc/aethergate/altar/AltarStructureChecker.java` 中的 `validate` 方法和 `getStairForBase` 方法：

```java
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

        // 1. 检查 y-2 中心是否为定义的底座砖块
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

        // 2. 直接从 AltarMaterialSet 获取对应的楼梯类型
        Material stairType = AltarMaterialSet.getStair(baseType);

        if (stairType == null || stairType == Material.AIR) {
            errors.add("未找到与所选底座砖 (" + baseType + ") 匹配的楼梯类型。");
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

    // 移除了 getStairForBase 方法，因为它现在由 AltarMaterialSet 统一管理
}
```

### 修改说明：

1.  **全面覆盖**：添加了红砖、泥砖、海晶石砖、末地石砖以及 1.21 新增的凝灰岩和树脂砖。
2.  **变种支持**：通过 Map 映射，现在底座可以使用变种方块（如裂纹石砖、雕纹凝灰岩砖），代码会自动识别它们应该搭配的标准楼梯类型。
3.  **代码解耦**：结构检查器 (`StructureChecker`) 不再需要关心哪种砖配哪种楼梯，只需向 `MaterialSet` 查询即可，方便未来添加更多方块。