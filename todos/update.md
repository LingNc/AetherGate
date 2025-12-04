v1.0.0
实现基本功能后
fix 1.3
Updated PearlCostManager.java to treat custom fuel as the new ender ingot item everywhere (detection, availability checks, consumption).
consumeIngot now issues nine pearl refunds (mirroring the 9→1 crafting change) and skips standard pearls via CustomItems.isEnderIngot, ensuring inventories only lose true ingots.
Renamed helper logic (hasIngot, etc.) so remaining callers pick up the new semantics automatically.

fix 1.4
Updated CustomItems.java so createWorldAnchorItem now sets its name via Component.text("世界锚点", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false), matching the Adventure API used elsewhere and removing the missing ChatColor dependency.
Verified the project no longer references ChatColor (workspace grep) and confirmed the file already imports Component, NamedTextColor, and TextDecoration, satisfying the fix_1.4 checklist.

fix 1.5
TeleportMenuService.java now calls .build() on the Component.text() builder in buildEntry, so the method returns a Component instead of a TextComponent.Builder, resolving the Type mismatch error that blocked the Teleport menu.
Suggest rebuilding (build.sh) and reloading the plugin, then right-click a world anchor to confirm the book UI opens without exception.

fix 1.6
Updated AltarService.java to use Particle.FIREWORK instead of the removed FIREWORKS_SPARK, preventing runtime particle lookups from failing on 1.21.4.
Adjusted RecipeRegistry.java so the world anchor recipe now uses the custom ender ingot (RecipeChoice.ExactChoice) in the center row (DID/LBL/DOD), ensuring only the bespoke ingot can craft anchors.
Reworked PearlCostManager.java so every inventory (core barrel, nearby containers, player) spends ender ingots before individual pearls, always converts an ingot into nine pearls, tries to return that change to the originating inventory, and only falls back to dropping leftovers if full.

fix 1.7
TeleportMenuService.java now removes the current anchor from the destination list before sorting so the book never shows the origin entry, updates the “no targets” message, and formats hover text as world (x, y, z) for clearer negative coords.
AltarService.java generates default names with parentheses (Anchor (x,y,z), Altar (x,y,z)), eliminating the earlier dash-vs-negative ambiguity.

fix 1.8
TeleportListener.java now cancels an in-progress teleport if the player moves (while still ignoring pure rotation), instead of snapping them back, so warmup can be interrupted by walking.
TeleportMenuService.java shows only two destinations per page and drops the leading > marker, which keeps each book page within vanilla height limits and restores the pagination buttons.

v1.0.1
fix 1.9 1.10
AltarService.java: raised the conduit display to y+2.0, enlarged its base scale to 1.5× (plus updated bob animation), aligned the hidden light block with the new height, expanded backfire particle ranges, and changed dormant handling so drained anchors keep a dimmed core (effects/light shut off but the display remains for later cleanup).
TeleportService.java: arrival sequences now trigger a shockwave helper (spawnArrivalShockwave) after the existing burst, adding a flash/explosion combo without damage.
TeleportMenuService.java: hover text coordinates render as (<world>: x, y, z), matching the v1.9 readability guideline.
plugin.yml, pom.xml, and dependency-reduced-pom.xml: version bumped to 1.0.1 (POMs now 1.0.1-SNAPSHOT) to track these fixes.

fix 1.11
AltarService now loads every stored waypoint via loadAllWaypoints, keeps dormant entries in memory, and drives visuals through a new updateVisualState helper so inactive cores shrink, lose light, but remain spawned. Activation now happens through attemptActivation (ender ingot + structure validation) while recharge handles mineral power-ups/NETHER_STAR infinities without re-validating the build. Charge consumption refuses to start when an altar is dormant, preserves the waypoint entry, and flips visuals/DB state when charges hit zero so dormant anchors stay available as destinations.
WorldAnchorListener routes interactions by item: ender ingots trigger activation (with protection against wasting them on already-active anchors), mineral blocks call recharge, and other interactions are blocked until activation completes. A shared consumeItem helper handles survival-only item removal, and right-click guidance now tells players to activate before trying to rename/open menus.
Teleport flow (TeleportService, TeleportMenuService, CharmCommand) enforces that players stand on/in the altar block before /charm travel proceeds, prevents dormant origins from starting rituals, adjusts readiness indicators in the teleport book to require both charge and pearl fuel, and removes redundant “无法开始传送” spam so only the precise failure reason is shown.
UI polish: /charm list now reports “已登记的世界锚点”, and dormant anchors continue to appear in menus/command lookups with their remaining charges (possibly “0”/“∞”).

fix 1.12
Updated config.yml with the new altar.backfire-power and teleport.arrival-radius defaults (plus inline docs) so server owners can tune blast damage and landing search size without touching code.
Extended PluginConfig to expose getBackfirePower() and getArrivalRadius(), then consumed those values in AltarService (configurable explosion power) and TeleportService (arrival search now honors the configured radius, clamped to ≥1).
Reworked the teleport book (TeleportMenuService): first page keeps the status header but now fits up to four entries, while later pages drop the header and list up to six destinations each, so players see far more anchors per book open.

fix 1.13
Added the new config knobs in config.yml: teleport.interaction-radius plus teleport.menu.first-page-entries/other-page-entries, and wired them through PluginConfig so they’re available at runtime alongside the existing arrival/backfire values.
AltarService now exposes findNearestActiveAnchor and isWithinInteractionRange, both honoring the configured radius (horizontal square around the lodestone, +5 vertical). These helpers drive the updated /charm workflows and also gate TeleportService.beginTeleport, replacing the older “must stand on the core block” rule.
TeleportMenuService takes an AetherGatePlugin reference so it can size every book page from config (first page vs subsequent pages) while still showing the status header only once; AetherGatePlugin was updated to pass this.
CharmCommand now relies entirely on the new range-aware detection: /charm book, /charm travel, and /charm debugtp all look for the nearest registered anchor within the allowed zone (or re-use the locked-in block only if the player remains nearby), and the user feedback includes the current radius.
TeleportService simply reuses AltarService.isWithinInteractionRange instead of the old rigid check, so any origin provided by the command must still be near the player before the ritual starts.