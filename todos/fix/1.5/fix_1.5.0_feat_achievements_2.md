是的，这两处都需要根据 Minecraft 1.20.5+ (以及 Paper 1.21.4) 的 API 变更进行调整。

主要原因是 Minecraft 已经全面转向使用 **Adventure Components (组件)** 来处理文本，而不是以前的 String。Paper 正在逐步弃用旧的 String API。

以下是具体的修复方案：

### 1. 修复 `WorldAnchorListener.java` (ItemMeta 部分)

**问题**：`hasDisplayName()` 和 `getDisplayName()` 已过时（Obsolete/Deprecated）。
**解决**：使用 `hasCustomName()` 和 `customName()`，并使用序列化器将组件（Component）转换回字符串（String）。

**修改后的代码：**

你需要导入这个包：

```java
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

```

然后修改判定逻辑：

```java
// 修改前: if (meta != null && meta.hasDisplayName()) {
// 修改后:
if (meta != null && meta.hasCustomName()) {
    event.setCancelled(true);

    // 获取 Component
    var componentName = meta.customName();

    // 将 Component 转换为纯文本 String (去除颜色代码等格式，或者保留内容)
    // 如果你的 renameWaypoint 需要纯文本名字：
    String nameText = PlainTextComponentSerializer.plainText().serialize(componentName);

    // 调用重命名逻辑
    boolean renamed = altarService.renameWaypoint(block.getLocation(), nameText, player);

    if (renamed && player.getGameMode() != GameMode.CREATIVE) {
        consumeItem(player, held);
    }
}

```

> **解释**：
> * `hasCustomName()` 是新版 API 用来检查物品是否有自定义名字的方法。
> * `customName()` 返回的是 `Component` 对象。
> * `PlainTextComponentSerializer` 用于将复杂的组件文本提取为纯字符串，方便你存入数据库。
>
>

### 总结

1. **ItemMeta**: 必须改。旧的 `getDisplayName()` 可能会在未来版本被移除，且无法正确处理复杂的 Hex 颜色或 JSON 文本组件。请使用 `customName()` 配合序列化器。
2. 检查代码中未使用变量包导入等清理一下。