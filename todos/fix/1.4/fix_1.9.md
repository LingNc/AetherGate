
这是一个针对 AetherGate v1.9 的修复说明书，专注于解决 UI 视觉显示问题。

问题分析：

坐标显示不清: 祭坛名称如果默认包含负号（例如 Anchor--33...），在书本界面中与分隔符混淆，难以辨认。需要将坐标部分用括号包裹，例如 Anchor (-33, 74, -34)。

名称生成逻辑: 为了从源头解决这个问题，新建祭坛的默认名称生成逻辑也需要修改。

请将以下文档交付给开发人员。

AetherGate 修复说明书 (v1.9)
优先级: 低 (Visual Polish) 目标版本: Paper 1.21.4

1. 修复坐标显示格式 (UI Enhancement)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/teleport/TeleportMenuService.java

修改目标: 在传送书本的悬浮提示 (Hover Text) 中，将坐标信息的格式从原本的 Anchor--28,69,-30 优化为更易读的 Anchor (-28, 69, -30)。

修改代码 (buildEntry 方法):

Java
    private Component buildEntry(Waypoint waypoint) {
        String charges = waypoint.isInfinite() ? "∞" : String.valueOf(waypoint.getCharges());
        String command = "/charm travel " + waypoint.getId();

        // 关键修改：优化 Hover 文本中的坐标格式
        // 原代码可能是直接拼接，现在改为带括号的格式
        String locString = String.format("§7(%s: %d, %d, %d)",
                waypoint.getWorldName(), waypoint.getBlockX(), waypoint.getBlockY(), waypoint.getBlockZ());

        Component hover = Component.text()
                .append(Component.text("点击前往 ", NamedTextColor.GRAY))
                .append(Component.text(waypoint.getName(), NamedTextColor.BLACK, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(locString, NamedTextColor.DARK_GRAY)) // 使用优化后的 locString
                .build();

        // 同时，如果想让书本列表里的标题也显示得更清楚（如果标题本身包含坐标）
        // 我们不修改 waypoint.getName() 的存储，只在显示时做处理
        // 但根据截图，问题主要出在 waypoint.getName() 本身的默认命名上

        // ... (后续代码保持不变)
2. 优化默认命名逻辑 (Source Fix)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java

问题根源: 截图中的 Anchor--28,69,-30 实际上是数据库中存储的 祭坛名称 (Name)。这是因为代码在创建新祭坛时，默认使用的名称格式是 Anchor-x,y,z。当坐标为负数时，就会出现双横杠 --。

修改方案: 修改默认名称的生成格式，使其在创建时就自带括号。

修改代码 (registerPlacedAnchor 方法 & activateOrBackfire 方法):

查找所有生成默认名称的代码行（通常有两处）：

修改前:

Java
"Anchor-" + x + "," + y + "," + z
修改后:

Java
"Anchor (" + x + "," + y + "," + z + ")"
示例代码片段 (AltarService.java):

Java
    // 在 registerPlacedAnchor 方法中
    Waypoint waypoint = new Waypoint(UUID.randomUUID(),
            "Anchor (" + x + "," + y + "," + z + ")", // 修改此处
            worldName, x, y, z, owner, 0);

    // 在 activateOrBackfire 方法中
    if (existing == null) {
        // ...
        target = new Waypoint(UUID.randomUUID(),
                "Altar (" + lodestone.getX() + "," + lodestone.getY() + "," + lodestone.getZ() + ")", // 修改此处
                world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), owner, baseCharges);
    }
3. 补充说明
对现有数据的影响: 修改代码 不会 自动更新数据库中已存在的旧名称（如 Anchor--28...）。

手动修复: 玩家需要破坏并重新放置锚点，或者使用命名牌重命名，才能看到新的格式。

开发建议: 不需要编写数据库迁移脚本，仅修复新生成的祭坛即可。

4. 检查清单
[ ] AltarService.java: 确认所有 new Waypoint(...) 时生成的默认名称字符串已改为带括号的格式 Anchor (x,y,z)。

[ ] TeleportMenuService.java: 确认 Hover 文本的格式也进行了括号优化，以防万一。

完成这些修改后，新创建的祭坛将显示为 Anchor (-28, 69, -30)，负号将清晰可见。