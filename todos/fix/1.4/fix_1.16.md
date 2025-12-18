这是一个针对 AetherGate v1.16 的紧急修复说明书。

问题根源分析：

PDC 失效 (核心问题): 你分析得完全正确。在 Minecraft 中，磁石 (Lodestone) 只是一个普通方块，不具备 TileEntity (BlockEntity) 属性，因此无法存储 NBT/PDC 数据。之前的代码试图向方块写入数据是徒劳的，导致系统无法区分“普通磁石”和“世界锚点”。

解决方案: 我们必须改变策略，不再依赖方块 NBT，而是依赖数据库。当玩家放置“世界锚点”物品时，立即在数据库中记录该坐标（标记为未激活/休眠）。交互和破坏时，通过查询坐标是否存在于数据库来判断它是否为“世界锚点”。

石砖问题: 材质列表确实漏掉了原版石砖。

请将以下文档交付给开发人员。

AetherGate 核心机制重构说明书 (v1.16)
优先级: 紧急 (Critical Bug Fix) 目标版本: Paper 1.21.4

1. 材质库修复 (AltarMaterialSet.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarMaterialSet.java

修改: 补充缺失的石砖系列。

2. 数据库查询修正 (SqliteStorage.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/storage/SqliteStorage.java

修改: loadActiveWaypoints 方法必须加载所有锚点（包括刚才放置、未充能的锚点），否则刚放下的锚点无法被识别。建议改名为 loadAllWaypoints。

3. 祭坛逻辑重写 (AltarService.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java

核心变更:

移除 hasAnchorTag (因为它依赖方块 PDC，已不可用)。

修改 registerPlacedAnchor：放置时直接向数据库写入一条 charges=0 的记录，并将其加入内存缓存。这是识别“它是世界锚点而非普通磁石”的唯一凭证。

修改 isAnchorBlock：直接查内存表。

4. 监听器修正 (WorldAnchorListener.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/listener/WorldAnchorListener.java

修改核心:

OnPlace: 如果放的是 CustomItems.WORLD_ANCHOR，调用 altarService.registerPlacedAnchor。这步至关重要，它是识别方块的唯一机会。

OnInteract: 通过 altarService.isAnchorBlock(block) 判断是否为锚点。如果是 -> 走锚点逻辑；如果不是 -> 直接 return (让原版磁石正常工作)。

OnBreak: 同样通过 isAnchorBlock 判断。如果是 -> 取消原版掉落，掉落自定义物品，清理数据；如果是 -> return (原版掉落)。