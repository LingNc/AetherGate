package cn.lingnc.aethergate.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class Waypoint {

    private final UUID id;
    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final UUID owner;
    private final int charges;
    private final boolean activated;

    // charges < 0 表示无限 (NETHER_STAR)

    public Waypoint(UUID id, String name, String worldName, double x, double y, double z, UUID owner, int charges, boolean activated) {
        this.id = id;
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.owner = owner;
        this.charges = charges;
        this.activated = activated;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getBlockX() {
        return (int) Math.round(x);
    }

    public int getBlockY() {
        return (int) Math.round(y);
    }

    public int getBlockZ() {
        return (int) Math.round(z);
    }

    public UUID getOwner() {
        return owner;
    }

    public int getCharges() {
        return charges;
    }

    public boolean isInfinite() {
        return charges < 0;
    }

    public boolean isActivated() {
        return activated;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}
