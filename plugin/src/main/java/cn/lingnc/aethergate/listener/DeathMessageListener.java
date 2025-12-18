package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.AetherGatePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

public class DeathMessageListener implements Listener {

    private static final String SACRIFICE_META = "aethergate_sacrificed";

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!player.hasMetadata(SACRIFICE_META)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long expiresAt = readExpiry(player.getMetadata(SACRIFICE_META));
        player.removeMetadata(SACRIFICE_META, AetherGatePlugin.getInstance());
        if (expiresAt == null || expiresAt < now) {
            return;
        }

        EntityDamageEvent lastDamage = player.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = lastDamage != null ? lastDamage.getCause() : null;

        String successTemplate = AetherGatePlugin.getInstance().getPluginConfig().getSacrificeSuccessMessage();
        String failPrefix = AetherGatePlugin.getInstance().getPluginConfig().getSacrificeFailPrefix();
        if (successTemplate == null || successTemplate.isBlank()) {
            successTemplate = "%player% 被献祭了";
        }
        if (failPrefix == null) {
            failPrefix = "%player% 在献祭仪式中 ";
        }

        boolean isExplosion = cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;

        Component finalMessage;
        if (isExplosion) {
            String text = successTemplate.replace("%player%", player.getName());
            finalMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        } else {
            Component original = event.deathMessage();
            String prefixText = failPrefix.replace("%player%", player.getName());
            Component prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(prefixText + " ");
            if (original == null) {
                original = Component.text("意外死亡", NamedTextColor.GRAY);
            }
            finalMessage = prefix.append(original);
        }

        event.deathMessage(finalMessage);
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
