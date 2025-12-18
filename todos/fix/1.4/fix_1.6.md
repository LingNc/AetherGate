AetherGate 修复与功能补全说明书 (v1.6)
优先级: 紧急 (Blocker) 目标版本: Paper 1.21.4

1. 修复粒子报错 (FIREWORKS_SPARK)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java

问题: Particle.FIREWORKS_SPARK 在 1.20.5+ API 中已重命名。

修改方案: 将所有 Particle.FIREWORKS_SPARK 替换为 Particle.FIREWORK。

代码定位 (约 582 行):

Java

// 旧代码 (错误)
Particle particle = ring < 8 ? Particle.END_ROD : Particle.FIREWORKS_SPARK;

// 新代码 (正确)
Particle particle = ring < 8 ? Particle.END_ROD : Particle.FIREWORK;
2. 配方修正：世界锚点使用末影锭
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/recipe/RecipeRegistry.java

需求: 世界锚点的合成配方中，原本使用 ENDER_PEARL (A) 的位置，现在必须使用 末影锭 (Ender Ingot)。

修改方案: 修改 registerWorldAnchorRecipe 方法。

Java

    private static void registerWorldAnchorRecipe(AetherGatePlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "world_anchor");
        ShapedRecipe recipe = new ShapedRecipe(key, CustomItems.createWorldAnchorItem());

        // 保持形状:
        // D I D
        // L B L
        // D O D
        recipe.shape("DID", "LBL", "DOD");

        recipe.setIngredient('D', Material.DIAMOND);

        // 修改点：使用末影锭 (CustomItems.createEnderIngot(1)) 代替原版珍珠
        // 注意：必须使用 ExactChoice 才能匹配带有 CustomModelData 的物品
        recipe.setIngredient('I', new RecipeChoice.ExactChoice(CustomItems.createEnderIngot(1)));

        recipe.setIngredient('L', Material.LAPIS_BLOCK);
        recipe.setIngredient('B', Material.LODESTONE);
        recipe.setIngredient('O', Material.CRYING_OBSIDIAN);

        Bukkit.addRecipe(recipe);
    }
注意：请确保 CustomItems.java 中有名为 createEnderIngot (或 createEnderIngotItem) 的方法。如果方法名是 createEnderIngotItem，请相应调整调用。

3. 消耗逻辑修正：优先消耗末影锭
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/teleport/PearlCostManager.java

需求: 传送时扣除费用的逻辑需要更新。如果有末影锭就优先消耗,然后找零为末影珍珠,返回容器.