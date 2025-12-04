package cn.lingnc.aethergate.config;

import org.bukkit.configuration.file.FileConfiguration;

public class PluginConfig {

    private final FileConfiguration config;

    public PluginConfig(FileConfiguration config) {
        this.config = config;
    }

    public int getAltarSearchRadius() {
        return config.getInt("altar.search-radius", 5);
    }

    public double getBackfirePower() {
        return config.getDouble("altar.backfire-power", 6.0);
    }

    public int getArrivalRadius() {
        return config.getInt("teleport.arrival-radius", 2);
    }

    public int getInteractionRadius() {
        return config.getInt("teleport.interaction-radius", 2);
    }

    public int getMenuFirstPageEntries() {
        return config.getInt("teleport.menu.first-page-entries", 4);
    }

    public int getMenuOtherPageEntries() {
        return config.getInt("teleport.menu.other-page-entries", 7);
    }
}
