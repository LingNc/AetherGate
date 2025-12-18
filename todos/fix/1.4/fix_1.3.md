这是一个详细的 **AetherGate 修复与变更技术文档 (v1.3)**。

这份文档解决了两个核心问题：

1.  **编译报错修复**：修正了 `SMOKE_LARGE` 在 1.21 API 中不存在的问题。
2.  **物品重构**：将“末影珍珠块”重构为“末影锭”，并修正了资源包的加载方式（采用 SilicaCraft 标准）。

------

# AetherGate 修复与变更说明书 (v1.3)

优先级: 紧急 (Blocker)

目标版本: Paper 1.21.4

依赖变更: 无

## 1. 紧急修复：编译错误 (Compilation Error)

错误原因:

在 Minecraft 1.21+ API 中，旧版的粒子枚举 Particle.SMOKE_LARGE 已被重命名/移除。

**修复文件:** `plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java`

修改操作:

定位到 backfire 方法 (约第 201 行)，将报错的粒子常量替换为新版名称。

-   **原代码 (错误):** `Particle.SMOKE_LARGE`
-   **新代码 (正确):** `Particle.LARGE_SMOKE`

Java

```
// AltarService.java

private void backfire(World world, Location loc, Player trigger, AltarValidationResult debugInfo) {
    // ...
    // 修改前: world.spawnParticle(Particle.SMOKE_LARGE, center, 160, 4.0, 2.0, 4.0, 0.02);
    world.spawnParticle(Particle.LARGE_SMOKE, center, 160, 4.0, 2.0, 4.0, 0.02);
    // ...
}
```

------

## 2. 核心物品重构：末影锭 (Ender Ingot)

变更说明:

放弃“末影珍珠块”的可放置方块属性，将其改为纯物品“末影锭”。

-   **基底材质:** `IRON_INGOT` (铁锭)
-   **功能:** 仅作为合成材料、投掷弹药(如果保留投掷功能)或货币。
-   **PDC 数据:** 继续使用 PersistentDataContainer 存储身份标识。

### 2.1 插件端代码修改

#### 文件 1: `CustomItems.java`

修改物品创建逻辑，适配 1.21.4 的 `editPersistentDataContainer` (最佳实践) 或继续使用 Meta。

Java

```
// plugin/src/main/java/cn/lingnc/aethergate/item/CustomItems.java

package cn.lingnc.aethergate.item;

import cn.lingnc.aethergate.AetherGatePlugin;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class CustomItems {

    // 使用新的 key 标识，或者沿用旧的 item_type
    private static final NamespacedKey KEY_TYPE = new NamespacedKey(AetherGatePlugin.getInstance(), "item_type");

    // ID 变更为 ender_ingot
    public static final String TYPE_ENDER_INGOT = "ender_ingot";
    public static final String TYPE_WORLD_ANCHOR = "world_anchor";

    private CustomItems() {
    }

    // 工厂方法重命名
    public static ItemStack createEnderIngot(int amount) {
        // 基底改为铁锭
        ItemStack stack = new ItemStack(Material.IRON_INGOT, amount);

        // 1. 设置 CustomModelData (字符串模式) - 关键！资源包识别全靠它
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(TYPE_ENDER_INGOT).build());

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // 2. 设置显示名称
            meta.displayName(Component.text("末影锭", NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.ITALIC, false));

            // 3. 设置 PDC 数据 (双重保险，且用于逻辑判断)
            meta.getPersistentDataContainer().set(KEY_TYPE, PersistentDataType.STRING, TYPE_ENDER_INGOT);

            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack createWorldAnchorItem() {
        // ... (保持世界锚点代码不变，除非你想把它也变成基于头颅或者其他物品) ...
        // 如果世界锚点保持 LODESTONE 方块属性，则不需要变动。
        return CustomItems.createWorldAnchorItem_OriginalLogic(); // 伪代码，保持原样
    }

    // 校验方法更新
    public static boolean isEnderIngot(ItemStack stack) {
        if (stack == null || stack.getType() != Material.IRON_INGOT) {
            return false;
        }
        // 优先检查 CustomModelData
        if (stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            CustomModelData cmd = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (cmd != null && cmd.strings().contains(TYPE_ENDER_INGOT)) {
                return true;
            }
        }
        // 降级检查 PDC
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        String type = meta.getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        return TYPE_ENDER_INGOT.equals(type);
    }

    // ... 辅助方法保持不变
}
```

