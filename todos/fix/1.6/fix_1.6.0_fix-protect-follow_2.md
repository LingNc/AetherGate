这种情况通常是因为**插件重载（Reload）或服务器重启**发生在玩家正在传送的过程中（特别是那 3 秒的预热期或传送后的恢复期）。

**原因分析：**
在 `TeleportTask` 开始时，为了保护玩家，代码执行了 `applyLock()`，其中调用了 `player.setInvulnerable(true)`（设置为无敌）。
正常流程下，任务结束或中止时会调用 `cleanup()` 来解除无敌。
但是，如果**服务器突然关闭**或**插件被重载**，`TeleportTask` 会被迫终止，但 `cleanup()` 方法可能没有机会执行。这就导致玩家的数据文件（UUID.dat）里被永久写入了 `Invulnerable: 1`（无敌状态），即使切换游戏模式也无法清除这个底层 NBT 标签。

怪物不攻击是因为它们判定该玩家为“无敌目标”，从而忽略仇恨。

---

### 解决方案

#### 1. 紧急修复当前账号 (无需写代码)

如果你有管理员权限，可以直接在游戏内用原版指令修正该账号的数据：

```mcfunction
/effect clear <玩家ID>
/data merge entity <玩家ID> {Invulnerable:0b}

```

*(注意：需要 OP 权限，且该指令直接修改玩家 NBT 数据，强制关闭无敌标记)*

---

#### 2. 代码层面彻底修复 (防止未来发生)

你需要在插件关闭（onDisable）时，强制中止所有正在进行的传送任务，确保“清理逻辑”被执行。

**第一步：在 `TeleportService.java` 中添加 `shutdown()` 方法**

```java
// 在 TeleportService 类中添加
public void shutdown() {
    // 遍历所有正在进行的任务并强制中止
    for (TeleportTask task : activeTasks.values()) {
        // 传入 null 不发送消息，或者传入提示信息
        task.abort("§c插件重载，传送强制取消。");
    }
    activeTasks.clear();
    // 顺便清理所有被锁定的实体集合，防止内存泄漏
    globalLockedEntities.clear();
}

```

**第二步：在 `AetherGatePlugin.java` 的 `onDisable` 中调用它**

```java
@Override
public void onDisable() {
    if (teleportService != null) {
        teleportService.shutdown(); // <--- 新增这行，确保所有人的无敌状态被解除
    }
    if (altarService != null) {
        altarService.clearVisuals();
    }
    getLogger().info("AetherGate disabled");
}

```

**第三步：增加一个“急救”指令**
在 `CharmCommand` 或调试指令中加一个清理功能，以防万一：

```java
// 在 CharmCommand 的 debug 处理中，或者新增一个 fix 命令
if (args[0].equalsIgnoreCase("fix")) {
    Player target = player; // 或者解析 args[1] 指定玩家
    target.setInvulnerable(false);
    target.setWalkSpeed(0.2f);
    target.setFlySpeed(0.1f);
    player.sendMessage("§a已重置玩家状态。");
    return true;
}

```

加上这套 `shutdown` 逻辑后，下次即便你写代码写一半 reload 插件，正在传送的玩家也会立即被踢出传送状态并恢复正常，不会再卡在无敌模式了。