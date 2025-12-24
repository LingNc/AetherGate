# AetherGate

English | [简体中文](README_zh.md)

AetherGate is an immersive teleportation plugin designed for Minecraft Paper servers. It moves away from command-based teleportation (such as `/warp` or `/tpa`) and introduces a balanced system based on altars, resource consumption, and visual effects.

## Usage
### Crafting
Adds two new items: Ender Ingot and World Anchor.
![Ender Ingot Recipe](assert/末影锭配方-0.png)
![World Anchor Recipe](assert/世界锚点配方-0.png)

### Construction
By default, please use any stone brick variants and their corresponding stairs (except for Quartz Bricks, which correspond to Quartz Stairs) to build the altar.

Construct a 5x5 base with pillars at the four corners, each 3 blocks high. Place a light-emitting block on top of each pillar, and place outward-facing stairs at the bottom of each pillar. In the exact center, place a Barrel at the bottom (used to store teleportation consumables: Ender Pearls or Ender Ingots), and place the 'World Anchor' block immediately above it.

Example:
![Construction Example](assert/效果展示.png)

### Activation
Right-click the central 'World Anchor' with an Ender Ingot to activate it.
**Note**: If constructed incorrectly, an explosion will occur.

### Charging
Right-click the 'World Anchor' with mineral blocks (e.g., Copper Block, Iron Block, etc.) to replenish durability. Charging with a Nether Star grants infinite usage. Higher-tier mineral blocks provide more durability per block.

### Consumption
Each teleportation consumes Ender Pearls (per player, per trip). If no pearls are detected anywhere, the teleportation will be refused. The altar will prioritize consuming Ender Ingots or Ender Pearls from the Barrel beneath the 'World Anchor'. If none are found there, it will search for pearls in containers within the altar's 5x5 range (prioritizing Ender Pearls; if none are found, it will automatically decompose Ender Ingots). Finally, if still not found, it will look for pearls in the player's inventory to consume.

### Teleportation
Right-click the central Conduit to open the book GUI. It displays the current anchor's name, remaining teleportation charges, charging status, and available destinations. Click the corresponding teleport button to travel. During the waiting period before a successful teleport, you can open the altar interface at any time to click "Cancel Teleport."

### Naming
Right-click the 'World Anchor' with a renamed Name Tag to change the current anchor's name for easier identification during teleportation.

### Depletion
If charges are not replenished in time, after the final teleportation, the altar will dim and the light-emitting blocks at the four corners will be destroyed. However, the altar can still receive teleports from other altars. Please replace the light-emitting blocks before initiating a teleport from this altar again; otherwise, an explosion will occur due to the incomplete structure.

### Features
Since v1.6.0, teleporting players will lock onto unowned entities or the player's pets within the altar range to teleport them along. Each additional entity increases the cost by one Ender Pearl, though dropped items do not consume pearls.

## License
This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.