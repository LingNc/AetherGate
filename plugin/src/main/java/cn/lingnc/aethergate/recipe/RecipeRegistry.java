package cn.lingnc.aethergate.recipe;

import cn.lingnc.aethergate.AetherGatePlugin;
import cn.lingnc.aethergate.item.CustomItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

public final class RecipeRegistry {

    private RecipeRegistry() {
    }

    public static void registerAll(AetherGatePlugin plugin) {
        registerEnderIngotRecipes(plugin);
        registerWorldAnchorRecipe(plugin);
    }

    private static void registerEnderIngotRecipes(AetherGatePlugin plugin) {
        NamespacedKey craftKey = new NamespacedKey(plugin, "ender_ingot_craft");
        ShapedRecipe craft = new ShapedRecipe(craftKey, CustomItems.createEnderIngotItem(1));
        craft.shape("PPP", "PPP", "PPP");
        craft.setIngredient('P', Material.ENDER_PEARL);
        Bukkit.addRecipe(craft);

        NamespacedKey uncraftKey = new NamespacedKey(plugin, "ender_ingot_uncraft");
        ShapelessRecipe uncraft = new ShapelessRecipe(uncraftKey, new ItemStack(Material.ENDER_PEARL, 9));
        uncraft.addIngredient(new RecipeChoice.ExactChoice(CustomItems.createEnderIngotItem(1)));
        Bukkit.addRecipe(uncraft);
    }

    private static void registerWorldAnchorRecipe(AetherGatePlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "world_anchor");
        ShapedRecipe recipe = new ShapedRecipe(key, CustomItems.createWorldAnchorItem());
        recipe.shape("DID", "LBL", "DOD");
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('I', new RecipeChoice.ExactChoice(CustomItems.createEnderIngotItem(1)));
        recipe.setIngredient('L', Material.LAPIS_BLOCK);
        recipe.setIngredient('B', Material.LODESTONE);
        recipe.setIngredient('O', Material.CRYING_OBSIDIAN);
        Bukkit.addRecipe(recipe);
    }

    public static void clearPluginRecipes(AetherGatePlugin plugin) {
        // Optional: could be used on reload to clean up; left minimal for now.
        for (Recipe recipe : Bukkit.getServer().getRecipesFor(CustomItems.createWorldAnchorItem())) {
            // Bukkit API doesn't expose direct removal by object in all versions; skipping for simplicity.
        }
    }
}
