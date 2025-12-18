这是一个非常典型的**执行时序（Execution Order）**问题。

根据你提供的代码，**根本原因极有可能是：代码执行顺序反了。**

在 `AltarService.java` 中，你**先**制造了爆炸（这瞬间玩家就死了，触发了死亡事件），**然后**才给玩家贴上“被献祭”的标签。当 `DeathMessageListener` 监听到死亡时，玩家身上还没有标签，所以它直接忽略了，导致显示原版爆炸消息。

下面是给开发者的排查指南、调试代码方案，以及最终的修复建议。

---

### 🔍 第一部分：问题分析（给开发者看）

**目前的逻辑流程（错误的）：**

1. 代码执行 `world.createExplosion(...)`。
2. **Minecraft 内部逻辑**：爆炸造成伤害 -> 玩家血量归零 -> **触发 `PlayerDeathEvent**`。
3. **插件监听器**：`DeathMessageListener` 启动 -> 检查 `hasMetadata` -> **返回 False**（还没贴标签）-> **显示原版消息**。
4. 代码继续往下走 -> 执行 `markSacrificeVictims(...)` -> 给玩家尸体（或重生中的玩家）贴上标签。
5. **结果**：消息未变，标签白贴了。

**期望的逻辑流程（修正后）：**

1. 先执行 `markSacrificeVictims(...)`（先贴标签）。
2. 再执行 `world.createExplosion(...)`（再炸死）。
3. **触发 `PlayerDeathEvent**` -> 检查 `hasMetadata` -> **返回 True** -> **修改消息成功**。

---

### 🛠️ 第二部分：调试方案 (使用现有的 Debug 命令)

为了验证上述推论，我们需要在关键节点添加日志。请利用 `AetherGatePlugin.isDebugEnabled(uuid)` 来控制日志输出，避免刷屏。

#### 1. 修改 `DeathMessageListener.java`

在监听器里打印日志，看看死亡瞬间到底有没有读取到数据。

```java
// 在 onPlayerDeath 方法开头插入
if (AetherGatePlugin.getInstance().isDebugEnabled(player.getUniqueId())) {
    AetherGatePlugin.getInstance().getLogger().info("[Debug] 玩家 " + player.getName() + " 死亡。");
    boolean hasMeta = player.hasMetadata(SACRIFICE_META);
    AetherGatePlugin.getInstance().getLogger().info("[Debug] 是否持有献祭标签: " + hasMeta);
    if (hasMeta) {
         // 只有当有标签时，才读取过期时间看看
         List<MetadataValue> values = player.getMetadata(SACRIFICE_META);
         AetherGatePlugin.getInstance().getLogger().info("[Debug] 标签元数据数量: " + values.size());
    }
}

```

#### 2. 修改 `AltarService.java`

在标记受害者的方法里添加日志，确认标记发生的时间点。

```java
// 在 markSacrificeVictims 方法中
private void markSacrificeVictims(Location center, double radius) {
    if (center == null || center.getWorld() == null) return;
    long expiresAt = System.currentTimeMillis() + 3000;

    center.getWorld().getNearbyPlayers(center, radius).forEach(player -> {
        // --- 添加调试日志 ---
        if (plugin.isDebugEnabled(player.getUniqueId())) {
            plugin.getLogger().info("[Debug] 正在标记玩家: " + player.getName() + " 为献祭品");
        }
        // ------------------
        player.setMetadata(SACRIFICE_META, new FixedMetadataValue(plugin, expiresAt));
        Bukkit.getScheduler().runTaskLater(plugin, () -> player.removeMetadata(SACRIFICE_META, plugin), 60L);
    });
}

```

#### 3. 执行调试

1. 进游戏，输入 `/charm debug` 开启调试模式。
2. 故意触发祭坛炸膛。
3. **观察控制台顺序**：
* **如果是 Bug 状态**，你会先看到 `[Debug] 玩家 xx 死亡`，然后看到 `[Debug] 是否持有献祭标签: false`，最后才看到 `[Debug] 正在标记玩家...`。



---

### ✅ 第三部分：修复方案（最好的解决办法）

不需要复杂的逻辑重构，只需要调整 `AltarService.java` 中 **两行代码的顺序**。

**文件：** `plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java`
**方法：** `backfire`

**修改前 (Current Code - BUG IS HERE):**

```java
// Line 254 in your provided file
world.createExplosion(loc, power, false, false); // 1. 先爆炸 (导致死亡事件立即触发)
Location center = loc.clone().add(0.5, 0.5, 0.5);
markSacrificeVictims(center, 6.0); // 2. 后标记 (此时监听器已经跑完了)

```

**修改后 (Fixed Code):**

```java
// ... 省略清理方块代码 ...

// 1. 准备中心点
Location center = loc.clone().add(0.5, 0.5, 0.5);

// 2. 【关键】先给范围内的倒霉蛋贴上标签
markSacrificeVictims(center, 6.0);

// 3. 【关键】然后再制造爆炸
// 这样当爆炸造成伤害触发 DeathEvent 时，玩家身上已经有标签了
world.createExplosion(loc, power, false, false);

// ... 后续特效代码 ...

```

### 总结

你不需要在这个阶段重写 `DeathMessageListener`（虽然之前我给你的 Component 优化方案是好的，建议保留），当前的核心问题纯粹是 **“先开枪后瞄准”** 的时序错误。

请开发者**交换那两行代码的顺序**，问题即可解决。