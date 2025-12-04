package cn.lingnc.aethergate.command;

import cn.lingnc.aethergate.AetherGatePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class AetherGateCommand implements CommandExecutor, TabCompleter {

    private final AetherGatePlugin plugin;

    public AetherGateCommand(AetherGatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§b用法: /aethergate debug");
            return true;
        }
        if (!"debug".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§c未知子命令，当前仅支持 /aethergate debug。");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以切换调试模式。");
            return true;
        }
        boolean enabled = plugin.toggleDebug(player.getUniqueId());
        player.sendMessage(enabled ? "§a祭坛结构调试已开启。" : "§e祭坛结构调试已关闭。");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("debug");
        }
        return Collections.emptyList();
    }
}
