1. 增加两个配置项配置书ui的页面显示条目，一个是配置第一页的一个是配置后面的默认是第一页4条，第二页7条
2. 移除传送时必须站在祭坛核心上面，只需要在配置文件中的范围内就可以传送，默认2，就是就是默认祭坛不是5*5吗核心在中间，必须在周围2格内，或者就是配置5表示以祭坛核心为中心的5*5方块内，高度从祭坛底座开始到上面5格。高度固定都是5格内，这个5调整的是可以点击ui传送的范围（而不是必须站在祭坛上）
AetherGate 功能增强说明书 (v1.13)
优先级: 中 (Configuration & Logic) 目标版本: Paper 1.21.4

1. 配置文件更新 (config.yml)
涉及文件: plugin/src/main/resources/config.yml

修改: 增加 GUI 分页配置和传送交互范围配置。

2. 配置类更新 (PluginConfig.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/config/PluginConfig.java

修改: 添加新配置项的读取方法。

3. 祭坛服务更新 (AltarService.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java

修改: 新增 findNearestActiveAnchor 方法，用于检测玩家是否在配置的有效范围内。

4. 书本 UI 服务更新 (TeleportMenuService.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/teleport/TeleportMenuService.java

修改: 根据配置项动态计算每页显示的条目数。第一页显示更少，后续页显示更多。

5. 指令处理更新 (CharmCommand.java)
涉及文件: plugin/src/main/java/cn/lingnc/aethergate/command/CharmCommand.java