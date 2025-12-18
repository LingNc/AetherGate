这是一个针对 AetherGate v1.5 的紧急修复文档。

错误分析： 报错信息 Type mismatch: cannot convert from TextComponent.Builder to Component 指出，在 TeleportMenuService.java 的 buildEntry 方法中，代码使用了 Component.text()（这会返回一个 Builder 构建器对象），但没有在链式调用的末尾加上 .build() 来生成最终的 Component 对象，导致类型不匹配。

这就是为什么你右键点击祭坛时（尝试打开菜单），后台报错且没有任何界面弹出的原因。

请将此文档交付给开发人员。

AetherGate 修复说明书 (v1.5)
优先级: 紧急 (Blocker) - 导致传送菜单无法打开 目标版本: Paper 1.21.4

1. 修复 GUI 构建错误 (Missing .build())
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/teleport/TeleportMenuService.java

修改方案: 在 buildEntry 方法的返回值链式调用末尾添加 .build()。

修改前 (错误代码):

Java

    private Component buildEntry(Waypoint waypoint) {
        // ... (省略部分代码)
        return Component.text()
                .append(firstLine)
                .append(Component.newline())
                .append(secondLine)
                .append(Component.newline()); // 错误：缺少 .build()，返回的是 Builder
    }
修改后 (正确代码):

Java

    private Component buildEntry(Waypoint waypoint) {
        // ... (省略部分代码)
        return Component.text()
                .append(firstLine)
                .append(Component.newline())
                .append(secondLine)
                .append(Component.newline())
                .build(); // 修复：添加 .build()
    }

