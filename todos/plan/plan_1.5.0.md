# 📝 以太之门 (AetherGate) v1.5.0 开发计划书

**版本目标**：v1.5.0 是一个“架构优化与内容扩展”版本。核心目标是实现配置文件的动态化、引入完整的成就系统（深度兼容 BACAP），并修复已知的经济逻辑 Bug。

**版本号**：`1.5.0`
**状态**：待开发

---

## 1. 核心架构重构：配置化 (Configuration Refactor)



---

## 2. 交互体验优化：命令重命名

* **主命令变更**：
* 将主命令注册名从 `/charm` 更改为 `/aether`。
* 保留 `/charm` 和 `/ag` 作为别名（Alias），以保证老玩家习惯不被强制改变。


* **实现**：修改 `plugin.yml` 中的 commands 节点。

---

## 3. 全新成就系统 (BACAP 深度集成)



---

## 4. 死亡信息自定义

* **需求**：当玩家死于祭坛反噬爆炸时，聊天栏显示特殊死亡信息。
* **逻辑**：
1. 当祭坛结构检测失败触发 `triggerBackfire()` 时，标记爆炸范围内的玩家（例如给玩家添加一个有效期 2 秒的 Tag 或 Metadata `ag_explosion_victim`）。
2. 监听 `PlayerDeathEvent`。
3. 如果玩家有该 Tag 且死因为 `BLOCK_EXPLOSION` 或 `ENTITY_EXPLOSION`。
4. 修改 `deathMessage` 为：`config.yml` 中定义的字符串（默认：“%player% 被献祭了”）。



---

## 5. Bug 修复：找零逻辑

* **当前 Bug**：消耗末影锭（价值9珍珠）进行传送（需消耗1珍珠）时，返还了9个珍珠，实际导致玩家“白嫖”了1个珍珠。
* **修正逻辑**：
* **变量定义**：`Cost` (路费, 例: 1), `Input` (投入, 例: 9)。
* **计算**：`Refund = Input - Cost` (例: 9 - 1 = 8)。
* **执行**：
1. 扣除玩家手中的末影锭（数量 -1）。
2. 向玩家背包添加 `Refund` 数量的末影珍珠。
3. **关键检查**：如果 `player.getInventory().addItem(...)` 返回了剩余物品（即背包已满装不下），必须将剩余的珍珠在玩家位置执行 `world.dropItem()`，防止吞物品。





---

## 6. 开发者自测清单 (Checklist)

在提交版本前，请确保通过以下测试：

* [ ] **配置测试**：删除 `config.yml` 重启服务器，新配置项是否生成？修改砖块材质为 `MOSSY_STONE_BRICKS`，旧的石砖祭坛是否失效？新的苔石砖祭坛是否生效？
* [ ] **命令测试**：`/aether` 和 `/charm` 是否都能打开帮助或执行命令？
* [ ] **BACAP测试**：
* [ ] 安装 BACAP 数据包：成就是否出现在“数据控”标签页下？
* [ ] 不安装 BACAP 数据包：插件是否报错？成就显示是否正常（独立页）？


* [ ] **找零测试**：背包放满杂物，只留一个末影锭。传送后，地上是否掉落了 8 个珍珠？
* [ ] **死亡测试**：站在祭坛上，让朋友破坏祭坛触发爆炸。被炸死后聊天栏显示什么？（需确保在爆炸伤害足够致死的情况下测试）。



## 1. 开发规范 (Development Standards)

所有开发活动必须严格遵守 `todos/rules.md` 中的规定。

### 1.1 版本号命名规范

遵循 Semantic Versioning：`主版本号.次版本号.修订号` (本次目标: **1.5.0**)

### 1.2 Git 分支策略

* **main**: 绝对稳定版本。
* **develop**: 开发主分支，所有新功能汇聚于此。
* **feat/xxx**: 功能特性分支，从 develop 切出，完成后合并回 develop。
* **fix/xxx**: 修复分支。

**标准工作流示例**:

```bash
# 1. 开始一个新功能
git checkout develop
git checkout -b feat/config-refactor

# 2. 开发并提交
git add .
git commit -m "feat: implement dynamic material config"

# 3. 合并回开发分支
git checkout develop
git merge feat/config-refactor
git branch -d feat/config-refactor

```

---

## 2. 需求清单与技术实现细节

### 任务 A: 配置文件重构 (Config Refactor)

* **分支名**: `feat/config-refactor`
* **需求**:
1. 将祭坛检测的硬编码方块移入 `config.yml`。
2. 实现 `config.yml` 的自动更新（保留旧配置，追加新默认值）。


* **实现细节**:
* 在 `config.yml` 中定义结构：
将原本硬编码在 Java 代码中的祭坛结构数据移至 `config.yml`，并实现配置文件的自动更新。

