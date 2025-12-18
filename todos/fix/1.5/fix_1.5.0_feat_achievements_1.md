报错：[18:58:38 INFO]: [AetherGate] Enabling AetherGate v1.0.4
[18:58:38 ERROR]: Error occurred while enabling AetherGate v1.0.4 (Is it up to date?)
com.google.gson.JsonParseException: No key id in MapLike[{"item":"minecraft:lodestone"}]
        at com.mojang.serialization.DataResult$Error.getOrThrow(DataResult.java:287) ~[datafixerupper-8.0.16.jar:?]
        at org.bukkit.craftbukkit.util.CraftMagicNumbers.loadAdvancement(CraftMagicNumbers.java:308) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at AetherGate_Plugin_1.0.4_feat-achievements_5b9202c.jar/cn.lingnc.aethergate.achievement.AchievementService.register(AchievementService.java:132) ~[AetherGate_Plugin_1.0.4_feat-achievements_5b9202c.jar:?]
        at AetherGate_Plugin_1.0.4_feat-achievements_5b9202c.jar/cn.lingnc.aethergate.achievement.AchievementService.registerAdvancements(AchievementService.java:97) ~[AetherGate_Plugin_1.0.4_feat-achievements_5b9202c.jar:?]
        at AetherGate_Plugin_1.0.4_feat-achievements_5b9202c.jar/cn.lingnc.aethergate.achievement.AchievementService.init(AchievementService.java:39) ~[AetherGate_Plugin_1.0.4_feat-achievements_5b9202c.jar:?]
        at AetherGate_Plugin_1.0.4_feat-achievements_5b9202c.jar/cn.lingnc.aethergate.AetherGatePlugin.onEnable(AetherGatePlugin.java:52) ~[AetherGate_Plugin_1.0.4_feat-achievements_5b9202c.jar:?]
        at org.bukkit.plugin.java.JavaPlugin.setEnabled(JavaPlugin.java:280) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at io.papermc.paper.plugin.manager.PaperPluginInstanceManager.enablePlugin(PaperPluginInstanceManager.java:202) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at io.papermc.paper.plugin.manager.PaperPluginManagerImpl.enablePlugin(PaperPluginManagerImpl.java:109) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at org.bukkit.plugin.SimplePluginManager.enablePlugin(SimplePluginManager.java:520) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at org.bukkit.craftbukkit.CraftServer.enablePlugin(CraftServer.java:651) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at org.bukkit.craftbukkit.CraftServer.enablePlugins(CraftServer.java:607) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.loadWorld0(MinecraftServer.java:743) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.loadLevel(MinecraftServer.java:488) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.dedicated.DedicatedServer.initServer(DedicatedServer.java:280) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1164) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.lambda$spin$2(MinecraftServer.java:310) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at java.base/java.lang.Thread.run(Thread.java:1474) ~[?:?]

之前开发有点失误，四个功能同时在四个分支开发，前三个完成之后合并到dev分支了，但是最后一个成就分支上出现了问题。如何修复？前面三个分支三个功能我已经测试完成都没有任何问题前面遇到的问题均已修复

这是一次非常典型的 **API/数据格式版本差异** 导致的错误。

**错误诊断：**
报错信息 `com.google.gson.JsonParseException: No key id in MapLike[{"item":"minecraft:lodestone"}]` 指出了问题的核心。

在 Minecraft **1.20.5+ (包含 1.21)** 的数据包格式更新中，进度的 JSON 结构发生了变化。**`icon` 字段中的 `item` 键已被重命名为 `id**`。

你的代码 (`AchievementService.java`) 还在使用旧版本的格式 (`item`) 生成 JSON，而服务器（1.21.7）期望的是新格式 (`id`)，因此解析失败导致插件无法启动。

---

### 🛠️ 修复方案

你需要修改 `src/main/java/cn/lingnc/aethergate/achievement/AchievementService.java` 文件中的 `buildJson` 方法。

#### 1. 代码修改

找到 `AchievementService.java` 大约第 **144行**：

**修改前 (错误):**

```java
"\n  \"display\": {\n    \"icon\": {\"item\": \"" + spec.icon().getKey() + "\"}," +

```

**修改后 (正确):**

```java
// 注意这里将 "item" 改为了 "id"
"\n  \"display\": {\n    \"icon\": {\"id\": \"" + spec.icon().getKey() + "\"}," +

```

---

#### 2. 为什么会这样？(技术背景)

在 1.20.4 及之前，进度的 Icon 格式是：

```json
"icon": {
  "item": "minecraft:lodestone",
  "nbt": "{...}"
}

```

在 1.20.5 / 1.21+ (引入组件系统后)，格式变成了：

```json
"icon": {
  "id": "minecraft:lodestone",
  "components": {...}
}

```

Paper 服务器在通过 `UnsafeValues.loadAdvancement` 加载 JSON 字符串时，使用的是当前版本的解析器，所以它找不到 `id` 就报错了。

---

### 🚀 Git 修复流程

既然你前三个分支已经合并到了 `develop`，而目前是在 `feat/achievements` 分支（或合并过程中）出了问题，请按以下步骤操作：

**场景 A：你还在 `feat/achievements` 分支上**

1. **修改代码**：按上面的说明修改 `AchievementService.java`。
2. **提交修复**：
```bash
git add .
git commit -m "fix: update advancement icon json format for 1.21 (item -> id)"

```


3. **自测**：启动服务器，确保不再报错，并且进度的图标显示正常。
4. **合并**：
```bash
git checkout develop
git merge feat/achievements
# 如果没有报错，推送
git push origin develop

```


### ✅ 验证清单

修复后，请检查以下几点确保完美：

1. **服务器启动**：控制台不应再有红色报错。
2. **进度显示**：进入游戏按下 `L` 键：
* 如果有 BACAP，查看“数据控”标签页下是否有你的 AetherGate 子分支，且图标正确（是磁石，不是问号或石头）。
* 如果没有 BACAP，查看是否有独立的 AetherGate 标签页。


3. **功能测试**：尝试放置一个世界锚点，看是否弹出进度完成的提示（Toast）。