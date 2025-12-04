package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.item.CustomItems;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class EnderPearlBlockListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // 末影珍珠块现在仅作为物品使用，不再作为独立方块，故不需要对方块破坏做特殊处理。
        if (block.getType() == Material.MUSHROOM_STEM) {
            return;
        }
    }
}
