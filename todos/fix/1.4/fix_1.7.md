AetherGate 修复说明书 (v1.7)
优先级: 高 (Usability) 目标版本: Paper 1.21.4

1. 修复 GUI 显示与列表逻辑
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/teleport/TeleportMenuService.java

修改 1：排除当前祭坛 在 openMenu 方法中，获取 active 列表后，需要将 originWaypoint 从列表中移除。

修改 2：坐标格式优化 在 buildEntry 方法的 hover 文本中，优化坐标的显示格式，增加括号以区分负号。

代码修改指南
请直接替换 TeleportMenuService.java 中的相关方法，或参考以下逻辑修改。

1. 修改 openMenu 方法：

Java

    public boolean openMenu(Player player, Block originBlock) {
        if (player == null || originBlock == null) {
            return false;
        }
        if (originBlock.getType() != Material.LODESTONE || !altarService.isAnchorBlock(originBlock)) {
            player.sendMessage("§c请面对一个已激活的世界锚点。");
            return false;
        }

        // 获取当前所在的祭坛
        Waypoint originWaypoint = altarService.getActiveWaypoint(originBlock.getLocation());

        // 获取所有激活祭坛
        List<Waypoint> active = new ArrayList<>(altarService.getActiveAltars());

        // 关键修改：从列表中移除当前祭坛
        if (originWaypoint != null) {
            active.removeIf(w -> w.getId().equals(originWaypoint.getId()));
        }

        if (active.isEmpty()) {
            player.sendMessage("§e除了这里，暂时没有其他可用的目标祭坛。");
            return false;
        }

        active.sort(Comparator.comparing(w -> w.getName().toLowerCase(Locale.ROOT)));

        boolean ready = costProbe.hasAvailableFuel(originBlock.getLocation(), player);
        ItemStack book = buildBook(active, originWaypoint, ready);
        if (book == null) {
            player.sendMessage("§c无法生成传送名册，请联系管理员。");
            return false;
        }
        pendingOrigins.put(player.getUniqueId(), originBlock.getLocation().clone());
        player.openBook(book);
        return true;
    }
2. 修改 buildEntry 方法 (优化坐标显示)：

Java

    private Component buildEntry(Waypoint waypoint) {
        String charges = waypoint.isInfinite() ? "∞" : String.valueOf(waypoint.getCharges());
        String command = "/charm travel " + waypoint.getId();

        // 关键修改：优化坐标格式，增加括号
        String locString = String.format("%s (%d, %d, %d)",
                waypoint.getWorldName(), waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ());

        Component hover = Component.text()
                .append(Component.text("点击前往 ", NamedTextColor.GRAY))
                .append(Component.text(waypoint.getName(), NamedTextColor.BLACK, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(locString, NamedTextColor.DARK_GRAY))
                .build();

        Component firstLine = Component.text("> " + waypoint.getName(), NamedTextColor.BLACK, TextDecoration.BOLD)
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
注意： 如果你的截图中的 Anchor--28 是祭坛名称 (Name) 本身（因为默认名称生成逻辑是 Anchor-x,y,z），那么还需要修改默认名称的生成逻辑。

3. 修改默认名称生成逻辑 (可选，建议修改)

涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java

在 activateOrBackfire 方法中，创建新 Waypoint 时：

Java

// 旧代码
"Altar-" + lodestone.getX() + "," + lodestone.getY() + "," + lodestone.getZ()

// 新代码 (建议)
"Altar (" + lodestone.getX() + "," + lodestone.getY() + "," + lodestone.getZ() + ")"
或者在 registerPlacedAnchor 方法中：

Java

// 旧代码
"Anchor-" + x + "," + y + "," + z

// 新代码 (建议)
"Anchor (" + x + "," + y + "," + z + ")"
这样生成的默认名称就会变成 Anchor (-28, 69, -30)，更加清晰。

2. 检查清单
[ ] TeleportMenuService.java: 确认 openMenu 中已移除当前祭坛 (active.removeIf(...))。

[ ] TeleportMenuService.java: 确认 buildEntry 中的 Hover 文本格式已优化。

[ ] AltarService.java: (可选) 确认新创建的祭坛默认名称格式已优化，避免负号歧义。

完成这些修改后，重新编译插件即可解决界面显示不清和列表包含自身的问题。