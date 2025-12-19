package cn.lingnc.aethergate.teleport;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public class TeleportListener implements Listener {

    private final TeleportService teleportService;

    public TeleportListener(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        teleportService.handleMove(event);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (teleportService.isPlayerLocked(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!teleportService.isPlayerLocked(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§c传送过程中无法打开背包。");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!teleportService.isPlayerLocked(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!teleportService.isPlayerLocked(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!teleportService.isPlayerLocked(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!teleportService.isPlayerLocked(uuid)) {
            return;
        }
        if (!teleportService.isInternalTeleport(uuid)) {
            event.setCancelled(true);
            player.sendMessage("§c传送仪式进行中，无法被其他传送影响。");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teleportService.handlePlayerQuit(event.getPlayer().getUniqueId());
    }
}
