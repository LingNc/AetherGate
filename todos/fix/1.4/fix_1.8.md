这是一个针对 AetherGate v1.8 的修复说明书。

修改目标：

传送机制变更: 将“强制锁定玩家位置”改为“移动即取消传送”。玩家在传送预热期间如果发生了位移（不包括转头），传送会被打断。

UI 修复:

解决书本无法翻页的问题：原因是单页内容过多导致溢出（Header 占地太大）。将每页显示的条目数从 5 减少为 2，确保内容在单页高度限制内，从而让原版书本的“下一页”按钮正常出现。

视觉优化：移除条目前面的 > 符号。

请将以下文档交付给开发人员。

AetherGate 修复说明书 (v1.8)
优先级: 高 (Gameplay & UI) 目标版本: Paper 1.21.4

1. 传送机制：移动打断 (Cancel on Move)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/teleport/TeleportListener.java

修改逻辑: 废弃原有的 event.setTo(lock) 强制拉回逻辑，改为检测到位移后调用 teleportService.cancelTeleport。