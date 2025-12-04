package cn.lingnc.aethergate.teleport;

import cn.lingnc.aethergate.item.CustomItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PearlCostManager {

    private static final int SCAN_RADIUS = 2;

    public boolean tryConsumePearl(Location anchorLoc, Player player) {
        Inventory coreBarrel = getCoreBarrel(anchorLoc);
        if (coreBarrel != null && consumeIngot(coreBarrel, anchorLoc, true)) {
            return true;
        }
        if (coreBarrel != null && consumePearl(coreBarrel)) {
            return true;
        }

        List<ContainerRef> barrels = new ArrayList<>();
        List<ContainerRef> others = new ArrayList<>();
        collectContainers(anchorLoc, barrels, others);

        for (ContainerRef ref : barrels) {
            if (ref.isCore()) continue;
            if (consumeIngot(ref.getInventory(), ref.getDropLocation(), true)) {
                return true;
            }
            if (consumePearl(ref.getInventory())) {
                return true;
            }
        }
        for (ContainerRef ref : others) {
            if (consumeIngot(ref.getInventory(), ref.getDropLocation(), true)) {
                return true;
            }
            if (consumePearl(ref.getInventory())) {
                return true;
            }
        }

        Inventory playerInv = player.getInventory();
        if (consumeIngot(playerInv, player.getLocation(), true)) {
            return true;
        }
        if (consumePearl(playerInv)) {
            return true;
        }
        return false;
    }

    public boolean hasAvailableFuel(Location anchorLoc, Player player) {
        if (anchorLoc == null || player == null) {
            return false;
        }
        Inventory coreBarrel = getCoreBarrel(anchorLoc);
        if (hasPearl(coreBarrel) || hasIngot(coreBarrel)) {
            return true;
        }

        List<ContainerRef> barrels = new ArrayList<>();
        List<ContainerRef> others = new ArrayList<>();
        collectContainers(anchorLoc, barrels, others);

        for (ContainerRef ref : barrels) {
            if (ref.isCore()) continue;
            if (hasPearl(ref.getInventory())) {
                return true;
            }
        }
        for (ContainerRef ref : barrels) {
            if (ref.isCore()) continue;
            if (hasIngot(ref.getInventory())) {
                return true;
            }
        }
        for (ContainerRef ref : others) {
            if (hasPearl(ref.getInventory())) {
                return true;
            }
        }
        for (ContainerRef ref : others) {
            if (hasIngot(ref.getInventory())) {
                return true;
            }
        }
        Inventory playerInv = player.getInventory();
        if (hasPearl(playerInv) || hasIngot(playerInv)) {
            return true;
        }
        return false;
    }

    private Inventory getCoreBarrel(Location anchorLoc) {
        Block block = anchorLoc.clone().add(0, -1, 0).getBlock();
        BlockState state = block.getState();
        if (state instanceof Barrel barrel) {
            return barrel.getInventory();
        }
        return null;
    }

    private void collectContainers(Location anchor, List<ContainerRef> barrels, List<ContainerRef> others) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    Block block = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                    BlockState state = block.getState();
                    if (!(state instanceof InventoryHolder holder)) {
                        continue;
                    }
                    Inventory inventory = holder.getInventory();
                    Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
                    boolean isCore = (dx == 0 && dy == -1 && dz == 0 && state instanceof Barrel);
                    if (state instanceof Barrel) {
                        barrels.add(new ContainerRef(inventory, dropLoc, true, isCore));
                    } else if (state instanceof Chest || state instanceof ShulkerBox) {
                        others.add(new ContainerRef(inventory, dropLoc, false, false));
                    }
                }
            }
        }
    }

    private boolean consumePearl(Inventory inventory) {
        if (inventory == null) return false;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null) continue;
            if (stack.getType() != Material.ENDER_PEARL) continue;
            if (CustomItems.isEnderIngot(stack)) continue;
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            }
            return true;
        }
        return false;
    }

    private boolean hasPearl(Inventory inventory) {
        if (inventory == null) return false;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null) continue;
            if (stack.getType() != Material.ENDER_PEARL) continue;
            if (CustomItems.isEnderIngot(stack)) continue;
            if (stack.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean consumeIngot(Inventory inventory, Location dropLoc, boolean returnToInventory) {
        if (inventory == null) return false;
        World world = dropLoc != null ? dropLoc.getWorld() : null;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null) continue;
            if (!CustomItems.isEnderIngot(stack)) continue;
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            }
            ItemStack refund = new ItemStack(Material.ENDER_PEARL, 9);
            if (returnToInventory) {
                Map<Integer, ItemStack> leftover = inventory.addItem(refund);
                if (world != null && dropLoc != null) {
                    for (ItemStack remain : leftover.values()) {
                        world.dropItemNaturally(dropLoc, remain);
                    }
                }
            } else if (world != null && dropLoc != null) {
                world.dropItemNaturally(dropLoc, refund);
            }
            return true;
        }
        return false;
    }

    private boolean hasIngot(Inventory inventory) {
        if (inventory == null) return false;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null) continue;
            if (!CustomItems.isEnderIngot(stack)) continue;
            if (stack.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }
}
