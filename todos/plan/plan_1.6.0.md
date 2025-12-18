# AetherGate v1.6.0 计划书
### 第一部分：原理与实现逻辑探讨

在动手写代码前，我们需要解决以下几个核心的技术与逻辑问题：

#### 1. 实体层级与“根实体” (Root Entity)

Minecraft 中的实体存在层级关系（例如：玩家骑着猪，猪在船上）。

* **原则**：我们不能只传送乘客，必须传送最底层的载具（Root Entity）。
* **实现**：扫描范围内的实体时，对于每一个实体，都要获取其 `getRootVehicle()`。
* 如果玩家在船上，实际被锁定的应该是“船”。船传送走了，玩家作为乘客会自动跟随（Paper/Spigot 1.21+ 处理跨界传送相对成熟，但仍需注意）。
* **去重**：如果船上有两个玩家，扫描时会扫到两个人，但最终只能算作传送“1艘船”。



#### 2. 锁定机制 (Locking)

目前代码中 `TeleportService` 只锁定了 `Player` (UUID)。

* **变更**：需要引入一个全局的 `Set<UUID> lockedEntities`。
* **逻辑**：
1. 玩家点击传送按钮。
2. 扫描 5x5 范围。
3. 筛选出所有符合条件的实体（未被锁定的）。
4. **立即**将这些实体的 UUID 加入 `lockedEntities`。
5. 如果有其他玩家此时也点击传送，扫描时发现这些实体已在 `lockedEntities` 中，则跳过，避免“争抢”导致的卡顿或崩服。



#### 3. 归属权判定 (Ownership & Filtering)

这是本次更新最复杂的逻辑，需要严格的 `if-else` 判定：

* **绝对排除**：
* 其他玩家（除非他们也在自己的传送流程中，否则不应被强制带走，防止恶意传送杀人）。
* 被其他玩家骑乘的载具（通过 `entity.getPassengers()` 检查是否包含其他玩家）。
* 属于其他玩家的驯服生物（检查 `Tameable.getOwner()`）。


* **免费白名单**：
* 属于**当前发起传送者**的驯服生物（Owner == Initiator）。
* 掉落物 (`Item` 实体)。


* **收费名单**：
* 无主的生物（牛、羊、僵尸）。
* 无主的载具（空的船、矿车）。
* 属于当前发起者的载具（如马），虽然属于玩家，但按你的描述，除了“宠物”，其他实体需要消耗珍珠。**这里需要确认：你的马是否算作宠物？** (通常 Minecraft 插件逻辑中，马匹算作 Tameable，如果不消耗珍珠，需要归类进免费组)。
* *建议策略*：如果是 `Tameable` 且主人是发起者，一律免费。



#### 4. 计费系统 (Cost Calculation)

目前的 `PearlCostManager` 是“尝试扣除(tryConsume)”。

* **变更**：需要改为“预计算(Pre-calculate)”。
1. 统计所有需要收费的实体数量 N。
2. 检查祭坛桶 + 玩家背包是否有 N+1（玩家本体）个珍珠。
3. 如果不够，提示“珍珠不足，需 X 个”，并取消传送。



#### 5. 视觉特效适配

目前的粒子效果是固定的高度。

* **变更**：读取 `entity.getHeight()` 和 `entity.getWidth()`。
* **效果**：粒子螺旋上升的高度应动态适配实体的高度（例如末影人很高，鸡很矮）。

---

### 第二部分：AetherGate v1.6.0 开发文档

**文档版本**：v1.6.0
**目标读者**：插件开发者
**前置要求**：基于现有的 `TeleportService.java` 和 `PearlCostManager.java` 进行重构。

#### 1. 功能需求概述

实现以祭坛为中心的区域群体传送。传送启动时锁定范围内有效实体，传送结束时将所有锁定实体一同移动到目标位置，并保持相对状态或重组骑乘关系。

#### 2. 核心类变更说明

##### A. `TeleportService.java` (重构核心)

**新增成员变量：**

```java
// 用于防止多个祭坛同时抓取同一个实体
private final Set<UUID> globalLockedEntities = ConcurrentHashMap.newKeySet();

```

**方法变更：`beginTeleport**`
在该方法内，不仅要检查玩家，还要执行“实体扫描与预锁定”流程。

**新增逻辑流程：实体收集 (Entity Collection)**

