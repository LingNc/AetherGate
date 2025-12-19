# v1.0.0
实现基本功能后
### fix 1.3
Updated PearlCostManager.java to treat custom fuel as the new ender ingot item everywhere (detection, availability checks, consumption).
consumeIngot now issues nine pearl refunds (mirroring the 9→1 crafting change) and skips standard pearls via CustomItems.isEnderIngot, ensuring inventories only lose true ingots.
Renamed helper logic (hasIngot, etc.) so remaining callers pick up the new semantics automatically.

### fix 1.4
Updated CustomItems.java so createWorldAnchorItem now sets its name via Component.text("世界锚点", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false), matching the Adventure API used elsewhere and removing the missing ChatColor dependency.
Verified the project no longer references ChatColor (workspace grep) and confirmed the file already imports Component, NamedTextColor, and TextDecoration, satisfying the fix_1.4 checklist.

### fix 1.5
TeleportMenuService.java now calls .build() on the Component.text() builder in buildEntry, so the method returns a Component instead of a TextComponent.Builder, resolving the Type mismatch error that blocked the Teleport menu.
Suggest rebuilding (build.sh) and reloading the plugin, then right-click a world anchor to confirm the book UI opens without exception.

### fix 1.6
Updated AltarService.java to use Particle.FIREWORK instead of the removed FIREWORKS_SPARK, preventing runtime particle lookups from failing on 1.21.4.
Adjusted RecipeRegistry.java so the world anchor recipe now uses the custom ender ingot (RecipeChoice.ExactChoice) in the center row (DID/LBL/DOD), ensuring only the bespoke ingot can craft anchors.
Reworked PearlCostManager.java so every inventory (core barrel, nearby containers, player) spends ender ingots before individual pearls, always converts an ingot into nine pearls, tries to return that change to the originating inventory, and only falls back to dropping leftovers if full.

### fix 1.7
TeleportMenuService.java now removes the current anchor from the destination list before sorting so the book never shows the origin entry, updates the “no targets” message, and formats hover text as world (x, y, z) for clearer negative coords.
AltarService.java generates default names with parentheses (Anchor (x,y,z), Altar (x,y,z)), eliminating the earlier dash-vs-negative ambiguity.

### fix 1.8
TeleportListener.java now cancels an in-progress teleport if the player moves (while still ignoring pure rotation), instead of snapping them back, so warmup can be interrupted by walking.
TeleportMenuService.java shows only two destinations per page and drops the leading > marker, which keeps each book page within vanilla height limits and restores the pagination buttons.

## v1.0.1
### fix 1.9 1.10
AltarService.java: raised the conduit display to y+2.0, enlarged its base scale to 1.5× (plus updated bob animation), aligned the hidden light block with the new height, expanded backfire particle ranges, and changed dormant handling so drained anchors keep a dimmed core (effects/light shut off but the display remains for later cleanup).
TeleportService.java: arrival sequences now trigger a shockwave helper (spawnArrivalShockwave) after the existing burst, adding a flash/explosion combo without damage.
TeleportMenuService.java: hover text coordinates render as (<world>: x, y, z), matching the v1.9 readability guideline.
plugin.yml, pom.xml, and dependency-reduced-pom.xml: version bumped to 1.0.1 (POMs now 1.0.1-SNAPSHOT) to track these fixes.

