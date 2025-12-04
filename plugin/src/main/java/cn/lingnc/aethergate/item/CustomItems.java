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

    private static final NamespacedKey KEY_TYPE = new NamespacedKey(AetherGatePlugin.getInstance(), "item_type");

    public static final String TYPE_ENDER_INGOT = "ender_ingot";
    public static final String TYPE_WORLD_ANCHOR = "world_anchor";

    private CustomItems() {
    }

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
        if (stack == null || stack.getType() != Material.IRON_INGOT) {
            return false;
        }
        if (stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            CustomModelData data = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (data != null && data.strings().contains(TYPE_ENDER_INGOT)) {
                return true;
            }
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        String type = meta.getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        return TYPE_ENDER_INGOT.equals(type);
    }

    public static boolean isWorldAnchor(ItemStack stack) {
        if (stack == null || stack.getType() != Material.LODESTONE) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        String type = meta.getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        return TYPE_WORLD_ANCHOR.equals(type);
    }
}