1. **扫描区域**：以祭坛中心为原点，`interaction-radius` (5x5) 为范围，获取所有实体 (`world.getNearbyEntities(...)`)。
2. **根节点提取**：对于每个实体，调用 `entity.getRootVehicle()` 获取其根载具。如果实体没有骑乘，根就是它自己。
3. **去重**：使用 `HashSet` 存储待传送的根实体，避免重复计算（例如船上的两个乘客都会指向同一艘船）。
4. **过滤 (Filtering)**：
* **跳过**：`BlockDisplay`, `ItemDisplay`, `Interaction`, `Marker` (装饰性实体)。
* **跳过**：已经在 `globalLockedEntities` 中的实体。
* **跳过**：`Player` 实体（如果是发起者自己除外；如果是其他玩家，则忽略）。
* **跳过**：`Tameable` 且 Owner 是**其他玩家**的生物。
* **跳过**：载具的乘客列表 (`getPassengers`) 中包含**其他玩家**的实体。


5. **分类与计费预估**：
* `List<Entity> freeTargets`: 包含掉落物 (`Item`)、发起者的宠物 (`Tameable` && Owner == Player)。
* `List<Entity> paidTargets`: 其他杂项生物、无主载具、未驯服生物。
* **总费用** = `paidTargets.size()` (每只1珍珠) + 1 (玩家基础费用)。


6. **资源检查**：调用 `PearlCostManager.checkHasEnough(location, player, totalCost)`。如果不足，发送消息并 `return false`。

**内部类变更：`TeleportTask**`

* **构造函数**：接收 `List<Entity> targets`。
* **运行逻辑 (`run`)**：
* **锁定**：初始化时，将所有 targets 的 UUID 加入 `globalLockedEntities`。
* **特效**：遍历 targets。
* 对每个实体，根据 `entity.getBoundingBox().getHeight()` 生成对应的粒子包围圈。
* *优化*：为了性能，不要每一 tick 都对所有实体生成大量粒子。可以轮询或降低非玩家实体的粒子密度。


* **完整性检查**：在 warmup 期间，如果实体死亡、被其他人强行传送走（如指令），则从 list 中移除。
* **新进入实体**：`run` 方法中**不**扫描新实体。只处理构造函数传入的“快照”列表。


* **传送执行 (`executeTeleport`)**：
* **扣费**：执行 `PearlCostManager.consume(..., totalCost)`。
* **传送序列**：
1. 先传送玩家。
2. 遍历 targets。
3. 使用 `entity.teleportAsync(arrivalLocation)` (推荐异步) 或 `teleport`。
4. *注意*：Paper 1.21 处理带着乘客的载具传送通常会自动处理，但建议在传送后检查乘客是否脱落。如果脱落，需要重新 `addPassenger`。
5. **位置抖动**：为了防止实体卡死（Entity Cramming），建议对非玩家实体在目标点周围 0.5~1.0 格内增加微小的随机偏移 `arrival.add(randomX, 0, randomZ)`。




* **清理 (`cleanup`)**：
* 将所有 targets 从 `globalLockedEntities` 中移除。



##### B. `PearlCostManager.java` (逻辑升级)

**新增方法：**

```java
/**
 * 预检查是否有足够的资源，不实际扣除
 */
public boolean hasEnoughPearls(Location anchorLoc, Player player, int requiredAmount) {
    // 逻辑：扫描桶 + 扫描玩家背包
    // 返回 count >= requiredAmount
}

/**
 * 实际扣除指定数量的珍珠
 */
public boolean consumePearls(Location anchorLoc, Player player, int amount) {
    // 逻辑：优先扣桶，再扣背包，直到扣除 amount 个
    // 支持末影锭逻辑（1锭 = 9珍珠，如果需要扣除1个，由于锭不能拆分放入背包，
    // 建议逻辑：优先扣珍珠。如果必须扣锭，消耗1锭，并在该容器/位置返还 9 - cost 个珍珠）
}

```

##### C. `CustomItems.java` / `AltarMaterialSet.java`

* 无需变更，复用现有逻辑。

#### 3. 详细业务逻辑规范 (Business Logic Rules)

1. **宠物判定逻辑**：
* 检查 `entity instanceof Tameable`。
* 如果 `((Tameable) entity).isTamed()` 且 `((Tameable) entity).getOwner().getUniqueId().equals(player.getUniqueId())`。
* **特殊情况**：狐狸 (`Fox`)。狐狸是 `Trusted` 不是 `Tamed`。
* 检查 `((Fox) entity).getFirstTrustedPlayer()` 是否等于玩家。


* **结果**：判定为“自有生物”，加入 `freeTargets`，**Cost = 0**。