### fix 1.11
AltarService now loads every stored waypoint via loadAllWaypoints, keeps dormant entries in memory, and drives visuals through a new updateVisualState helper so inactive cores shrink, lose light, but remain spawned. Activation now happens through attemptActivation (ender ingot + structure validation) while recharge handles mineral power-ups/NETHER_STAR infinities without re-validating the build. Charge consumption refuses to start when an altar is dormant, preserves the waypoint entry, and flips visuals/DB state when charges hit zero so dormant anchors stay available as destinations.
WorldAnchorListener routes interactions by item: ender ingots trigger activation (with protection against wasting them on already-active anchors), mineral blocks call recharge, and other interactions are blocked until activation completes. A shared consumeItem helper handles survival-only item removal, and right-click guidance now tells players to activate before trying to rename/open menus.
Teleport flow (TeleportService, TeleportMenuService, CharmCommand) enforces that players stand on/in the altar block before /charm travel proceeds, prevents dormant origins from starting rituals, adjusts readiness indicators in the teleport book to require both charge and pearl fuel, and removes redundant “无法开始传送” spam so only the precise failure reason is shown.
UI polish: /charm list now reports “已登记的世界锚点”, and dormant anchors continue to appear in menus/command lookups with their remaining charges (possibly “0”/“∞”).

### fix 1.12
Updated config.yml with the new altar.backfire-power and teleport.arrival-radius defaults (plus inline docs) so server owners can tune blast damage and landing search size without touching code.
Extended PluginConfig to expose getBackfirePower() and getArrivalRadius(), then consumed those values in AltarService (configurable explosion power) and TeleportService (arrival search now honors the configured radius, clamped to ≥1).
Reworked the teleport book (TeleportMenuService): first page keeps the status header but now fits up to four entries, while later pages drop the header and list up to six destinations each, so players see far more anchors per book open.

### fix 1.13
Added the new config knobs in config.yml: teleport.interaction-radius plus teleport.menu.first-page-entries/other-page-entries, and wired them through PluginConfig so they’re available at runtime alongside the existing arrival/backfire values.
AltarService now exposes findNearestActiveAnchor and isWithinInteractionRange, both honoring the configured radius (horizontal square around the lodestone, +5 vertical). These helpers drive the updated /charm workflows and also gate TeleportService.beginTeleport, replacing the older “must stand on the core block” rule.
TeleportMenuService takes an AetherGatePlugin reference so it can size every book page from config (first page vs subsequent pages) while still showing the status header only once; AetherGatePlugin was updated to pass this.
CharmCommand now relies entirely on the new range-aware detection: /charm book, /charm travel, and /charm debugtp all look for the nearest registered anchor within the allowed zone (or re-use the locked-in block only if the player remains nearby), and the user feedback includes the current radius.
TeleportService simply reuses AltarService.isWithinInteractionRange instead of the old rigid check, so any origin provided by the command must still be near the player before the ritual starts.

## v1.0.2
AltarService now owns the new Interaction entities: every time updateVisualState runs it spawns a responsive interaction hitbox that mirrors the conduit’s scale, tracks it via coreInteractions/interactionAnchors, and removes it whenever visuals are torn down or the plugin shuts off. A helper getAnchorFromEntity resolves an interaction back to its lodestone so other systems can recognize which altar was clicked.
WorldAnchorListener stops opening the teleport book via lodestone clicks; empty-hand block clicks just remind players to touch the hovering core. A new onCoreInteract handler listens for PlayerInteractEntityEvent on the invisible interaction hitbox, cancels the event, and opens the teleport book from there. Charging, activation, and renaming behavior on the block are unchanged.
Tests not run (needs in-game verification). Please reload the plugin, then confirm:

Right-clicking the lodestone without a charge item only shows the “click the core” tip.
Clicking the floating core now opens the teleport UI.
Breaking/destroying an altar or reloading the plugin doesn’t leave invisible hitboxes behind (no ghost interactions when clicking the air).
Once these checks pass, v1.0.2’s UX change is complete.

### fix 1.14
findNearestActiveAnchor now mirrors the spec’s cubic detection: it skips dormant anchors (zero charges when not infinite) and only snaps to a lodestone when the player is inside the configured cube (radius + 0.5 horizontally, +1.0 vertically to tolerate the player’s height). Distances are measured against the altar core, so whichever anchor is closest within that cube is returned.
isWithinInteractionRange uses the same cube definition for all consumers: absolute block deltas on X/Y/Z must each be ≤ teleport.interaction-radius, instead of the previous “horizontal only + fixed 5 blocks up” check. This keeps /charm commands and teleport initiation consistent with the new detection.

