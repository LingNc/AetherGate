### 修复计划概览

1. **重构资源消耗逻辑 (`PearlCostManager.java`)**：
* **现状**：先扣珍珠再扣锭，导致当通过量大（如 >9）时，如果没有足够的散珍珠，逻辑可能会卡住或判断错误。
* **修复**：改为“大额优先”策略。
* 优先计算需要多少个锭（`Cost / 9`）。
* 优先扣除锭。
* 剩余零头（`Cost % 9`）扣除珍珠。
* 如果零头珍珠不够，且还有锭，则“拆借”一个锭（扣1锭，找零 `9 - 零头` 个珍珠）。
* 如果锭不够，才尝试用大量珍珠填补缺口。

---

### 第一步：修复资源消耗逻辑

修改文件：`src/main/java/cn/lingnc/aethergate/teleport/PearlCostManager.java`

我们将 `consumePearls` 方法重写为智能扣除模式。

```java
// ... imports ...

public class PearlCostManager {

    // ... constants and hasEnoughPearls method remain same ...
    // hasEnoughPearls 逻辑不变，因为它只统计总价值，这是对的。

    /**
     * 智能扣除策略：
     * 1. 优先使用锭支付大额消耗 (cost / 9)。
     * 2. 使用珍珠支付零头 (cost % 9)。
     * 3. 如果珍珠不足支付零头，自动拆解 1 个锭进行支付并找零。
     * 4. 如果锭不足支付大额，尝试用海量珍珠填补。
     */
    public boolean consumePearls(Location anchorLoc, Player player, int amount) {
        if (anchorLoc == null || player == null || amount <= 0) {
            return false;
        }

        // 再次确认总资产是否足够（双重保险）
        if (!hasEnoughPearls(anchorLoc, player, amount)) {
            return false;
        }

        // 收集所有容器
        Inventory coreBarrel = getCoreBarrel(anchorLoc);
        List<ContainerRef> barrels = new ArrayList<>();
        List<ContainerRef> others = new ArrayList<>();
        collectContainers(anchorLoc, barrels, others);

        // 构建一个聚合的库存列表，按优先级排序：核心 -> 桶 -> 其他 -> 玩家
        List<Inventory> inventories = new ArrayList<>();
        List<Location> dropLocations = new ArrayList<>(); // 对应找零的位置

        if (coreBarrel != null) { inventories.add(coreBarrel); dropLocations.add(anchorLoc); }
        for (ContainerRef ref : barrels) { if(!ref.isCore()) { inventories.add(ref.getInventory()); dropLocations.add(ref.getDropLocation()); } }
        for (ContainerRef ref : others) { inventories.add(ref.getInventory()); dropLocations.add(ref.getDropLocation()); }
        inventories.add(player.getInventory()); dropLocations.add(player.getLocation());

        int remainingCost = amount;

        // --- 第一阶段：优先扣除锭 (解决 >9 的问题) ---
        // 计算理想情况下应该扣多少个锭
        int idealIngotsToTake = remainingCost / PEARL_VALUE;

        if (idealIngotsToTake > 0) {
            int takenIngots = 0;
            // 遍历所有容器扣除锭
            for (Inventory inv : inventories) {
                if (takenIngots >= idealIngotsToTake) break;
                takenIngots += takeItems(inv, true, idealIngotsToTake - takenIngots); // true = 只扣锭
            }
            // 减少待支付的总额
            remainingCost -= (takenIngots * PEARL_VALUE);
        }

        // --- 第二阶段：扣除剩余的零头 (珍珠) ---
        if (remainingCost > 0) {
            int takenPearls = 0;
            for (Inventory inv : inventories) {
                if (takenPearls >= remainingCost) break;
                takenPearls += takeItems(inv, false, remainingCost - takenPearls); // false = 只扣珍珠
            }
            remainingCost -= takenPearls;
        }

        // --- 第三阶段：自动拆解 (如果珍珠不够了，但还有锭) ---
        if (remainingCost > 0) {
            // 我们需要拆解锭来支付剩余的 cost
            // 此时 remainingCost 肯定 < 9，因为大头已经在第一阶段扣完了
            // 或者是因为玩家没锭了全靠珍珠，但珍珠也不够了

            for (int i = 0; i < inventories.size(); i++) {
                if (remainingCost <= 0) break;
                Inventory inv = inventories.get(i);
                Location dropLoc = dropLocations.get(i);

                // 尝试找一个锭
                if (takeItems(inv, true, 1) == 1) {
                    // 成功扣除了1个锭 (价值9)
                    int change = PEARL_VALUE - remainingCost; // 找零
                    refundPearls(inv, dropLoc, change, dropLoc.getWorld());
                    remainingCost = 0; // 支付完成
                }
            }
        }

        // 如果此时 remainingCost > 0，说明逻辑有误或hasEnoughPearls校验失效，但在步骤1已校验。
        // 还有一种边缘情况：玩家只有珍珠，且总量够，会全部在第二阶段扣完。

        return remainingCost <= 0;
    }

    /**
     * 通用扣除辅助方法
     * @param targetIsIngot true扣锭, false扣珍珠
     * @return 实际扣除的数量
     */
    private int takeItems(Inventory inventory, boolean targetIsIngot, int maxToTake) {
        if (inventory == null || maxToTake <= 0) return 0;
        int taken = 0;
        ItemStack[] contents = inventory.getContents();

        for (int i = 0; i < contents.length; i++) {
            if (taken >= maxToTake) break;
            ItemStack stack = contents[i];
            if (stack == null) continue;

            boolean isIngot = CustomItems.isEnderIngot(stack);
            boolean isPearl = stack.getType() == Material.ENDER_PEARL && !isIngot;

            if ((targetIsIngot && isIngot) || (!targetIsIngot && isPearl)) {
                int amountInStack = stack.getAmount();
                int need = maxToTake - taken;
                int toRemove = Math.min(amountInStack, need);

                stack.setAmount(amountInStack - toRemove);
                if (stack.getAmount() <= 0) {
                    inventory.setItem(i, null);
                }
                taken += toRemove;
            }
        }
        return taken;
    }

    // ... getCoreBarrel, collectContainers, refundPearls 等辅助方法保持不变 ...
    // ... 可以删除旧的 consumeIngotAmount, consumePearlAmount 等逻辑 ...
}

```

### 总结

1. **资源消耗**：现在的逻辑是“先把总成本算出来”，然后“能用锭就用锭，锭不够用珍珠，锭用完有零头拆锭找零”。彻底解决了 >9 时的卡顿问题。