2. **骑乘保护逻辑**：
* 场景：玩家 A 骑着马。
* 操作：玩家 A 点击传送书。
* 处理：系统识别到马是 `RootVehicle`。马属于玩家控制（即便马没驯服，只要玩家骑着）。
* 费用：如果马是驯服且属于 A，Cost=0；否则 Cost=1。马和玩家一起传走。
* 场景：玩家 B 在玩家 A 的马旁边点击传送书。
* 处理：系统扫描到马。检查 `马.getPassengers()`。发现包含玩家 A。
* 结果：**跳过**该马。不能传送其他玩家正在使用的载具。


3. **掉落物逻辑**：
* `entity instanceof Item`。
* 直接加入 `freeTargets`。
* Cost = 0。


4. **安全保护 (Safety)**：
* 如果传送目的地是悬空的（如空岛祭坛），对于玩家我们通常会找安全落点。对于生物，必须确保落点有方块支撑，否则猪传过去就摔死了。
* 复用 `TeleportService.findArrivalSpot`，但在放置实体时，确保不会把它们放进墙里。



#### 4. 视觉效果规范

* **玩家**：保持现有 `ENCHANT` (附魔台符文) + `END_ROD` 效果。
* **生物/物品**：
* 颜色：使用略微不同的粒子，或减少粒子数量以示区别。建议使用 `PORTAL` (紫色传送门粒子) 或稀疏的 `ENCHANT`。
* 范围：粒子生成的圆柱体高度 = `entity.getHeight()`。半径 = `entity.getWidth() * 1.5`。



#### 5. 异常处理

* **传送中途实体消失**：在 `TeleportTask` 的每一 tick，检查 `target.isValid()` 或 `target.isDead()`。如果失效，从列表中移除，不再渲染粒子，最终不传送。
* **珍珠不足**：在启动瞬间（点击书本条目时）进行计算。如果 5x5 范围内有一百只鸡（Cost 100），玩家只有 16 颗珍珠，直接报错：“能量不足！在该区域传送需要 100 份末影能量。”


这份 **AetherGate v1.6.0 补丁计划书** 严格遵循你的最新需求，特别是关于“双人同船”的特殊处理逻辑和移动容错机制。

---

# AetherGate v1.6.0 补丁计划书 (Patch Plan)

**版本目标**：实现以祭坛为中心的群体/编队传送，支持载具、乘客、宠物及掉落物，优化传送打断判定。

## 📅 开发分块与实现顺序 (Implementation Phases)

建议按照以下顺序进行开发，以确保逻辑层层递进，便于调试。

### 第一阶段：配置与基础架构 (Configuration & Infrastructure)

**目标**：为新功能准备配置文件项和全局数据结构。

1. **修改 `config.yml**`：
* 新增 `teleport.allowable-movement-radius` (double)：默认 `0.5`。允许玩家在传送蓄力期间产生的轻微位移半径。
* 新增 `teleport.cost.passive-entity` (int)：默认 `1`。普通实体/乘客/载具的传送费用。
* 新增 `teleport.cost.player-passenger` (int)：默认 `1`。携带其他玩家时的费用。


2. **修改 `TeleportService` 数据结构**：
* 引入全局锁 `Set<UUID> globalLockedEntities`（线程安全 Set），用于记录当前所有正在被传送（无论是发起者还是被动跟随者）的实体 UUID。
* 防止多个祭坛或多个玩家同时尝试传送同一个实体。



### 第二阶段：实体扫描与锁定逻辑 (Entity Scanning & Locking)

**目标**：实现最核心的“选人”逻辑，特别是处理复杂的骑乘关系。

1. **实现 `EntityScanner` 工具类**：
* **范围扫描**：扫描祭坛周围 5x5 (`interaction-radius`)。
* **根节点提取**：对每一个实体调用 `getRootVehicle()`，只操作最底层的载具（如船、矿车、马）。


2. **实现“霸道锁定”逻辑 (双人同船处理)**：
* **场景**：玩家 A (发起者) 和 玩家 B (乘客) 在同一条船上。
* **逻辑流**：
1. A 点击传送。
2. 系统识别到 `Boat` 是根实体。
3. 系统检查 `Boat` 的乘客列表，发现 B。
4. **状态检查**：检查 B 是否已经在 `activeTasks` (正在自己传送)。如果是，则 A 的传送失败或跳过该船（避免冲突）。
5. **强制锁定**：如果 B 空闲，将 A、Boat、B 全部加入 `globalLockedEntities`。
6. **被动状态**：B 虽然没有发起传送，但会被标记为“被动传送中”，无法进行其他操作，且屏幕应显示“你正在被玩家 A 传送...”。




