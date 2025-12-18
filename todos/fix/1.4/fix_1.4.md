# AetherGate 修复说明书 (v1.4)

优先级: 紧急 (Blocker) - 导致插件无法启动

目标版本: Paper 1.21.4

## 1. 修复编译错误 (ChatColor Missing)

**涉及文件:** `plugin/src/main/java/cn/lingnc/aethergate/item/CustomItems.java`

问题描述:

代码中调用了 ChatColor.AQUA 和 ChatColor.BOLD，但未导入 org.bukkit.ChatColor，且与同文件其他部分使用的 Modern Component API 风格不统一。

修改方案:

将 createWorldAnchorItem 方法中的旧版 setDisplayName 替换为新版 displayName(Component) 方法。

**修改前 (错误代码):**

Java

```
    public static ItemStack createWorldAnchorItem() {
        ItemStack stack = new ItemStack(Material.LODESTONE, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // Error here: ChatColor cannot be resolved
            meta.setDisplayName(ChatColor.AQUA.toString() + ChatColor.BOLD + "世界锚点");
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_TYPE, PersistentDataType.STRING, TYPE_WORLD_ANCHOR);
            stack.setItemMeta(meta);
        }
        return stack;
    }
```

**修改后 (正确代码):**

Java

```
    public static ItemStack createWorldAnchorItem() {
        ItemStack stack = new ItemStack(Material.LODESTONE, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // 使用 Adventure Component API (与其他物品保持一致)
            meta.displayName(Component.text("世界锚点", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false)); // 推荐：去除去斜体，保持整洁

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_TYPE, PersistentDataType.STRING, TYPE_WORLD_ANCHOR);
            stack.setItemMeta(meta);
        }
        return stack;
    }
```

------

## 2. 检查清单 (Checklist)

开发人员在打包前请确认：

1.  [ ] **`CustomItems.java`**: 确认不再包含任何 `ChatColor` 的引用。

2.  [ ] **`AltarService.java`**: 确认 `SMOKE_LARGE` 已替换为 `LARGE_SMOKE` (上一轮修复)。

3.  [ ] **Imports**: 确认 `CustomItems.java` 头部已包含以下导入（通常 IDE 会自动处理）：

    Java

    ```
    import net.kyori.adventure.text.Component;
    import net.kyori.adventure.text.format.NamedTextColor;
    import net.kyori.adventure.text.format.TextDecoration;
    ```

完成此修改后，请重新运行 `build.sh` 进行编译和打包。