### 1.1 配置文件结构变更

在 `config.yml` 中新增 `altar-materials` 节点。开发者需实现读取此配置并缓存到内存（Set/Map）中，而非每次交互都读取文件。

```yaml
# 自动生成的 config.yml 示例
altar-materials:
  # 允许作为光源的方块
  lights:
    - SEA_LANTERN
    - GLOWSTONE
    - SHROOMLIGHT

  # 允许作为底座/柱子的方块，及其对应的楼梯映射
  # 格式为 "方块材质: 楼梯材质"
  blocks:
    STONE_BRICKS: STONE_BRICK_STAIRS
    MOSSY_STONE_BRICKS: MOSSY_STONE_BRICK_STAIRS
    QUARTZ_BRICKS: QUARTZ_STAIRS
    POLISHED_BLACKSTONE_BRICKS: POLISHED_BLACKSTONE_BRICK_STAIRS
    # 开发者需确保代码能动态读取这些键值对

```

### 1.2 自动更新逻辑

* **需求**：当插件更新且用户保留了旧的 `config.yml` 时，插件启动时必须自动补全缺失的新配置项（如上述的 `altar-materials`），但不能覆盖用户已修改的数值，也不能删除旧的配置项。
* **实现建议**：在 `onEnable` 中使用 `getConfig().setDefaults(...)` 和 `getConfig().options().copyDefaults(true)` 组合，最后 `saveConfig()`。


* **代码逻辑**:
* 创建一个 `AltarMaterialManager` 单例。
* 在 `onEnable` 时解析配置，将数据缓存到 `Set<Material>` 和 `Map<Material, Material>` 中以优化性能。
* 修改 `StructureChecker` 类，将硬编码的 `Material.STONE_BRICKS` 替换为从 Manager 获取校验。


* **配置更新**:
* 使用 `getConfig().options().copyDefaults(true);` 配合 `saveDefaultConfig();` 确保版本更新后新配置项会被写入磁盘。





### 任务 B: 核心交互更新 (Interaction Update)

* **分支名**: `feat/interaction-update`
* **需求**:
1. 更改主命令为 `/aether` (保留 `/charm` 别名)。
2. Bug 修复：末影锭消耗时的找零逻辑错误。


* **实现细节**:
* **命令**: 修改 `plugin.yml`，注册 `aether` 为主键，`charm` 移入 `aliases`。
* **Bug 修复 (`PearlCostManager`)**:
* *当前错误逻辑*: 消耗锭后直接返还9个珍珠，未扣除路费。
* *修正逻辑*:
```java
int cost = calculateCost(...); // 例如 1
int value = 9; // 锭的价值
int refund = value - cost; // 应找零 8
if (refund > 0) player.getInventory().addItem(new ItemStack(PEARL, refund));

```







### 任务 C: 成就系统与 BACAP 集成 (Achievements)

* **分支名**: `feat/achievements`
* **需求**:
1. 实现一系列自定义成就。
2. 兼容 BACAP 数据包，将成就挂载到“数据控” (Statistics) 标签页下。


* **技术分析**:
* **BACAP 锚点**: 根据提供的 `root.json`，BACAP 统计页面的根 ID 为 `blazeandcave:statistics/root`。
* **成就列表 (NamespacedKey 建议)**:
* `aethergate:root` (如果未安装 BACAP，作为根)
* `aethergate:obtain_anchor` (获得世界锚点)
* `aethergate:activate_altar` (首次激活)
* `aethergate:first_teleport` (首次传送)
* `aethergate:infinite_power` (获得无限耐久)
* `aethergate:builder_10` (建造10个祭坛) -> Parent: BACAP root
* `aethergate:sacrifice` (发生爆炸)




* **实现逻辑**:
1. **动态父级**:
* 在插件启动时，检测 `config.yml` 中的 `integration.bacap` 是否为 true。
* 如果开启，设置成就 JSON 的 `"parent": "blazeandcave:statistics/root"`。
* 如果关闭，设置 `"parent": "aethergate:root"`。


2. **进度统计**:
* 利用 `PersistentDataContainer` (PDC) 在玩家身上存储 `altars_built` (Integer)。
* 每次 `AltarService` 成功激活新祭坛时，`count++`。
* 检查阈值 (10, 50, 100) 并授予对应成就。


基于你提供的 BACAP `root.json`，我们将 AetherGate 的成就挂载到 BACAP 的 **Statistics (数据控)** 标签页下。

### 3.1 智能挂载逻辑 (Smart Parenting)

代码需在启动时检查服务器是否加载了 BACAP 数据包（检查是否存在 key 为 `blazeandcave:statistics/root` 的进度）。

