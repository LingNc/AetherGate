package cn.lingnc.aethergate.command;

import cn.lingnc.aethergate.AetherGatePlugin;
import cn.lingnc.aethergate.altar.AltarService;
import cn.lingnc.aethergate.item.CustomItems;
import cn.lingnc.aethergate.model.Waypoint;
import cn.lingnc.aethergate.teleport.TeleportMenuService;
import cn.lingnc.aethergate.teleport.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CharmCommand implements CommandExecutor, TabCompleter {

    private final AetherGatePlugin plugin;
    private final AltarService altarService;
    private final TeleportService teleportService;
    private final TeleportMenuService menuService;

    public CharmCommand(AetherGatePlugin plugin, AltarService altarService,
                        TeleportService teleportService, TeleportMenuService menuService) {
        this.plugin = plugin;
        this.altarService = altarService;
        this.teleportService = teleportService;
        this.menuService = menuService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "list" -> handleList(sender);
            case "book" -> requirePlayer(sender, player -> handleBook(player));
            case "travel" -> requirePlayer(sender, player -> handleTravel(player, Arrays.copyOfRange(args, 1, args.length)));
            case "give_block" -> requirePlayer(sender, player -> handleGiveBlock(player, Arrays.copyOfRange(args, 1, args.length)));
            case "reload" -> handleReload(sender);
            case "debug" -> requirePlayer(sender, this::handleDebugToggle);
            case "debugtp" -> requirePlayer(sender, player -> handleDebugTeleport(player, Arrays.copyOfRange(args, 1, args.length)));
            case "cancel" -> requirePlayer(sender, this::handleCancel);
            case "fix" -> handleFix(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleList(CommandSender sender) {
        List<Waypoint> active = new ArrayList<>(altarService.getActiveAltars());
        if (active.isEmpty()) {
            sender.sendMessage("§e当前没有登记的世界锚点。");
            return true;
        }
        active.sort(Comparator.comparing(w -> w.getName().toLowerCase(Locale.ROOT)));
        sender.sendMessage("§b==== 已登记的世界锚点 ====");
        active.forEach(waypoint -> sender.sendMessage(String.format("§f- %s §7(%s %d %d %d) §a剩余:%s",
                waypoint.getName(), waypoint.getWorldName(), waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ(),
                waypoint.isInfinite() ? "∞" : waypoint.getCharges())));
        return true;
    }

    private boolean handleBook(Player player) {
        Block origin = altarService.findNearestActiveAnchor(player.getLocation());
        if (origin == null) {
            int radius = plugin.getPluginConfig().getInteractionRadius();
            player.sendMessage("§c请站在距离祭坛不超过 " + radius + " 格的范围内。");
            return true;
        }
        menuService.openMenu(player, origin);
        return true;
    }

    private boolean handleTravel(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§e用法: /aether travel <目标名称或 UUID>");
            return true;
        }
        String target = String.join(" ", args);
        Waypoint destination = resolveDestination(target);
        if (destination == null) {
            player.sendMessage("§c找不到匹配的目标: " + target);
            return true;
        }
        Block origin = locateOrigin(player);
        if (origin == null) {
            int radius = plugin.getPluginConfig().getInteractionRadius();
            player.sendMessage("§c请靠近祭坛 (水平 " + radius + " 格内) 并重新尝试。");
            return true;
        }
        if (teleportService.beginTeleport(player, origin, destination)) {
            menuService.clearPendingOrigin(player.getUniqueId());
        }
        return true;
    }

    private boolean handleGiveBlock(Player player, String[] args) {
        if (!player.hasPermission("aethergate.admin")) {
            player.sendMessage("§c你没有权限使用该指令。");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§e用法: /aether give_block <anchor|ingot> [数量]");
            return true;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                player.sendMessage("§c数量必须是 1-64 的整数。");
                return true;
            }
        }
        ItemStack stack;
        switch (type) {
            case "anchor" -> {
                stack = CustomItems.createWorldAnchorItem();
                stack.setAmount(amount);
            }
            case "ingot", "pearl", "block" -> stack = CustomItems.createEnderIngotItem(amount);
            default -> {
                player.sendMessage("§c未知类型，仅支持 anchor 或 ingot。");
                return true;
            }
        }
        player.getInventory().addItem(stack);
        player.sendMessage("§a已发放 " + amount + " 个 " + (type.equals("anchor") ? "世界锚点" : "末影锭"));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("aethergate.admin")) {
            sender.sendMessage("§c你没有权限执行重载。");
            return true;
        }
        plugin.reloadPluginSettings();
        sender.sendMessage("§a配置已重载。");
        return true;
    }

    private boolean handleDebugToggle(Player player) {
        if (!player.hasPermission("aethergate.debug")) {
            player.sendMessage("§c你没有权限切换调试模式。");
            return true;
        }
        boolean enabled = plugin.toggleDebug(player.getUniqueId());
        player.sendMessage(enabled ? "§a祭坛结构调试已开启。" : "§e祭坛结构调试已关闭。");
        return true;
    }

    private boolean handleDebugTeleport(Player player, String[] args) {
        if (!player.hasPermission("aethergate.debug")) {
            player.sendMessage("§c你没有权限使用调试传送。");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§e用法: /aether debugtp <锚点名称>");
            return true;
        }
        String targetName = String.join(" ", args);
        Waypoint destination = altarService.findActiveByName(targetName);
        if (destination == null) {
            player.sendMessage("§c找不到激活中的锚点: " + targetName);
            return true;
        }
        Block origin = altarService.findNearestActiveAnchor(player.getLocation());
        if (origin == null) {
            int radius = plugin.getPluginConfig().getInteractionRadius();
            player.sendMessage("§c请靠近一个已激活的世界锚点 (<= " + radius + " 格)。");
            return true;
        }
        teleportService.beginTeleport(player, origin, destination);
        return true;
    }

    private boolean handleCancel(Player player) {
        if (!teleportService.isPlayerLocked(player.getUniqueId())) {
            player.sendMessage("§e当前没有正在进行的传送。");
            return true;
        }
        teleportService.cancelTeleport(player, "§e你主动中止了传送。");
        menuService.clearPendingOrigin(player.getUniqueId());
        return true;
    }

    /**
     * @param sender
     * @param args
     * @return
     */
    private boolean handleFix(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aethergate.admin")) {
            sender.sendMessage("§c你没有权限执行修复。");
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§c找不到在线玩家: " + args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c控制台请指定玩家: /aether fix <玩家>。");
                return true;
            }
            target = player;
        }

        target.setInvulnerable(false);
        target.setWalkSpeed(0.2f);
        target.setFlySpeed(0.1f);
        target.setSprinting(false);
        target.removePotionEffect(PotionEffectType.JUMP_BOOST);
        target.removePotionEffect(PotionEffectType.BLINDNESS);
        target.removePotionEffect(PotionEffectType.REGENERATION);

        target.sendMessage("§a已重置传送保护状态。");
        if (!target.equals(sender)) {
            sender.sendMessage("§a已重置 " + target.getName() + " 的传送保护状态。");
        }
        return true;
    }

    private Waypoint resolveDestination(String input) {
        try {
            UUID id = UUID.fromString(input);
            Waypoint byId = altarService.findActiveById(id);
            if (byId != null) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return altarService.findActiveByName(input);
    }

    private Block locateOrigin(Player player) {
        Block origin = null;
        var pending = menuService.getPendingOrigin(player.getUniqueId());
        if (pending != null) {
            Block stored = pending.getBlock();
            if (stored.getType() == Material.LODESTONE
                    && altarService.isAnchorBlock(stored)
                    && altarService.isWithinInteractionRange(player.getLocation(), stored)) {
                origin = stored;
            }
        }
        if (origin == null) {
            origin = altarService.findNearestActiveAnchor(player.getLocation());
        }
        return origin;
    }

    private boolean requirePlayer(CommandSender sender, Function<Player, Boolean> action) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以执行该命令。");
            return true;
        }
        return action.apply(player);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b/aether list §7- 查看所有激活的世界锚点");
        sender.sendMessage("§b/aether book §7- 面对锚点打开传送书");
        sender.sendMessage("§b/aether travel <目标> §7- 选择书中列出的目的地");
        sender.sendMessage("§b/aether cancel §7- 中止正在进行的传送");
        sender.sendMessage("§b/aether give_block <anchor|pearl> [数量] §7- 管理员发放物品");
        sender.sendMessage("§b/aether reload §7- 重新载入配置");
        sender.sendMessage("§b/aether debug §7- 切换结构调试模式");
        sender.sendMessage("§b/aether fix [玩家] §7- 解除无敌并重置移动状态");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> subs = List.of("list", "book", "travel", "give_block", "reload", "debug", "debugtp", "cancel", "fix");
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        if (args[0].equalsIgnoreCase("travel") && args.length >= 2) {
            String partial = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
            return altarService.getActiveAltars().stream()
                    .map(Waypoint::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args[0].equalsIgnoreCase("give_block") && args.length == 2) {
            return List.of("anchor", "pearl");
        }
        if (args[0].equalsIgnoreCase("debugtp") && args.length >= 2) {
            String partial = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
            return altarService.getActiveAltars().stream()
                    .map(Waypoint::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args[0].equalsIgnoreCase("fix") && args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
