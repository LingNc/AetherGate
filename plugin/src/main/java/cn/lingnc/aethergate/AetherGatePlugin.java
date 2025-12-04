package cn.lingnc.aethergate;

import cn.lingnc.aethergate.altar.AltarService;
import cn.lingnc.aethergate.command.CharmCommand;
import cn.lingnc.aethergate.config.PluginConfig;
import cn.lingnc.aethergate.listener.WorldAnchorListener;
import cn.lingnc.aethergate.recipe.RecipeRegistry;
import cn.lingnc.aethergate.storage.SqliteStorage;
import cn.lingnc.aethergate.teleport.TeleportListener;
import cn.lingnc.aethergate.teleport.TeleportMenuService;
import cn.lingnc.aethergate.teleport.TeleportService;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class AetherGatePlugin extends JavaPlugin {

    private static AetherGatePlugin instance;
    private PluginConfig pluginConfig;
    private SqliteStorage storage;
    private AltarService altarService;
    private TeleportService teleportService;
    private TeleportMenuService teleportMenuService;
    private final Set<UUID> debugPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(getConfig());
        this.storage = new SqliteStorage(getDataFolder());
        try {
            storage.init();
        } catch (SQLException e) {
            getLogger().severe("Failed to init SQLite: " + e.getMessage());
        }
        RecipeRegistry.registerAll(this);
        this.altarService = new AltarService(this);
        altarService.loadExistingAltars();
        this.teleportService = new TeleportService(this, altarService);
        this.teleportMenuService = new TeleportMenuService(altarService);
        getServer().getPluginManager().registerEvents(new WorldAnchorListener(altarService, teleportMenuService), this);
        getServer().getPluginManager().registerEvents(new TeleportListener(teleportService), this);
        CharmCommand charmCommand = new CharmCommand(this, altarService, teleportService, teleportMenuService);
        var charmPluginCommand = Objects.requireNonNull(getCommand("charm"), "charm command not defined");
        charmPluginCommand.setExecutor(charmCommand);
        charmPluginCommand.setTabCompleter(charmCommand);
        getLogger().info("AetherGate enabled");
    }

    @Override
    public void onDisable() {
        if (altarService != null) {
            altarService.clearVisuals();
        }
        getLogger().info("AetherGate disabled");
    }

    public static AetherGatePlugin getInstance() {
        return instance;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public SqliteStorage getStorage() {
        return storage;
    }

    public AltarService getAltarService() {
        return altarService;
    }

    public TeleportService getTeleportService() {
        return teleportService;
    }

    public TeleportMenuService getTeleportMenuService() {
        return teleportMenuService;
    }

    public boolean toggleDebug(UUID uuid) {
        if (debugPlayers.contains(uuid)) {
            debugPlayers.remove(uuid);
            return false;
        }
        debugPlayers.add(uuid);
        return true;
    }

    public boolean isDebugEnabled(UUID uuid) {
        return debugPlayers.contains(uuid);
    }

    public void reloadPluginSettings() {
        reloadConfig();
        this.pluginConfig = new PluginConfig(getConfig());
        getLogger().info("AetherGate 配置已重载。");
    }
}

