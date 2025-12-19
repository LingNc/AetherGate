**目标**：修复 v1.6.0 开发版中发现的移动判定错误、骑乘传送失效、粒子丢失及 GUI 交互缺失问题。

## 📅 修复分块与实现顺序

### 🛠️ Fix Plan 1: 核心机制修复 (Movement & Mounting)

**解决问题**：

* **问题 1**：传送后移动报错 / 蓄力期间移动不灵敏。
* **问题 3**：玩家骑乘载具时，两者均未传送。

#### 1.1 优化移动判定 (`TeleportService.java`)

目前逻辑中，任务进入“恢复期 (Recovery Phase)”时玩家仍处于锁定状态，导致到达目的地后移动会触发 `cancelTeleport`。

* **修改 `handleMove` 方法**：
* 增加检查：`if (task.hasPerformedTeleport()) return;`。
* **逻辑**：如果已经发生了传送（即进入了 Recovery 阶段），不再检测移动距离，允许玩家自由走动（或者保持静止但不再报错）。
* **优化蓄力期判定**：确保 `allowable-movement-radius` 是基于 `lockPoint` 的水平距离计算（忽略 Y 轴变化，允许原地跳跃）。



#### 1.2 重构骑乘传送逻辑 (`TeleportTask.executeTeleport`)

Paper/Spigot API 中，直接传送骑着实体的玩家或被骑乘的实体，往往会导致“脱落”或“传送失败”。必须手动管理骑乘关系。

* **修改 `TeleportTask.executeTeleport**`：
* **Step 1: 记录关系**。在传送前，记录哪个玩家骑着哪个 Entity（`Map<UUID, UUID> mountMap`）。
* **Step 2: 强制下马**。遍历所有 targets 和玩家，执行 `entity.eject()` 或 `vehicle.removePassenger(passenger)`。
* **Step 3: 执行传送**。
* 先传送所有非玩家实体（载具、宠物）。
* 最后传送玩家（`internalTeleporting = true`）。


* **Step 4: 延迟重组 (关键)**。
* 使用 `Bukkit.getScheduler().runTaskLater(plugin, ..., 2L)`。
* 在 1-2 tick 后，根据 `mountMap` 重新寻找实体，并执行 `vehicle.addPassenger(player)`。


* *注意*：需要处理异步加载区块的问题，确保目标区块已加载。



---

### 🎨 Fix Plan 2: 视觉效果修复 (Visuals)

**解决问题**：

* **问题 4**：被传送的实体（如船、猪）没有粒子效果。

#### 2.1 实体粒子同步 (`TeleportTask`)

目前的 `spawnWarmupParticles` 和 `spawnArrivalPreview` 仅围绕 `lockPoint` (玩家位置) 生成粒子。

* **修改 `spawnWarmupParticles**`：
* 遍历 `this.targets` 列表。
* 对每个 entity，获取 `BoundingBox`。
* **动态高度**：根据 `entity.getHeight()` 生成螺旋粒子（类似玩家的 ENCHANT 效果，但可以换个颜色比如紫色 `WITCH` 粒子以示区别）。


* **修改 `spawnArrivalPreview**`：
* 同样遍历 `targets`。
* 根据目标的相对位置计算落点，在落点生成预览光柱。



---

### 📖 Fix Plan 3: 交互体验优化 (GUI)

**解决问题**：

* **问题 2**：需要在书中增加“取消传送”按钮。

#### 3.1 动态菜单构建 (`TeleportMenuService`)

目前 `openMenu` 只是生成目的地列表。

* **修改 `openMenu**`：
* 检查 `teleportService.isPlayerLocked(player.getUniqueId())`。
* **分支逻辑**：
* **如果未锁定**：显示正常的目的地列表（现有逻辑）。
* **如果已锁定**：生成一本特殊的书。
* 标题：“传送进行中”。
* 内容：显示当前倒计时或状态。
* **按钮**：居中显示大号红色的 **[终止仪式]**。
* **点击事件**：执行指令 `/aether cancel`（需要注册一个内部指令或带参数的 travel 指令）。






* **注册取消指令** (`CharmCommand`)：
* 处理 `/aether cancel`：调用 `teleportService.cancelTeleport(player, "玩家主动取消")`。



---

## 📝 开发 checklist

### Phase 1 (Core Logic)

* [ ] `TeleportService.handleMove`: 忽略 `performedTeleport == true` 时的移动检测。
* [ ] `TeleportTask`: 引入 `Map<Entity, List<Entity>> rideRelations` 记录骑乘结构。
* [ ] `TeleportTask.executeTeleport`: 实现 Dismount -> Teleport -> Remount (Delay 2 ticks) 逻辑。
* [ ] 测试：骑马传送，人马是否同时到达且人仍在马上。
* [ ] 测试：划船传送，人船是否同时到达且人仍在船上。

### Phase 2 (Visuals)

* [ ] `TeleportTask`: 遍历 `targets` 生成 Warmup 粒子。
* [ ] `TeleportTask`: 遍历 `targets` 生成 Arrival Preview 粒子。

### Phase 3 (GUI)

* [ ] `CharmCommand`: 新增 `cancel` 子命令。
* [ ] `TeleportMenuService`: 增加 `buildCancelPage()` 方法。
* [ ] `TeleportMenuService`: 在 `openMenu` 中判断锁定状态并切换页面。