这是一个针对 AetherGate v1.0.2 的升级说明书。

核心变更： 将 “打开传送名册” 的交互操作从 右键点击磁石方块 转移到 右键点击悬浮的潮涌核心。

实现原理： 由于 ItemDisplay（物品展示实体）本身没有碰撞箱，无法被点击，我们需要在相同位置生成一个透明的 Interaction (交互) 实体来捕获玩家的点击事件。

请将以下文档交付给开发人员。

AetherGate 升级说明书 (v1.0.2)
优先级: 中 (Interaction UX) 目标版本: Paper 1.21.4

1. 祭坛服务升级 (AltarService.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java

修改目标:

管理 Interaction 实体的生命周期（生成、移除）。

提供通过实体查找祭坛坐标的方法。

修改步骤:

添加 coreInteractions 映射表。

修改 updateVisualState：在生成 Display 的同时生成 Interaction 实体。

修改 removeVisuals / clearVisuals：同步移除 Interaction 实体。

新增 getAnchorFromEntity 方法。

代码替换/新增:

2. 监听器调整 (WorldAnchorListener.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/listener/WorldAnchorListener.java

修改目标:

添加 onEntityInteract 监听器，处理对 Interaction 实体的点击，触发传送菜单。

修改 onRightClickAnchor，移除点击方块打开菜单的逻辑，改为提示玩家点击核心。

代码替换/新增:

3. 检查清单
[ ] AltarService.java: 确认 updateVisualState 会生成 Interaction 实体并设置正确的尺寸（宽/高随 scale 变化）。

[ ] AltarService.java: 确认 removeVisuals 和 clearVisuals 能够正确清除 Interaction 实体，防止产生幽灵实体。

[ ] WorldAnchorListener.java: 确认点击磁石方块不再打开书本，而是发送提示。

[ ] WorldAnchorListener.java: 确认点击悬浮核心（Interaction）能正确打开书本 UI。

完成这些修改后，传送操作将完全转移到核心实体上，提升交互的沉浸感。