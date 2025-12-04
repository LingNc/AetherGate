package cn.lingnc.aethergate.teleport;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;

public class ContainerRef {

    private final Inventory inventory;
    private final Location dropLocation;
    private final boolean barrel;
    private final boolean core;

    public ContainerRef(Inventory inventory, Location dropLocation, boolean barrel, boolean core) {
        this.inventory = inventory;
        this.dropLocation = dropLocation;
        this.barrel = barrel;
        this.core = core;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Location getDropLocation() {
        return dropLocation;
    }

    public boolean isBarrel() {
        return barrel;
    }

    public boolean isCore() {
        return core;
    }
}
