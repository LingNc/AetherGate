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
}
