package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.altar.AltarService;
import cn.lingnc.aethergate.item.CustomItems;
import cn.lingnc.aethergate.teleport.TeleportMenuService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class WorldAnchorListener implements Listener {

    private final AltarService altarService;
    private final TeleportMenuService menuService;

    public WorldAnchorListener(AltarService altarService, TeleportMenuService menuService) {
        this.altarService = altarService;
        this.menuService = menuService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.LODESTONE) return;
        if (!CustomItems.isWorldAnchor(event.getItemInHand())) return;
        altarService.registerPlacedAnchor(block.getLocation(), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceAttempt(PlayerInteractEvent event) {
        // 目前不需要在交互阶段做任何特殊处理
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClickAnchor(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LODESTONE) return;
        if (!altarService.isAnchorBlock(block)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack held = event.getItem();

        if (held != null && held.getType() == Material.NAME_TAG) {
            ItemMeta meta = held.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                event.setCancelled(true);
                boolean renamed = altarService.renameWaypoint(block.getLocation(), meta.getDisplayName(), player);
                if (renamed && player.getGameMode() != GameMode.CREATIVE) {
                    held.setAmount(held.getAmount() - 1);
                    if (held.getAmount() <= 0) {
                        player.getInventory().setItemInMainHand(null);
                    } else {
                        player.getInventory().setItemInMainHand(held);
                    }
                }
            } else {
                event.setCancelled(true);
                player.sendMessage("§c使用前请先为命名牌设置名称。");
            }
            return;
        }

        int addedCharges = switch (held != null ? held.getType() : Material.AIR) {
            case COPPER_BLOCK -> 3;
            case IRON_BLOCK -> 6;
            case GOLD_BLOCK -> 12;
            case LAPIS_BLOCK -> 20;
            case DIAMOND_BLOCK -> 30;
            case NETHERITE_BLOCK -> 100;
            case NETHER_STAR -> -1;
            default -> 0;
        };

        if (addedCharges == 0) {
            event.setCancelled(true);
            if (!menuService.openMenu(player, block)) {
                player.sendMessage("§7请手持矿物块充能，或在有目标时使用空手右键打开目的地名册。");
            }
            return;
        }

        event.setCancelled(true);
        altarService.activateOrBackfire(player, block, addedCharges);
        if (held != null && player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
            if (held.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInMainHand(held);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnchorBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.LODESTONE) return;
        if (!altarService.isAnchorBlock(block)) {
            return;
        }
        altarService.handleAnchorBreak(block);
    }
}