### fix 1.15
WorldAnchorListener now stamps newly placed lodestones with the world-anchor PDC tag and blocks all interactions (ender ingot activation, charging, renaming, core entity menus) unless that tag exists, so only crafted anchors remain usable.
AltarService exposes hasAnchorTag, guards activation/recharge flows with it, and reuses the same tolerance math the search routine already used (±radius horizontally, radius+1 vertically) to keep menu access behavior consistent.
Existing untagged lodestones will be inert until players replace them with properly tagged anchors, matching the spec’s safety requirement.
Tests not run (not requested).

### fix 1.16
Added an activated flag to Waypoint plus a matching activated column in SqliteStorage; the storage init now auto-migrates older databases and always loads/saves this flag so anchors placed but not yet activated survive restarts.
registerPlacedAnchor now records world anchors in the DB/cache immediately (charges = 0, activated=false) while attemptActivation, recharge, consumeCharge, and the various query helpers only operate on entries whose isActivated() returns true. All PDC/tile-entity logic has been removed in favor of the registry + helper getOrLoadAnchor, and range/visual logic now ignores unactivated anchors.
getActiveAltars, findNearestActiveAnchor, consumeCharge, rename/dormant flows, and entity lookups now filter on isActivated() so dormant/placed anchors don’t leak into teleport UX.
WorldAnchorListener relies solely on AltarService: non-registered lodestones behave like vanilla, activation is the only action allowed on dormant anchors, recharge/rename/menu access stay blocked until activation, and breaking a registered anchor suppresses vanilla drops in favor of CustomItems.createWorldAnchorItem().

### fix 1.16.1
Removed the stray getOrLoadAnchor definition and other pasted fragments that were sitting inside the attemptActivation catch block, which caused the compile/runtime errors you reported.
Restored the intended activation flow: after persisting the waypoint we now repopulate activeAltars, refresh visuals, play effects, and notify the player; database failures now cleanly exit with an error message.
Verified that getOrLoadAnchor only exists once (near the end of the class) and all earlier references point to it.

## v1.0.3

### fix 1.17
AltarMaterialSet now keeps a Map<Material, Material> from every supported brick (vanilla, mossy/cracked variants, prismarine, mud, quartz, 1.21 tuff/resin, etc.) to its matching stair while leaving the light whitelist untouched.
AltarStructureChecker pulls the stair type directly from the material set, eliminating the old switch and letting any mapped base automatically validate both floor and stairs; stair orientation checks still run as before.
Bumped the plugin to version 1.0.3 across pom.xml, dependency-reduced-pom.xml, and plugin.yml.

### fix 1.18
updateVisualState now purges any lingering item displays at the altar location before spawning a fresh one, and the new ItemDisplay is marked setPersistent(false) so it won’t be written to disk and respawned by vanilla after restarts.
spawnInteractionEntity uses the same cleanup + non-persistence strategy for the Interaction hitbox, preventing duplicate/ghost entities from stock chunk loading.
Added clearOldEntities(Location, Class<?>) to encapsulate the cleanup, ensuring legacy “ghost” cores are removed automatically when the plugin rebuilds visuals.

### fix 1.19
Updated loadExistingAltars to only spawn visuals when the owning chunk is loaded, preventing chunkless ghost entities during startup, and added onChunkLoad in AltarService plus a corresponding ChunkLoadEvent listener so visuals appear automatically once their chunks enter memory. No automated tests run; consider a quick local start to confirm visuals pop in when walking into previously unloaded altar chunks.

## v1.0.4

### fix 1.20
WorldAnchorListener.java: consume logic now re-reads the player’s live main-hand stack so cancelled events no longer leave behind ghost name tags or other charge items.
pom.xml, plugin.yml, dependency-reduced-pom.xml: version strings bumped to 1.0.4 to mark fix 1.20.

