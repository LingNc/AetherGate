package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.item.CustomItems;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public class CraftingProtectionListener implements Listener {

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            // Unknown recipe but contains special ingots: block just in case.
            if (containsEnderIngot(matrix)) {
                event.getInventory().setResult(null);
            }
            return;
        }

        ItemStack result = recipe.getResult();
        boolean isPluginRecipe = CustomItems.isWorldAnchor(result)
                || (result.getType() == Material.ENDER_PEARL && result.getAmount() == 9);

        boolean containsEnderIngot = containsEnderIngot(matrix);
        if (containsEnderIngot && !isPluginRecipe) {
            event.getInventory().setResult(null);
            return;
        }

        // Prevent crafting the world anchor with ordinary iron ingots.
        if (CustomItems.isWorldAnchor(result)) {
            ItemStack ingotSlot = matrix != null && matrix.length > 1 ? matrix[1] : null;
            if (ingotSlot == null || !CustomItems.isEnderIngot(ingotSlot)) {
                event.getInventory().setResult(null);
            }
        }
    }

    private boolean containsEnderIngot(ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (ItemStack item : matrix) {
            if (item != null && CustomItems.isEnderIngot(item)) {
                return true;
            }
        }
        return false;
    }
}
