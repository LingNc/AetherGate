package cn.lingnc.aethergate.achievement;

import cn.lingnc.aethergate.AetherGatePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

public class AchievementService {

    private static final String CRITERION = "unlock";
    private static final NamespacedKey BACAP_ROOT = new NamespacedKey("blazeandcave", "statistics/root");

    private final AetherGatePlugin plugin;
    private final Logger logger;
    private final Map<AdvancementType, NamespacedKey> keys = new EnumMap<>(AdvancementType.class);
    private final NamespacedKey builderCountKey;
    private boolean bacapDetected;

    public AchievementService(AetherGatePlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.builderCountKey = new NamespacedKey(plugin, "altars_activated");
        for (AdvancementType type : AdvancementType.values()) {
            keys.put(type, new NamespacedKey(plugin, type.getKey()));
        }
    }

    public void init() {
        detectBacap();
        registerAdvancements();
    }

    public void handleAnchorPlaced(Player player) {
        grant(player, AdvancementType.OBTAIN_ANCHOR);
    }

    public void handleAltarActivated(Player player) {
        if (player == null) {
            return;
        }
        grant(player, AdvancementType.ACTIVATE_ALTAR);
        int total = incrementBuilderCount(player);
        if (total >= 100) {
            grant(player, AdvancementType.BUILDER_100);
        } else if (total >= 50) {
            grant(player, AdvancementType.BUILDER_50);
        } else if (total >= 10) {
            grant(player, AdvancementType.BUILDER_10);
        }
    }

    public void handleTeleportComplete(Player player) {
        grant(player, AdvancementType.FIRST_TELEPORT);
    }

    public void handleInfiniteCharge(Player player) {
        grant(player, AdvancementType.INFINITE_POWER);
    }

    public void handleBackfire(Player player) {
        grant(player, AdvancementType.SACRIFICE);
    }

    private void grant(Player player, AdvancementType type) {
        if (player == null) {
            return;
        }
        Advancement advancement = Bukkit.getAdvancement(keys.get(type));
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        progress.awardCriteria(CRITERION);
    }

    private void detectBacap() {
        bacapDetected = Bukkit.getAdvancement(BACAP_ROOT) != null;
        if (bacapDetected) {
            logger.info("Detected BlazeandCave advancements; attaching to statistics/root.");
        }
    }

    private void registerAdvancements() {
        for (AdvancementType type : AdvancementType.values()) {
            register(type, specs(type));
        }
    }

private AdvancementSpec specs(AdvancementType type) {
        return switch (type) {
            case ROOT -> new AdvancementSpec(type, "以太探索者", "加入以太之门传送网络。",
                    Material.LODESTONE, Frame.TASK, true, true);
            case OBTAIN_ANCHOR -> new AdvancementSpec(type, "空间锚点", "放置一个世界锚点。",
                    Material.LODESTONE, Frame.TASK, true, true);
            case ACTIVATE_ALTAR -> new AdvancementSpec(type, "祭坛", "使用末影锭稳定并激活世界锚点。",
                    Material.ENDER_EYE, Frame.TASK, true, true);
            case FIRST_TELEPORT -> new AdvancementSpec(type, "跃迁", "在两个祭坛之间完成一次传送。",
                    Material.ENDER_PEARL, Frame.GOAL, true, true);
            case INFINITE_POWER -> new AdvancementSpec(type, "永恒能量", "赋予祭坛无穷无尽的能量。",
                    Material.NETHER_STAR, Frame.CHALLENGE, true, true);
            case BUILDER_10 -> new AdvancementSpec(type, "初级网络", "累计激活 10 个祭坛。",
                    Material.COPPER_BLOCK, Frame.TASK, true, true);
            case BUILDER_50 -> new AdvancementSpec(type, "资深祭坛师", "累计激活 50 个祭坛。",
                    Material.GOLD_BLOCK, Frame.GOAL, true, true);
            case BUILDER_100 -> new AdvancementSpec(type, "世界互联", "累计激活 100 个祭坛。",
                    Material.NETHERITE_BLOCK, Frame.CHALLENGE, true, true);
            case SACRIFICE -> new AdvancementSpec(type, "献祭", "触发一次灾难性的祭坛反噬。",
                    Material.TNT, Frame.CHALLENGE, true, true);
        };
    }

    private void register(AdvancementType type, AdvancementSpec spec) {
        NamespacedKey key = keys.get(type);
        try {
            Bukkit.getUnsafe().removeAdvancement(key);
        } catch (IllegalArgumentException ignored) {
            // ignore if not present
        }
        String json = buildJson(spec);
        Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
        if (advancement == null) {
            logger.warning("Failed to register advancement: " + key.toString());
        }
    }

    private String buildJson(AdvancementSpec spec) {
        String parent = spec.type() == AdvancementType.ROOT ? rootParent() : keys.get(AdvancementType.ROOT).toString();
        String background = spec.type() == AdvancementType.ROOT
                ? ",\n    \"background\": \"minecraft:textures/gui/advancements/backgrounds/end.png\"" : "";
        return "{" +
            "\n  \"parent\": \"" + parent + "\"," +
            "\n  \"criteria\": {\n    \"" + CRITERION + "\": {\n      \"trigger\": \"minecraft:impossible\"\n    }\n  }," +
            "\n  \"display\": {\n    \"icon\": {\"id\": \"" + spec.icon().getKey() + "\"}," +
            "\n    \"title\": {\"text\": \"" + spec.title() + "\"}," +
            "\n    \"description\": {\"text\": \"" + spec.description() + "\"}," +
            "\n    \"frame\": \"" + spec.frame().getKey() + "\"," +
            "\n    \"show_toast\": " + spec.toast() + ',' +
            "\n    \"announce_to_chat\": " + spec.announce() + ',' +
            "\n    \"hidden\": false" + background +
            "\n  }" +
            "\n}";
    }

    private String rootParent() {
        return bacapDetected ? BACAP_ROOT.toString() : "minecraft:adventure/root";
    }

    private int incrementBuilderCount(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        int current = container.getOrDefault(builderCountKey, PersistentDataType.INTEGER, 0);
        int updated = Math.max(0, current + 1);
        container.set(builderCountKey, PersistentDataType.INTEGER, updated);
        return updated;
    }

    private record AdvancementSpec(AdvancementType type, String title, String description,
                                   Material icon, Frame frame, boolean announce, boolean toast) {
        @Override
        public boolean toast() {
            return toast;
        }
    }

    private enum Frame {
        TASK("task"),
        GOAL("goal"),
        CHALLENGE("challenge");

        private final String key;

        Frame(String key) {
            this.key = key;
        }

        String getKey() {
            return key;
        }
    }

    public enum AdvancementType {
        ROOT("root"),
        OBTAIN_ANCHOR("obtain_anchor"),
        ACTIVATE_ALTAR("activate_altar"),
        FIRST_TELEPORT("first_teleport"),
        INFINITE_POWER("infinite_power"),
        BUILDER_10("builder_10"),
        BUILDER_50("builder_50"),
        BUILDER_100("builder_100"),
        SACRIFICE("sacrifice");

        private final String key;

        AdvancementType(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }
}