#### 文件 2: `EnderPearlBlockListener.java`

操作: 直接删除此文件。

原因: 既然“末影锭”不再是方块，就不需要监听放置事件把它变成蘑菇柄了。它现在就像普通的铁锭一样，拿在手里只能看，不能放（除非你写了放置逻辑，但根据需求是纯物品）。

#### 文件 3: `RecipeRegistry.java`

更新配方以使用新的 `createEnderIngot`。

Java

```
// 修改配方逻辑
private static void registerEnderIngotRecipes(AetherGatePlugin plugin) {
    // 9 珍珠 -> 1 末影锭
    NamespacedKey craftKey = new NamespacedKey(plugin, "ender_ingot_craft");
    ShapedRecipe craft = new ShapedRecipe(craftKey, CustomItems.createEnderIngot(1));
    craft.shape("PPP", "PPP", "PPP");
    craft.setIngredient('P', Material.ENDER_PEARL);
    Bukkit.addRecipe(craft);

    // 1 末影锭 -> 9 珍珠
    NamespacedKey uncraftKey = new NamespacedKey(plugin, "ender_ingot_uncraft");
    ShapelessRecipe uncraft = new ShapelessRecipe(uncraftKey, new ItemStack(Material.ENDER_PEARL, 9));
    // 使用 RecipeChoice.ExactChoice 确保只有带 NBT 的末影锭能分解，普通铁锭不行
    uncraft.addIngredient(new RecipeChoice.ExactChoice(CustomItems.createEnderIngot(1)));
    Bukkit.addRecipe(uncraft);
}
```

------

### 2.2 资源包 (Resource Pack) 修改

请确保文件结构完全符合以下规范，采用 **覆盖原版定义 (Override Vanilla Definition)** 的方式。

**目录结构:**

Plaintext

```
resource_pack/
├── pack.mcmeta
├── pack.png
└── assets/
    ├── minecraft/
    │   └── items/
    │       └── iron_ingot.json  <-- 关键！覆盖原版铁锭定义
    └── aether_gate/
        ├── models/
        │   └── item/
        │       └── ender_ingot.json
        └── textures/
            └── item/
                └── ender_ingot.png  <-- 请确认这就是你做好的“末影锭”贴图
```

#### 1. 定义文件: `assets/minecraft/items/iron_ingot.json`

这是拦截器，告诉客户端：“如果有 `ender_ingot` 标签，就显示别的模型”。

JSON

```
{
  "model": {
    "type": "minecraft:select",
    "property": "minecraft:custom_model_data",
    "cases": [
      {
        "when": "ender_ingot",
        "model": {
          "type": "minecraft:model",
          "model": "aether_gate:item/ender_ingot"
        }
      }
    ],
    "fallback": {
      "type": "minecraft:model",
      "model": "minecraft:item/iron_ingot"
    }
  }
}
```

#### 2. 模型文件: `assets/aether_gate/models/item/ender_ingot.json`

标准的物品模型文件。

JSON

```
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "aether_gate:item/ender_ingot"
  }
}
```

#### 3. 贴图文件

请确保你的贴图文件名为 ender_ingot.png 并放置在 assets/aether_gate/textures/item/ 目录下。

注意：由于你提到之前的材质是“蓝黑色且有问题”，如果你已经重做了新的材质，请确保新文件替换进去。如果只是重命名，请确保文件名对应。

------

### 3. 开发检查清单 (Checklist)

1.  [ ] **修复 Java 报错:** `AltarService.java` 中的 `SMOKE_LARGE` 已替换为 `LARGE_SMOKE`。
2.  [ ] **清理代码:** 删除了 `EnderPearlBlockListener.java` 及其在主类中的注册代码。
3.  [ ] **更新物品工厂:** `CustomItems.java` 已更新为生产基于 `IRON_INGOT` 的 `ender_ingot`，并使用了 `CustomModelData` 字符串 API。
4.  [ ] **更新配方:** 确保合成表使用的是新物品。
5.  [ ] **资源包重构:** * 删除了旧的 `block` 相关模型和贴图。
    -   新增了 `minecraft/items/iron_ingot.json`。
    -   确认贴图路径为 `aether_gate/textures/item/ender_ingot.png`。