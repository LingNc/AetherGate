package cn.lingnc.aethergate.util;

import cn.lingnc.aethergate.AetherGatePlugin;
import org.bukkit.NamespacedKey;

public final class BlockKeys {

    public static final NamespacedKey ENDER_PEARL_BLOCK = new NamespacedKey(AetherGatePlugin.getInstance(), "ender_pearl_block");
    public static final NamespacedKey WORLD_ANCHOR = new NamespacedKey(AetherGatePlugin.getInstance(), "world_anchor");

    private BlockKeys() {
    }
}
