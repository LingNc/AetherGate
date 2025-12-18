package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.altar.AltarService;
import cn.lingnc.aethergate.item.CustomItems;
import cn.lingnc.aethergate.teleport.TeleportMenuService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class WorldAnchorListener implements Listener {

    private final AltarService altarService;
    private final TeleportMenuService menuService;

    public WorldAnchorListener(AltarService altarService, TeleportMenuService menuService) {
        this.altarService = altarService;
        this.menuService = menuService;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        altarService.onChunkLoad(event.getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.LODESTONE) return;
        ItemStack placedItem = event.getItemInHand();
        if (!CustomItems.isWorldAnchor(placedItem)) return;
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
        boolean activatedAnchor = altarService.isActivatedAnchor(block);

        if (held != null && CustomItems.isEnderIngot(held)) {
            event.setCancelled(true);
            if (activatedAnchor) {
                player.sendMessage("§e该祭坛已激活，可直接充能。");
            } else {
                altarService.attemptActivation(player, block);
                consumeItem(player, held);
            }
            return;
        }

        if (!activatedAnchor) {
            event.setCancelled(true);
            player.sendMessage("§c请先用末影锭激活该祭坛。");
            return;
        }

        if (held != null && held.getType() == Material.NAME_TAG) {
            ItemMeta meta = held.getItemMeta();
            if (meta != null && meta.hasCustomName()) {
                event.setCancelled(true);
                Component nameComponent = meta.customName();
                String plainName = nameComponent != null
                        ? PlainTextComponentSerializer.plainText().serialize(nameComponent)
                        : "";
                boolean renamed = altarService.renameWaypoint(block.getLocation(), plainName, player);
                if (renamed && player.getGameMode() != GameMode.CREATIVE) {
                    consumeItem(player, held);
                }
            } else {
                event.setCancelled(true);
                player.sendMessage("§c使用前请先为命名牌设置名称。");
            }
            return;
        }

        int addedCharges = getChargeAmount(held);

        if (addedCharges == 0) {
            event.setCancelled(true);
            player.sendMessage("§7请点击悬浮核心打开传送名册，或手持矿物块为祭坛充能。");
            return;
        }

        event.setCancelled(true);
        altarService.recharge(player, block, addedCharges);
        consumeItem(player, held);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCoreInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        Block anchor = altarService.getAnchorFromEntity(interaction);
        if (anchor == null) {
            return;
        }
        if (!altarService.isActivatedAnchor(anchor)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c该祭坛尚未激活，请先使用末影锭。");
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!menuService.openMenu(player, anchor)) {
            player.sendMessage("§c无法打开传送名册，请检查祭坛状态。");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnchorBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.LODESTONE) return;
        if (!altarService.isAnchorBlock(block)) {
            return;
        }
        event.setDropItems(false);
        altarService.handleAnchorBreak(block);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), CustomItems.createWorldAnchorItem());
    }

    private int getChargeAmount(ItemStack held) {
        Material material = held != null ? held.getType() : Material.AIR;
        return switch (material) {
            case COPPER_BLOCK -> 3;
            case IRON_BLOCK -> 6;
            case GOLD_BLOCK -> 12;
            case LAPIS_BLOCK -> 20;
            case DIAMOND_BLOCK -> 30;
            case NETHERITE_BLOCK -> 100;
            case NETHER_STAR -> -1;
            default -> 0;
        };
    }

    private void consumeItem(Player player, ItemStack stack) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType().isAir()) {
            return;
        }
        handItem.setAmount(handItem.getAmount() - 1);
        if (handItem.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInMainHand(handItem);
        }
    }

}
