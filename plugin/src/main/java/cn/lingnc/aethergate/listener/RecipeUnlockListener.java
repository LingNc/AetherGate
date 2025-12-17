package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.AetherGatePlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class RecipeUnlockListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        AetherGatePlugin plugin = AetherGatePlugin.getInstance();
        NamespacedKey anchorKey = new NamespacedKey(plugin, "world_anchor");
        NamespacedKey ingotKey = new NamespacedKey(plugin, "ender_ingot_craft");
        NamespacedKey uncraftKey = new NamespacedKey(plugin, "ender_ingot_uncraft");
        player.discoverRecipe(anchorKey);
        player.discoverRecipe(ingotKey);
        player.discoverRecipe(uncraftKey);
    }
}
