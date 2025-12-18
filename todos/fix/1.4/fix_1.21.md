这里是针对 1.21 版本的修复和改进计划。

你的需求涉及三个核心问题：

1.  **功能扩充**：添加 `END_ROD` (末地烛) 支持。
2.  **物品隔离**：防止“末影锭”被当作普通“铁锭”参与原版合成（如合成铁桶、铁镐等）。
3.  **配方系统优化**：修复配方书显示和自动填充的问题。

-----

### 1\. 修改 `AltarMaterialSet.java` (增加末地烛支持)

只需在 `LIGHT_BLOCKS` 集合中添加 `Material.END_ROD` 即可。

**文件：** `plugin/src/main/java/cn/lingnc/aethergate/altar/AltarMaterialSet.java`

找到 `LIGHT_BLOCKS` 的定义，修改如下：

```java
    private static final Set<Material> LIGHT_BLOCKS = EnumSet.of(
            Material.LANTERN,
            Material.SOUL_LANTERN,
            Material.GLOWSTONE,
            Material.SEA_LANTERN,
            Material.PEARLESCENT_FROGLIGHT,
            Material.VERDANT_FROGLIGHT,
            Material.OCHRE_FROGLIGHT,
            Material.SHROOMLIGHT,
            Material.END_ROD // [新增] 支持末地烛
    );
```

-----

### 2\. 新增 `CraftingProtectionListener.java` (隔离末影锭)

这是解决“末影锭被当做铁锭使用”的关键。我们需要监听合成准备事件，如果发现配方原料里混入了“末影锭”，但合成出的结果不是我们插件的物品，就强制将合成结果设为空。

**新建文件：** `plugin/src/main/java/cn/lingnc/aethergate/listener/CraftingProtectionListener.java`

```java
package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.item.CustomItems;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public class CraftingProtectionListener implements Listener {

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        // 获取合成矩阵中的所有物品
        ItemStack[] matrix = event.getInventory().getMatrix();
        boolean containsEnderIngot = false;

        // 1. 检查原料中是否包含末影锭
        for (ItemStack item : matrix) {
            if (item != null && CustomItems.isEnderIngot(item)) {
                containsEnderIngot = true;
                break;
            }
        }

        // 如果不含末影锭，则无关，直接返回
        if (!containsEnderIngot) {
            return;
        }

        // 2. 获取当前合成的配方结果
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            // 如果没有配方结果，但原料有末影锭，保险起见设为 Air
            event.getInventory().setResult(null);
            return;
        }

        ItemStack result = recipe.getResult();

        // 3. 判断结果是否为本插件的合法产物
        // 合法产物 1: 世界锚点 (World Anchor)
        // 合法产物 2: 末影珍珠 (末影锭分解回珍珠)
        boolean isValidResult = CustomItems.isWorldAnchor(result) ||
                                (result.getType() == Material.ENDER_PEARL && result.getAmount() == 9);

        // 4. 如果原料含末影锭，但结果不是合法产物（例如变成了铁镐），则禁止合成
        if (!isValidResult) {
            event.getInventory().setResult(null);
        }
    }
}
```

*注意：需要在 `AetherGatePlugin.java` 中注册这个新的 Listener。*

-----

### 3\. 修复配方书问题 (Recipe Book Fix)

**原因分析：**

1.  **配方解锁**：Minecraft 的配方书机制要求玩家必须先“解锁”配方，才能在书中看到。默认情况下，玩家获得铁锭会解锁铁制品，但获得末影锭不会自动解锁你的自定义配方。
2.  **NBT 严格匹配**：你使用了 `RecipeChoice.ExactChoice`。这在逻辑上是正确的（防止普通铁锭合成锚点），但客户端的配方书对带 NBT 的材料支持非常差。它经常无法识别“玩家背包里的末影锭”就是“配方里要求的那个 NBT 物品”，导致无法点击自动填充。

**修复方案：**

1.  **自动解锁**：玩家进服时，自动给予配方。
2.  **降低配方书显示的严格度**：这是一个权衡。为了让配方书**好用**，我们通常在注册配方时使用 `MaterialChoice` (只看材质)，但在 `PrepareItemCraftEvent` 中（上面的监听器）进行**严格校验**。

**步骤 A：修改 `RecipeRegistry.java`**

我们将配方注册改为使用 `MaterialChoice`（这样配方书就能识别背包里的物品了），然后依赖我们在**步骤 2** 中写的保护逻辑来防止普通铁锭被滥用。

修改 `registerWorldAnchorRecipe` 方法：

