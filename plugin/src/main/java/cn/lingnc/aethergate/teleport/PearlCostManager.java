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
    private static final int PEARL_VALUE = 9;

    /**
     * Checks whether the anchor surroundings and player's inventory contain enough pearl-equivalent fuel.
     */
    public boolean hasEnoughPearls(Location anchorLoc, Player player, int requiredAmount) {
        if (anchorLoc == null || player == null || requiredAmount <= 0) {
            return false;
        }
        int available = 0;
        Inventory coreBarrel = getCoreBarrel(anchorLoc);
        available += pearlValue(coreBarrel);

        List<ContainerRef> barrels = new ArrayList<>();
        List<ContainerRef> others = new ArrayList<>();
        collectContainers(anchorLoc, barrels, others);

        for (ContainerRef ref : barrels) {
            if (ref.isCore()) continue;
            available += pearlValue(ref.getInventory());
            if (available >= requiredAmount) return true;
        }
        for (ContainerRef ref : others) {
            available += pearlValue(ref.getInventory());
            if (available >= requiredAmount) return true;
        }

        available += pearlValue(player.getInventory());
        return available >= requiredAmount;
    }

    /**
     * Consumes pearl-equivalent fuel using an ingot-first strategy:
     * 1) pay large portion with ingots, 2) pay remainder with pearls, 3) if pearls short, break one ingot for change.
     */
    public boolean consumePearls(Location anchorLoc, Player player, int amount) {
        if (anchorLoc == null || player == null || amount <= 0) {
            return false;
        }

        // Double-check affordability
        if (!hasEnoughPearls(anchorLoc, player, amount)) {
            return false;
        }

        Inventory coreBarrel = getCoreBarrel(anchorLoc);
        List<ContainerRef> barrels = new ArrayList<>();
        List<ContainerRef> others = new ArrayList<>();
        collectContainers(anchorLoc, barrels, others);

        List<Inventory> inventories = new ArrayList<>();
        List<Location> dropLocations = new ArrayList<>();

        if (coreBarrel != null) {
            inventories.add(coreBarrel);
            dropLocations.add(anchorLoc);
        }
        for (ContainerRef ref : barrels) {
            if (ref.isCore()) {
                continue;
            }
            inventories.add(ref.getInventory());
            dropLocations.add(ref.getDropLocation());
        }
        for (ContainerRef ref : others) {
            inventories.add(ref.getInventory());
            dropLocations.add(ref.getDropLocation());
        }
        inventories.add(player.getInventory());
        dropLocations.add(player.getLocation());

        int remainingCost = amount;

        // Phase 1: pay large portion with ingots
        int idealIngots = remainingCost / PEARL_VALUE;
        if (idealIngots > 0) {
            int takenIngots = 0;
            for (Inventory inv : inventories) {
                if (takenIngots >= idealIngots) {
                    break;
                }
                takenIngots += takeItems(inv, true, idealIngots - takenIngots);
            }
            remainingCost -= takenIngots * PEARL_VALUE;
        }

        // Phase 2: pay remainder with pearls
        if (remainingCost > 0) {
            int takenPearls = 0;
            for (Inventory inv : inventories) {
                if (takenPearls >= remainingCost) {
                    break;
                }
                takenPearls += takeItems(inv, false, remainingCost - takenPearls);
            }
            remainingCost -= takenPearls;
        }

        // Phase 3: if pearls are short, break one ingot for change
        if (remainingCost > 0) {
            for (int i = 0; i < inventories.size(); i++) {
                if (remainingCost <= 0) {
                    break;
                }
                Inventory inv = inventories.get(i);
                Location dropLoc = dropLocations.get(i);
                if (takeItems(inv, true, 1) == 1) {
                    int change = PEARL_VALUE - remainingCost;
                    refundPearls(inv, dropLoc, change, dropLoc.getWorld());
                    remainingCost = 0;
                }
            }
        }

        return remainingCost <= 0;
    }

    /**
     * Removes up to maxToTake items of the requested type.
     * @param targetIsIngot true for ingots, false for pearls
     * @return number of items removed
     */
    private int takeItems(Inventory inventory, boolean targetIsIngot, int maxToTake) {
        if (inventory == null || maxToTake <= 0) {
            return 0;
        }
        int taken = 0;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && taken < maxToTake; i++) {
            ItemStack stack = contents[i];
            if (stack == null) {
                continue;
            }
            boolean isIngot = CustomItems.isEnderIngot(stack);
            boolean isPearl = stack.getType() == Material.ENDER_PEARL && !isIngot;
            if (targetIsIngot && !isIngot) {
                continue;
            }
            if (!targetIsIngot && !isPearl) {
                continue;
            }
            int available = stack.getAmount();
            int toRemove = Math.min(available, maxToTake - taken);
            stack.setAmount(available - toRemove);
            if (stack.getAmount() <= 0) {
                inventory.setItem(i, null);
            }
            taken += toRemove;
        }
        return taken;
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

    private int consumePearlAmount(Inventory inventory, int remaining) {
        if (inventory == null || remaining <= 0) {
            return remaining;
        }
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null) continue;
            if (stack.getType() != Material.ENDER_PEARL) continue;
            if (CustomItems.isEnderIngot(stack)) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            }
            remaining -= take;
        }
        return remaining;
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

    private int consumeIngotAmount(Inventory inventory, Location dropLoc, int remaining) {
        if (inventory == null || remaining <= 0) {
            return remaining;
        }
        World world = dropLoc != null ? dropLoc.getWorld() : null;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null) continue;
            if (!CustomItems.isEnderIngot(stack)) continue;
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            }
            int applied = Math.min(PEARL_VALUE, remaining);
            remaining -= applied;
            int refundCount = PEARL_VALUE - applied;
            if (refundCount > 0) {
                refundPearls(inventory, dropLoc, refundCount, world);
            }
        }
        return remaining;
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

    private int pearlValue(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null) continue;
            if (stack.getType() == Material.ENDER_PEARL && !CustomItems.isEnderIngot(stack)) {
                total += stack.getAmount();
            } else if (CustomItems.isEnderIngot(stack)) {
                total += stack.getAmount() * PEARL_VALUE;
            }
        }
        return total;
    }

    private void refundPearls(Inventory inventory, Location dropLoc, int refundCount, World world) {
        if (refundCount <= 0) {
            return;
        }
        ItemStack refund = new ItemStack(Material.ENDER_PEARL, refundCount);
        if (inventory != null) {
            Map<Integer, ItemStack> leftover = inventory.addItem(refund);
            if (world != null && dropLoc != null) {
                for (ItemStack remain : leftover.values()) {
                    world.dropItemNaturally(dropLoc, remain);
                }
            }
        } else if (world != null && dropLoc != null) {
            world.dropItemNaturally(dropLoc, refund);
        }
    }
}