* **情况 A：检测到 BACAP**
* AetherGate 的根成就 `aethergate:root` 的 `parent` 字段设为 `blazeandcave:statistics/root`。
* 显示图标建议：`END_CRYSTAL` 或 `LODESTONE`。
* 标题：“以太之门统计”。


* **情况 B：未检测到 BACAP**
* AetherGate 的根成就 `parent` 设为 `null`（或者原版的一个合适标签页），作为独立的标签页显示，避免报错。



### 3.2 成就树设计

所有成就应为 `TASK` 或 `GOAL` 类型，`show_toast` 设为 true。

1. **[根节点] 以太探索者**
* 触发条件：自动（或者获得任意 AetherGate 相关物品）。
* Parent: `blazeandcave:statistics/root`


2. **[进度 1] 空间锚点**
* 条件：第一次获得物品 `World Anchor` (世界锚点)。
* Parent: [根节点]


3. **[进度 2] 祭坛**
* 条件：第一次成功激活一个传送祭坛。
* Parent: [根节点]


4. **[进度 3] 跃迁**
* 条件：第一次成功使用传送功能。
* Parent: [根节点]


5. **[进度 4] 永恒能量**
* 条件：将一个祭坛的充能数增加到由配置定义的“无限”阈值（如 -1 或 9999）。
* Parent: [进度 3]


6. **[进度 5] 建筑师系列 (数据累计)**
* 此系列兼容 BACAP 风格，基于玩家 PDC (PersistentDataContainer) 中的计数器 `ag_altars_built`。
* **初级祭坛师**：累计建造 10 个祭坛。
* **资深祭坛师**：累计建造 50 个祭坛。
* **世界互联**：累计建造 100 个祭坛。
* *注意：这三个成就应并行排列在 [根节点] 下，或者线性解锁，请开发者自行判断 UI 美观度。*


7. **[隐藏进度] 献祭**
* 条件：玩家触发一次祭坛反噬爆炸（无论是否死亡）。
* 类型：`CHALLENGE` (挑战)。
* Parent: [根节点]




### 任务 D: 死亡信息优化 (Death Messages)

* **分支名**: `feat/death-messages`
* **需求**:
1. 被祭坛反噬爆炸炸死时，显示“xxxxx被献祭了”。


* **实现细节**:
* 在 `TeleportService` 触发 `triggerBackfire` (爆炸) 时：
* 获取爆炸范围内的玩家。
* 给玩家添加一个临时的元数据标签 (Metadata) `aethergate_sacrificed`，有效期 3 秒。


* 监听 `PlayerDeathEvent`:
```java
if (player.hasMetadata("aethergate_sacrificed")) {
    event.setDeathMessage(Component.text(player.getName() + " 被献祭了").color(NamedTextColor.DARK_RED));
    player.removeMetadata("aethergate_sacrificed", plugin);
}

```





---

## 3. 执行步骤 (Execution Steps)

请开发者按以下顺序执行：

1. **初始化**:
```bash
git checkout develop
git push -u origin develop

```


2. **开发 任务 A (Config)**:
```bash
git checkout -b feat/config-refactor develop
# ... coding ...
git checkout develop
git merge feat/config-refactor
git branch -d feat/config-refactor

```


3. **开发 任务 B (Interaction/Bug)**:
```bash
git checkout -b feat/interaction-update develop
# ... coding ...
git checkout develop
git merge feat/interaction-update
git branch -d feat/interaction-update

```


4. **开发 任务 D (Death Message)**:
*先做简单的死亡信息，再做复杂的成就。*
```bash
git checkout -b feat/death-messages develop
# ... coding ...
git checkout develop
git merge feat/death-messages

```


5. **开发 任务 C (Achievements)**:
*这是最复杂的部分，最后集成。*
```bash
git checkout -b feat/achievements develop
# ... coding ...
git checkout develop
git merge feat/achievements

```


6. **发布 v1.5.0**:
```bash
# 确保所有功能在 develop 上测试通过
git checkout main
git merge develop
git tag -a v1.5.0 -m "Release v1.5.0: 自定义与成就更新"
git push origin main --tags

```



---

## 4. 交付物检查项

* [ ] `config.yml` 包含新的 `altar-structure` 部分。
* [ ] 插件更新后，旧配置未丢失，新配置已追加。
* [ ] `/aether` 命令可用，`/charm` 仍可用。
* [ ] 消耗末影锭传送 1 格距离，背包正确增加了 8 个末影珍珠。
* [ ] 故意破坏祭坛导致爆炸炸死自己，死亡提示为“被献祭了”。
* [ ] (如果有 BACAP) 按下 `L` 键，在统计/数据控页面能看到 AetherGate 的成就分支。