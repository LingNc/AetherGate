### 核心问题分析

1. **Bug 原因**：你提供的 `PlayerDeathEvent` 源码显示，`setDeathMessage(String)` 已被标记为 `@Deprecated`（过时）。现代 Paper/Spigot 服务端内部优先处理 `Component`（组件）类型的消息。如果插件还在用旧的 String 方法，可能会被服务端的原生处理逻辑或其他插件覆盖，或者因为序列化格式不对导致显示失败。
2. **逻辑缺失**：目前的逻辑是“只要有标签就显示献祭成功”，缺少了对“真正死因”的判断。

---

### 开发者调整计划书 (Developer Adjustment Plan)

#### 阶段一：修复显示 Bug (迁移至 Modern Component API)

**目标**：解决死亡消息不显示或显示为原版爆炸消息的问题。
**依据**：根据你提供的 `PlayerDeathEvent.class`，我们需要使用 `deathMessage(Component)` 方法，而不是 `setDeathMessage(String)`。

* **行动点**：
1. 引入 `net.kyori.adventure.text` 包（Paper 核心自带）。
2. 不再使用字符串替换 (`replace`)，改用 Component 构建或 `MiniMessage`（如果服务器支持）。
3. **强制覆盖**：确保 `event.deathMessage(component)` 被调用。



#### 阶段二：实现“死因区分”逻辑

**目标**：区分“被祭坛炸死”和“献祭过程中死于意外”。
**逻辑流**：

*(图解：逻辑判断流程图 - 玩家死亡 -> 检查标签 -> 检查 getLastDamageCause ->如果是爆炸：显示献祭成功 -> 如果是其他：拼接“在献祭中”+原版死因)*

* **行动点**：
1. 获取 `player.getLastDamageCause()`。
2. 判断伤害类型 (`Cause`) 是否为 `BLOCK_EXPLOSION` 或 `ENTITY_EXPLOSION`。
3. **情况 A (炸死)**：使用配置中的 `sacrifice` 消息。
4. **情况 B (意外)**：获取原版生成的 `event.deathMessage()`，将其拼接到前缀后面。



#### 阶段三：配置文件更新

**目标**：为意外死亡提供配置支持。

* **config.yml 新增项**：
```yaml
death-messages:
  sacrifice-success: "%player% 被献祭了"
  sacrifice-fail-prefix: "%player% 在献祭仪式中 " # 后面会自动拼接原版死因，例如 "落地过猛"

```



---

### 建议修改的代码实现 (DeathMessageListener.java)

请开发者直接参考以下代码替换原有的 `DeathMessageListener` 类。这段代码使用了你提供的 API 结构，并解决了上述两个问题。

```java
package cn.lingnc.aethergate.listener;

import cn.lingnc.aethergate.AetherGatePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

public class DeathMessageListener implements Listener {

    private static final String SACRIFICE_META = "aethergate_sacrificed";

    // 提高优先级到 MONITOR 或 HIGHEST，确保我们是最后修改消息的人
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 1. 检查标签是否存在
        if (!player.hasMetadata(SACRIFICE_META)) {
            return;
        }

        // 2. 检查标签是否过期
        long now = System.currentTimeMillis();
        Long expiresAt = readExpiry(player.getMetadata(SACRIFICE_META));
        player.removeMetadata(SACRIFICE_META, AetherGatePlugin.getInstance()); // 立即移除，防止复活后逻辑污染

        if (expiresAt == null || expiresAt < now) {
            return;
        }

        // 3. 获取死因 (Last Damage Cause)
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        EntityDamageEvent.DamageCause cause = (lastDamage != null) ? lastDamage.getCause() : null;

        // 4. 获取配置
        String successTemplate = AetherGatePlugin.getInstance().getPluginConfig().getSacrificeDeathMessage(); // 对应 config: sacrifice
        // 建议在 config 添加一个新项，如果没有就用默认值
        String failPrefix = "在献祭仪式中";

        Component finalMessage;

        // 5. 分支逻辑判断
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
            cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {

            // === 情况 A: 被炸死的 (真正的献祭) ===
            // 修复 Bug: 使用 Component API 而不是 String
            if (successTemplate == null) successTemplate = "%player% 被献祭了";
            String text = successTemplate.replace("%player%", player.getName());

            // 将字符串转换为 Component (支持颜色代码 § 或 &)
            finalMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(text);

        } else {

            // === 情况 B: 意外死亡 (摔死、被咬死等) ===
            // 获取原版生成的消息 (例如 "Steve 落地过猛")
            Component originalDeathMsg = event.deathMessage();

            if (originalDeathMsg == null) {
                // 如果原版没消息，就兜底显示一个
                finalMessage = Component.text(player.getName() + " " + failPrefix + " 意外死亡", NamedTextColor.GRAY);
            } else {
                // 拼接逻辑: "%player% 在献祭仪式中" + " <原版消息去头>"
                // 但为了简单，我们可以直接构造: "玩家名" + "前缀" + "原版消息详情"
                // 这里采用一种更自然的方式： "前缀" + "原版消息" -> "在献祭仪式中 Steve 落地过猛"

                finalMessage = Component.text(failPrefix + " ", NamedTextColor.RED)
                        .append(originalDeathMsg);
            }
        }

        // 6. 最终设置 (修复 Bug 的关键：使用 deathMessage(Component) 方法)
        // 对应你提供的 class 文件中的 public void deathMessage(@Nullable Component deathMessage)
        event.deathMessage(finalMessage);
    }

    private Long readExpiry(List<MetadataValue> values) {
        for (MetadataValue value : values) {
            if (value == null) continue;
            try {
                return value.asLong();
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}

```

### 给开发者的关键提示 (Key Notes for Developer)

1. **关于 API 的使用**：
* 你提供的 `PlayerDeathEvent.class` 明确显示 `setDeathMessage(String)` 是 `@Deprecated` 的。
* 在上面的代码中，我使用了 `event.deathMessage(Component)`。这是修复显示 Bug 的关键。Spigot/Paper 服务端现在优先渲染这个字段。


2. **关于原版消息的拼接**：
* 原版消息 (`event.deathMessage()`) 通常是一个 `TranslatableComponent`（翻译组件）。
* 直接用 `append` 拼接到我们的前缀后面，客户端会自动根据玩家的语言设置显示（比如中文玩家看到“落地过猛”，英文玩家看到 "hit the ground too hard"）。这比硬编码字符串要好得多。


3. **配置建议**：
* 不需要大幅修改 Config 类，直接复用现有的，或者硬编码 `failPrefix` 也可以，看你对灵活度的要求。



按照这个方案修改，既能解决“不显示”的 Bug，又能完美实现你想要的“花式死法”通报。