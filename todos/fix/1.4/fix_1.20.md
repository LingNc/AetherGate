目前好像存在使用命名牌对锚点命名的时候命名牌没有被消耗：

是的，这部分代码确实存在一个潜在的逻辑隐患，可能导致物品不被消耗。

### 问题分析

在 `WorldAnchorListener.java` 中：

1.  **事件取消时机**：你在调用 `consumeItem` **之前** 就调用了 `event.setCancelled(true)`。
2.  **对象引用问题**：`consumeItem` 接收的 `stack` 是通过 `event.getItem()` 获取的。在某些服务端核心（或特定版本）中，一旦事件被取消，`event.getItem()` 返回的 `ItemStack` 对象可能变成一个快照（Snapshot）或者是与玩家背包断开连接的对象。
3.  **操作无效**：虽然你在 `consumeItem` 中调用了 `player.getInventory().setItemInMainHand(stack)`，但如果 `stack` 对象的内部状态因为事件取消而与实际背包产生了“脱钩”，或者客户端预测与服务端状态不同步（Client-side prediction desync），就可能导致物品看起来没少，或者右键瞬间少了一下又弹回来（鬼影物品）。

### 解决方案

最稳妥的方式是：**不要依赖事件传递过来的 `ItemStack` 对象去修改数量，而是直接获取玩家背包中当前主手的物品进行操作。**

请在 `plugin/src/main/java/cn/lingnc/aethergate/listener/WorldAnchorListener.java` 中修改 `consumeItem` 方法。

#### 修改前的代码：

```java
    private void consumeItem(Player player, ItemStack stack) {
        if (stack == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        if (stack.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInMainHand(stack);
        }
    }
```

#### 修改后的代码：

我们不再使用传入的 `stack` 参数（或者忽略它），直接从背包拿最新的。

```java
    private void consumeItem(Player player, ItemStack stack) {
        // 1. 再次检查创造模式 (双重保险)
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // 2. 直接获取主手物品的实时引用，而不是使用事件中的 snapshot
        ItemStack handItem = player.getInventory().getItemInMainHand();

        // 3. 防御性检查：确保手里确实有东西，且不是空气
        if (handItem.getType().isAir() || handItem.getAmount() <= 0) {
            return;
        }

        // 4. 执行消耗
        handItem.setAmount(handItem.getAmount() - 1);

        // 5. 如果数量归零，显式设置为空 (虽然 1.21 通常会自动处理，但为了保险)
        if (handItem.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            // 设置回去 (确保更新生效)
            player.getInventory().setItemInMainHand(handItem);
        }
    }
```

**或者**，如果你不想修改方法签名，直接在 `onRightClickAnchor` 调用时改一下也可以，但修改 `consumeItem` 方法内部是最安全的。

### 为什么这样做有效？

通过 `player.getInventory().getItemInMainHand()` 获取的是该 Player 背包槽位的**直接引用**（Direct Reference）。无论之前的 Event 是否被 Cancelled，直接操作这个引用并 `setItem` 回去，都会强制服务端更新背包数据，确保物品被正确扣除。