```java
    private static void registerWorldAnchorRecipe(AetherGatePlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "world_anchor");
        ShapedRecipe recipe = new ShapedRecipe(key, CustomItems.createWorldAnchorItem());
        recipe.shape("DID", "LBL", "DOD");
        recipe.setIngredient('D', Material.DIAMOND);

        // [修改]：不再使用 ExactChoice，改用 MaterialChoice。
        // 这样配方书就能高亮显示了。
        // 安全性由 CraftingProtectionListener 保证（它会检查那个 'I' 必须是 EnderIngot）。
        // 但是！我们需要反向检查：防止玩家用普通铁锭合成锚点。
        recipe.setIngredient('I', Material.IRON_INGOT);

        recipe.setIngredient('L', Material.LAPIS_BLOCK);
        recipe.setIngredient('B', Material.LODESTONE);
        recipe.setIngredient('O', Material.CRYING_OBSIDIAN);
        Bukkit.addRecipe(recipe);
    }
```

**步骤 B：升级 `CraftingProtectionListener` 以防止普通铁锭合成锚点**

因为我们放宽了注册条件，现在需要手动加强检查。在刚才的 `CraftingProtectionListener` 中添加逻辑：

```java
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        Recipe recipe = event.getRecipe();
        if (recipe == null) return;
        ItemStack result = recipe.getResult();

        // --- 逻辑 A: 防止末影锭被用于原版合成 (之前的代码) ---
        boolean containsEnderIngot = false;
        for (ItemStack item : matrix) {
            if (item != null && CustomItems.isEnderIngot(item)) {
                containsEnderIngot = true;
                break;
            }
        }

        // 如果是我们的物品合成 (锚点 或 锭分解)
        boolean isPluginRecipe = CustomItems.isWorldAnchor(result) ||
                                (result.getType() == Material.ENDER_PEARL && result.getAmount() == 9);

        if (containsEnderIngot && !isPluginRecipe) {
            event.getInventory().setResult(null);
            return;
        }

        // --- 逻辑 B: [新增] 防止普通铁锭合成世界锚点 ---
        if (CustomItems.isWorldAnchor(result)) {
            // 世界锚点中间必须是末影锭 (假设配方中间是索引 4)
            // 形状: DID / LBL / DOD -> 索引 4 是中间的 B(Lodestone)?
            // 等等，你的配方形状是:
            // 0 1 2 (D I D) -> 1 是 I
            // 3 4 5 (L B L)
            // 6 7 8 (D O D)
            // 所以我们要检查索引 1 的物品

            ItemStack ingotSlot = matrix[1]; // 对应第一行中间的 'I'
            if (ingotSlot == null || !CustomItems.isEnderIngot(ingotSlot)) {
                // 如果中间那个不是末影锭（比如是普通铁锭），则禁止合成
                event.getInventory().setResult(null);
            }
        }
    }
```

**步骤 C：自动解锁配方**

在 `WorldAnchorListener.java` 或新建一个 `JoinListener` 中添加：

```java
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        NamespacedKey anchorKey = new NamespacedKey(AetherGatePlugin.getInstance(), "world_anchor");
        NamespacedKey ingotKey = new NamespacedKey(AetherGatePlugin.getInstance(), "ender_ingot_craft");
        NamespacedKey uncraftKey = new NamespacedKey(AetherGatePlugin.getInstance(), "ender_ingot_uncraft");

        player.discoverRecipe(anchorKey);
        player.discoverRecipe(ingotKey);
        player.discoverRecipe(uncraftKey);
    }
```

-----

### 4\. 最后整合：修改 `AetherGatePlugin.java`

别忘了注册新的监听器。

```java
// ... existing imports
import cn.lingnc.aethergate.listener.CraftingProtectionListener; // 导入

public class AetherGatePlugin extends JavaPlugin {
    // ...
    @Override
    public void onEnable() {
        // ... existing code

        // 注册合成保护监听器
        getServer().getPluginManager().registerEvents(new CraftingProtectionListener(), this);

        // 如果你把 JoinEvent 写在了 WorldAnchorListener 里，就不用额外注册了
        // 如果写了新的 Listener 类，记得在这里 registerEvents

        getLogger().info("AetherGate enabled");
    }
    // ...
}
```

### 总结

1.  **末地烛**：已加入列表。
2.  **末影锭隔离**：
      * 通过 `CraftingProtectionListener`，如果配方里有末影锭但结果不是锚点/珍珠，**禁止合成**。
      * 通过同样的 Listener，如果结果是锚点但原料是普通铁锭，**禁止合成**。
3.  **配方书修复**：
      * 将 `RecipeRegistry` 里的 `ExactChoice` 降级为 `Material.IRON_INGOT`，让客户端能识别配方。
      * 配合 `PlayerJoinEvent` 里的 `discoverRecipe`，确保玩家进服就能看到配方。
      * 安全性由步骤 2 的后端逻辑死死守住，前端显示变得丝滑。