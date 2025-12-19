package cn.lingnc.aethergate.teleport;

import cn.lingnc.aethergate.AetherGatePlugin;
import cn.lingnc.aethergate.altar.AltarService;
import cn.lingnc.aethergate.model.Waypoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TeleportMenuService {

    private static final int PAGE_WIDTH = 20;

    private final AetherGatePlugin plugin;
    private final AltarService altarService;
    private final Map<UUID, Location> pendingOrigins = new HashMap<>();
    private final PearlCostManager costProbe = new PearlCostManager();

    public TeleportMenuService(AetherGatePlugin plugin, AltarService altarService) {
        this.plugin = plugin;
        this.altarService = altarService;
    }

    public boolean openMenu(Player player, Block originBlock) {
        if (player == null || originBlock == null) {
            return false;
        }
        if (originBlock.getType() != Material.LODESTONE || !altarService.isAnchorBlock(originBlock)) {
            player.sendMessage("§c请面对一个已激活的世界锚点。");
            return false;
        }
        Waypoint originWaypoint = altarService.getActiveWaypoint(originBlock.getLocation());
        List<Waypoint> active = new ArrayList<>(altarService.getActiveAltars());
        if (originWaypoint != null) {
            active.removeIf(w -> w.getId().equals(originWaypoint.getId()));
        }
        if (active.isEmpty()) {
            player.sendMessage("§e除了这里，暂时没有其他可用的目标祭坛。");
            return false;
        }
        active.sort(Comparator.comparing(w -> w.getName().toLowerCase(Locale.ROOT)));
        boolean hasCharge = originWaypoint != null && (originWaypoint.isInfinite() || originWaypoint.getCharges() > 0);
        boolean ready = hasCharge && costProbe.hasEnoughPearls(originBlock.getLocation(), player, 1);
        ItemStack book = buildBook(active, originWaypoint, ready);
        if (book == null) {
            player.sendMessage("§c无法生成传送名册，请联系管理员。");
            return false;
        }
        pendingOrigins.put(player.getUniqueId(), originBlock.getLocation().clone());
        player.openBook(book);
        return true;
    }

    public Location getPendingOrigin(UUID uuid) {
        Location loc = pendingOrigins.get(uuid);
        return loc == null ? null : loc.clone();
    }

    public void clearPendingOrigin(UUID uuid) {
        pendingOrigins.remove(uuid);
    }

    private ItemStack buildBook(List<Waypoint> waypoints, Waypoint originWaypoint, boolean ready) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.title(Component.text("传送名册", NamedTextColor.AQUA));
        meta.author(Component.text("AetherGate", NamedTextColor.WHITE));
        Component header = buildHeader(originWaypoint, ready);
        List<Component> pages = buildPages(waypoints, header);
        meta.pages(pages);
        book.setItemMeta(meta);
        return book;
    }

    private List<Component> buildPages(List<Waypoint> waypoints, Component header) {
        List<Component> pages = new ArrayList<>();
        if (waypoints.isEmpty()) {
            pages.add(Component.empty().append(header)
                    .append(Component.text("暂无可用祭坛", NamedTextColor.GRAY)));
            return pages;
        }
        int index = 0;
        int firstPageEntries = Math.max(1, plugin.getPluginConfig().getMenuFirstPageEntries());
        Component firstPage = Component.empty().append(header);
        for (int count = 0; count < firstPageEntries && index < waypoints.size(); count++, index++) {
            firstPage = firstPage.append(buildEntry(waypoints.get(index)));
        }
        pages.add(firstPage);

        int otherPageEntries = Math.max(1, plugin.getPluginConfig().getMenuOtherPageEntries());
        while (index < waypoints.size()) {
            Component page = Component.empty();
            for (int count = 0; count < otherPageEntries && index < waypoints.size(); count++, index++) {
                page = page.append(buildEntry(waypoints.get(index)));
            }
            pages.add(page);
        }
        return pages;
    }

    private Component buildHeader(Waypoint originWaypoint, boolean ready) {
        String title = originWaypoint != null ? originWaypoint.getName() : "未登记祭坛";
        String durability = originWaypoint == null ? "未知" : (originWaypoint.isInfinite() ? "∞" : String.valueOf(originWaypoint.getCharges()));
        String statusLabel = ready ? "充能状态: [准备就绪]" : "充能状态: [能源不足]";
        NamedTextColor statusColor = ready ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text()
                .append(centerLine(title, NamedTextColor.BLACK, true))
                .append(Component.newline())
                .append(centerLine("当前耐久: " + durability, NamedTextColor.BLACK, false))
                .append(Component.newline())
                .append(Component.newline())
                .append(centerLine(statusLabel, statusColor, false))
                .append(Component.newline())
                .append(Component.newline())
                .build();
    }

    private Component centerLine(String text, NamedTextColor color, boolean bold) {
        if (text == null) {
            text = "";
        }
        int padding = Math.max(0, (PAGE_WIDTH - text.length()) / 2);
        String spaces = " ".repeat(padding);
        Component line = Component.text(spaces + text, color);
        return bold ? line.decorate(TextDecoration.BOLD) : line;
    }

    private Component buildEntry(Waypoint waypoint) {
        String charges = waypoint.isInfinite() ? "∞" : String.valueOf(waypoint.getCharges());
        String command = "/aether travel " + waypoint.getId();
        String locString = String.format("(%s: %d, %d, %d)",
            waypoint.getWorldName(), waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ());
        Component hover = Component.text()
            .append(Component.text("点击前往 ", NamedTextColor.GRAY))
            .append(Component.text(waypoint.getName(), NamedTextColor.BLACK, TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text(locString, NamedTextColor.DARK_GRAY))
            .build();

        Component firstLine = Component.text(waypoint.getName(), NamedTextColor.BLACK, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(hover));

        int padding = Math.max(2, PAGE_WIDTH - ("剩余: " + charges).length() - 6);
        String gap = " ".repeat(Math.min(10, padding));
        Component button = Component.text("[传送]", NamedTextColor.BLACK, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(hover));
        Component secondLine = Component.text()
                .append(Component.text("剩余: " + charges, NamedTextColor.DARK_GRAY))
                .append(Component.text(gap))
                .append(button)
                .build();

        return Component.text()
                .append(firstLine)
                .append(Component.newline())
                .append(secondLine)
            .append(Component.newline())
            .build();
    }
}
