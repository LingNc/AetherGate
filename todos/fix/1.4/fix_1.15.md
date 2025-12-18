这是一个针对 AetherGate v1.15 的修复说明书。

问题确认与解决方案：

无法使用石砖 (Material Issue): AltarMaterialSet 确实漏掉了原版的 STONE_BRICKS 系列。

PDC 身份混淆 (Identity Issue): 这是最严重的问题。目前的逻辑只检查了“是否符合5x5结构”，而没有检查“核心方块是否是特定的世界锚点物品”。导致玩家摆放普通磁石也能激活。

修复方案: 利用 TileState (磁石是 TileEntity) 的 PDC。在 放置时 将物品的 NBT 标签拷贝到方块上；在 激活时 检查方块是否有该标签。只有带有特定标签的磁石才能被激活。

交互高度: 再次确认并微调范围判定逻辑，确保上下 2 格（共 5 格高）的体验。

请将以下文档交付给开发人员。

AetherGate 核心修复说明书 (v1.15)
优先级: 紧急 (Bug Fix) 目标版本: Paper 1.21.4

1. 材质库扩充 (AltarMaterialSet.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarMaterialSet.java

修改: 添加石砖系列 (Stone Bricks) 及其变种。
package cn.lingnc.aethergate.altar;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

public final class AltarMaterialSet {

    private static final Set<Material> BASE_BRICKS = EnumSet.of(
            // 原有
            Material.QUARTZ_BRICKS,
            Material.POLISHED_BLACKSTONE_BRICKS,
            Material.NETHER_BRICKS,
            Material.RED_NETHER_BRICKS,
            Material.DEEPSLATE_BRICKS,
            Material.DEEPSLATE_TILES,
            // 新增：石砖系列
            Material.STONE_BRICKS,
            Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS,
            Material.CHISELED_STONE_BRICKS,
            // 补充：深板岩系列变种
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
            // 补充：红石灯 (点亮状态很难维持检测，建议只允许常亮光源)
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
2. 物品类公开 Key (CustomItems.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/item/CustomItems.java

修改: 将 KEY_TYPE 修改为 public，以便监听器在放置方块时可以引用它来写入数据。
package cn.lingnc.aethergate.item;

import cn.lingnc.aethergate.AetherGatePlugin;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class CustomItems {

    // 修改：改为 public，供外部使用
    public static final NamespacedKey KEY_TYPE = new NamespacedKey(AetherGatePlugin.getInstance(), "item_type");

    public static final String TYPE_ENDER_INGOT = "ender_ingot";
    public static final String TYPE_WORLD_ANCHOR = "world_anchor";

    private CustomItems() {
    }

    // ... (createEnderIngotItem, createWorldAnchorItem 等方法保持不变) ...
    // ... (isEnderIngot, isWorldAnchor 等方法保持不变) ...

    // 为了完整性，保留核心方法，开发人员只需修改 KEY_TYPE 的修饰符
    public static ItemStack createEnderIngotItem(int amount) {
        ItemStack stack = new ItemStack(Material.IRON_INGOT, amount);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addString(TYPE_ENDER_INGOT).build());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("末影锭", NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_TYPE, PersistentDataType.STRING, TYPE_ENDER_INGOT);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack createWorldAnchorItem() {
        ItemStack stack = new ItemStack(Material.LODESTONE, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("世界锚点", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_TYPE, PersistentDataType.STRING, TYPE_WORLD_ANCHOR);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean isEnderIngot(ItemStack stack) {
        if (stack == null || stack.getType() != Material.IRON_INGOT) return false;
        if (stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            CustomModelData data = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (data != null && data.strings().contains(TYPE_ENDER_INGOT)) return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        String type = meta.getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        return TYPE_ENDER_INGOT.equals(type);
    }

    public static boolean isWorldAnchor(ItemStack stack) {
        if (stack == null || stack.getType() != Material.LODESTONE) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        String type = meta.getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        return TYPE_WORLD_ANCHOR.equals(type);
    }
}

打开

3. 方块 PDC 读写逻辑 (WorldAnchorListener.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/listener/WorldAnchorListener.java

修改重点:

onPlace: 放置时，将 Item 的 PDC 数据拷贝到 Block 的 TileState 中。

onRightClickAnchor: 激活前，检查方块是否有特定的 PDC 标记。如果没有，视为普通磁石，拒绝激活。

4. 祭坛服务微调 (AltarService.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java

修改: 确认高度判定逻辑。

Java

    // 确认 findNearestActiveAnchor 逻辑
    // ...
            // 垂直判定：
            // 核心位置 y=CoreY (Block Y)
            // Interaction Radius = R (e.g., 2)
            // 有效范围应该是 [CoreY - R, CoreY + R]
            // 加 1.5 是为了涵盖玩家的 eye location 和 feet location 的偏差
            if (Math.abs(playerLoc.getY() - coreY) > radius + 1.5) continue;
    // ...

    // 确认 isWithinInteractionRange 逻辑
    private boolean isWithinInteractionRange(Location origin, Block anchorBlock, int radius) {
        // ...
        // 垂直距离判定
        int dy = Math.abs(origin.getBlockY() - anchorLoc.getBlockY());
        return dy <= radius + 1; // 允许稍微宽松一点 (+1) 以适应跳跃或半砖高差
    }

**开发人员提示:**
请直接使用上述 **`WorldAnchorListener.java`** 的完整代码替换原有文件。这是解决“普通磁石也能激活”问题的关键。
同时，请更新 **`AltarMaterialSet.java`** 以支持石砖。
**注意:** 旧的（已经在世界中放置的）世界锚点方块因为没有 PDC 数据，更新插件后将**无法被激活或破坏掉落正确物品**。你需要挖掉它们（会掉落普通磁石），然后用指令 `/charm give_block anchor` 获取新的带 NBT 的锚点重新放置。已激活的祭坛不受影响（数据在数据库中）。