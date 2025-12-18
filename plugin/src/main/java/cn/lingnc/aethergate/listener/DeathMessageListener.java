package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.AetherGatePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.entity.Player;

import java.util.List;

public class DeathMessageListener implements Listener {

    private static final String SACRIFICE_META = "aethergate_sacrificed";
    private static final long MAX_AGE_MS = 3000L;

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DamageCause cause = player.getLastDamageCause() != null ? player.getLastDamageCause().getCause() : null;
        if (cause != DamageCause.BLOCK_EXPLOSION && cause != DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!player.hasMetadata(SACRIFICE_META)) {
            return;
        }
        Long expiresAt = readExpiry(player.getMetadata(SACRIFICE_META));
        player.removeMetadata(SACRIFICE_META, AetherGatePlugin.getInstance());
        if (expiresAt == null || expiresAt < now) {
            return;
        }
        String template = AetherGatePlugin.getInstance().getPluginConfig().getSacrificeDeathMessage();
        if (template == null || template.isBlank()) {
            template = "%player% 被献祭了";
        }
        String message = template.replace("%player%", player.getName());
        event.setDeathMessage(message);
    }

    private Long readExpiry(List<MetadataValue> values) {
        for (MetadataValue value : values) {
            if (value == null) continue;
            try {
                return value.asLong();
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