3. **过滤器实现 (Filter)**：
* **白名单（免费）**：掉落物 (`Item`)、属于发起者的已驯服生物 (`Tameable` 且 Owner == Caster)。
* **计费名单**：所有载具（马、船、矿车）、无主生物、其他玩家（作为乘客）。



### 第三阶段：计费系统重构 (Cost Calculation)

**目标**：实现“一人买单，全队升天”的经济逻辑。

1. **重写 `PearlCostManager**`：
* 废弃旧的 `tryConsumePearl`（边扣边算）。
* 新增 `PreCheckResult checkResources(Location anchor, Player payer, int totalCount)`：只计算，不扣除。
* 新增 `void deductResources(Location anchor, Player payer, int totalCount)`：执行实际扣除。


2. **费用计算公式**：
* `Total Cost = 1 (发起者) + N (计费实体数量) + M (被动玩家数量)`。
* *注*：如果是玩家自己的马（已驯服），费用为 0；如果是偷来的马或船，费用为 1。
* *双人同船*：A 发起，A 支付 1(A) + 1(船) + 1(B) = 3 颗珍珠（如果船和B都算单独实体，通常船作为载具算1，B作为乘客算1）。
* **优化策略**：建议将“船+B”作为一个整体组合计费，或者严格按实体头数计费。**本计划书建议按实体头数计费**，即船是实体(1)，B是实体(1)，共消耗2。



### 第四阶段：移动容错机制 (Movement Tolerance)

**目标**：解决“手抖打断传送”的问题。

1. **修改 `TeleportListener.onMove**`：
* 移除简单的 `getX() != getX()` 判定。
* 获取玩家开始传送时的 `lockLocation`。
* 计算 `event.getTo().distance(lockLocation)`。
* 如果距离 > `config.get("allowable-movement-radius")`，则取消传送。
* *允许*：原地跳跃（如果高度变化在范围内）、视角转动（Yaw/Pitch 变化不触发打断）。



### 第五阶段：传送执行与视觉同步 (Execution & Visuals)

**目标**：让被动传送的生物和玩家也能看到效果，并正确移动。

1. **修改 `TeleportTask**`：
* **多目标支持**：Task 不再只持有一个 Player，而是持有 `List<Entity> allTargets`。
* **粒子特效适配**：
* 遍历 `allTargets`。
* 读取 `entity.getHeight()` 和 `entity.getWidth()`。
* 生成动态高度的粒子圈，不再使用写死的 2.0 高度。




2. **被动玩家通知**：
* 如果列表里有其他玩家（玩家 B），给 B 发送 Title 或 Actionbar：“正在随 玩家 A 传送...”。


3. **执行传送**：
* 优先传送根实体（载具）。Paper/Spigot 会自动处理乘客跟随。
* **异常处理**：传送后检查乘客是否脱落（偶尔发生），如果脱落尝试在目标点重新 `addPassenger`（可选，视稳定性而定）。



---

## ✅ 功能逻辑自查表 (QA Checklist)

开发完成后，请开发者对照以下场景进行测试：

1. **载具计费测试**：
* [ ] 骑自己的马（已驯服）：只消耗 1 珍珠（人）。
* [ ] 骑路边的猪（未驯服）：消耗 2 珍珠（人+猪）。
* [ ] 划船：消耗 2 珍珠（人+船）。


2. **多人同船测试**：
* [ ] A 和 B 同船，A 点击传送：A 扣除 3 珍珠（A+船+B），B 无法操作直到传送完成，两人一船同时到达目的地。
* [ ] A 和 B 同船，A 没珍珠：提示资源不足，传送取消。


3. **移动容错测试**：
* [ ] 传送时转动视角：不打断。
* [ ] 传送时被推挤微小距离（<0.5）：不打断。
* [ ] 主动走出范围：打断。


4. **宠物测试**：
* [ ] 站着的狗（已驯服）：免费跟随。
* [ ] 坐下的狗：通常坐下的宠物不应被传送（逻辑需确认，建议坐下的宠物视为“守家”，不传送）。**本计划默认：范围内所有宠物都传，除非代码额外判断 `isSitting()**`。


5. **防刷测试**：
* [ ] 扔一组（64个）钻石在地上：免费传送，不消耗珍珠。