### fix 1.21
目前有可以改进的地方：
1. 就是增加一个光源方块末地烛也支持。
2. 末影锭和铁锭目前还是混合状态，就是末影锭能干铁锭干的事，可以和村民交易合成等，还能合成其他的东西，有问题
Added END_ROD to altar light whitelist so altars accept end rods as valid light blocks.
Implemented CraftingProtectionListener to block ender ingots from serving as generic iron in any crafting and to require the world anchor’s ingot slot to be a true ender ingot.
Relaxed world anchor recipe to Material.IRON_INGOT for recipe book visibility; safety is enforced by the crafting listener.
Added RecipeUnlockListener to auto-discover anchor/ender ingot recipes on join.
Registered the new listeners in AetherGatePlugin.

## 更新版本号命名规则

## v1.4.0同v1.0.4 第一个所有功能均可正常使用的稳定版本
## v1.5.0
### plan 1
Created feature branch feat/config-refactor (commit 5eed710): altar materials now configurable via altar-materials in config.yml, defaults auto-copied on load, and AltarMaterialSet reloads from config.
功能测试正常，包括配置文件的注释都正常保留了。
### plan 2
Created feature branch feat/interaction-update (commit 9ecb6f1): main command is /aether with /charm & /ag aliases, book click actions updated, and ender ingot refunds now return max(0, 9-1)=8 pearls with overflow dropped when inventories are full.
### plan 3
Added sacrificial death messaging: backfire now tags nearby players, and DeathMessageListener sets a configurable message (default “%player% 被献祭了”) when they die to the explosion within 3s; metadata auto-cleans.
Config/PluginConfig gained death-messages.sacrifice option for the text.
Registered the new listener in AetherGatePlugin.
#### fix 1
DeathMessageListener: raised to EventPriority.HIGHEST and relaxed cause checking—any player tagged by the backfire metadata within 3s now gets the custom sacrifice message, overriding other plugins’ death text; metadata is still cleared immediately.
#### fix 2
DeathMessageListener now sets the custom sacrifice message regardless of death cause (still at HIGHEST priority) as long as the backfire metadata is present within the 3s window.
AltarService clears the lodestone (and barrel below) before triggering the backfire explosion so blast damage isn’t blocked; metadata tagging and drops remain unchanged.
#### fix 3
Implemented fix 3 on feat/death-messages: backfire now only clears the lodestone block before exploding, leaving the barrel intact so blast exposure is consistent without deleting the container.
#### fix 4
config.yml: added death-messages.sacrifice-success and death-messages.sacrifice-fail-prefix.
PluginConfig: getters for the new messages.
DeathMessageListener: uses Adventure Component API, branches on explosion vs other causes, prefixes non-explosion deaths during sacrifice, and always overrides via deathMessage(Component).
#### fix 5
Applied fix 5 on feat/death-messages: in AltarService.backfire victims are now tagged before the explosion is triggered, ensuring the death event sees the metadata for custom messaging.
#### fix 6
Removed the hardcoded trailing space in DeathMessageListener prefix deserialization.
Updated config.yml fail prefix to &c在献祭仪式中，&f (no embedded player name, spacing handled by text).
### plan 4
Added AchievementService to register plugin advancements (with BACAP root fallback), track altar activation counts, and grant rewards via PDC-backed milestones.
Wired achievement grants into gameplay: anchor placement, first activation (plus builder 10/50/100), successful teleport, infinite charge application, and backfire triggers now award the corresponding advancements.
Service is initialized during plugin startup and exposed via a getter for other systems to call.
#### fix 1
调整了进度 JSON 生成：icon 的键从 item 改为 id，符合 1.21+ 的组件格式，解决启动时报的 No key id 解析异常。
#### fix 2
WorldAnchorListener 使用新版组件 API：hasCustomName()/customName() + PlainTextComponentSerializer，避免 1.20.5+ 名牌显示名弃用问题。
#### fix 3
修改成就为中文展示。
## v1.6.0
### plan 1
feat/teleport-scan-lock: 新增全局锁集合，按根载具 UUID 锁定，防止同一实体被同时占用（构建通过）。
### plan 2
feat/teleport-cost-precheck: 重构珍珠消耗逻辑，新增 hasEnoughPearls(..., amount) 与 consumePearls(..., amount)，Teleport/菜单已切换到可变成本接口（构建通过）。
### plan 3
feat/teleport-movement-tolerance: 新增配置 teleport.allowable-movement-radius（默认 0.5），PluginConfig 读取。
移动事件改由 TeleportService.handleMove 处理：零容差用方块级判定，>0 时按水平半径判定超距中断。
清理 TeleportListener 旧逻辑，集中调用新容差处理。
构建通过。
### plan 4
feat/teleport-movement-tolerance: Added multi-target teleport support in TeleportService.java: collect nearby root entities (skipping other players/utility displays/foreign tames/locked entities), block teleport if your vehicle carries another player, precheck pearl cost for all paid targets, lock every root during warmup, consume the aggregated cost, and teleport all targets with slight offsets while keeping the initiating player invulnerable.
### plan 5 to fix
1. 在一定范围内无法取消传送，在点击开始传送后人好像已经被固定了一样，左右移动虽然移动了但是不会打断传送。但是到达目的地后左右移动会提示打断传送的错误提示，这个不该出现，虽然到达目的地之后有一段时间不可以左右移动，但是不应该提示用户传送被打断。
2. 可以在书里面增加一个取消传送的按钮（只有当在点击传送处在传送过程中打开书GUI才会显示这个按钮并且取消显示传送这个按钮）
3. 传送的时候如果玩家坐在载具或者马上，我发现点击传送之后可以正确触发粒子效果，但是传送的时候玩家和载具都不会被传送了，有问题，没有按照开始的要求完成（但是矿车不坐其他生物的时候就可以正常传送、船、马也是）。就是当载具有实体乘坐的时候便两者均无法传送，但是如果没人坐载具可以正常被传送，否则两者都在原地。
4. 对于被传送的实体没有正确在其上面显示粒子效果，和在传送到达前的预先的粒子效果（应该和玩家的类似）。
#### fix 1
Movement/mount fixes in TeleportService: movement checks now ignore post-teleport recovery; full mount graph captured, dismounted, teleported with stable per-entity offsets, then remounted after a short delay; players are explicitly teleported alongside mounts; arrival offsets reused for previews; warmup/preview particles now render around every target using height-aware spirals/columns.
UI/command updates: TeleportMenuService now shows an in-progress cancel book with countdown and a [终止仪式] button when the player is locked; /aether cancel added in CharmCommand; AetherGatePlugin wires the menu with the teleport service.
#### fix 2
1. 传送的生物什么的应该找到安全的不会窒息的地方，不应该和玩家一样，在祭坛内部应该有比较安全的地方，就是不会让生物产生窒息。
2. 取消移动打断传送的机制，并且在传送过程中玩家是有一点的免伤的会在传送期间获得一个生命恢复直到传送结束消失。（因为有可以中断的按钮了）
Movement check no longer cancels teleport; ritual stays active even if pushed.
Warmup/preview particles now split: player keeps original blue spiral/beam; followers get purple WITCH effects at their positions.
Teleport safety: per-entity landing is validated against solids (with fallback to arrival/above), preventing suffocation; mount knockback now guards zero-length vectors to avoid x not finite errors.
Resilience: added Regeneration during warmup and clear it on cleanup.
#### fix 3
rewrote consumePearls in PearlCostManager to an ingot-first smart deduction (ingots for bulk, pearls for remainder, auto-break ingot for change) with a shared takeItems helper. hasEnoughPearls unchanged.