这是由于 AltarService.java 文件中存在严重的代码拼贴错误。

从报错信息和代码来看，似乎是在 attemptActivation 方法的 catch 块内部，意外粘贴进了一段包含 getOrLoadAnchor 方法定义的代码，导致了方法的嵌套定义（Java 不支持）以及变量重复定义等一系列语法错误。这也直接导致了插件运行时抛出异常，使得“末影锭激活”功能失效。

请使用下方修复后的完整代码替换 plugin/src/main/java/cn/lingnc/aethergate/altar/AltarService.java。

修复说明
清理垃圾代码: 移除了 attemptActivation 方法中 catch 块内错误粘贴的 getOrLoadAnchor 方法定义和多余逻辑。

补全激活逻辑: 在数据库保存成功后，正确更新内存缓存 activeAltars，并调用 updateVisualState 和 playActivationEffects 来显示激活效果（这部分逻辑在错误代码中被覆盖了）。
[19:52:46 ERROR]: Could not pass event PlayerInteractEvent to AetherGate v1.0.2
java.lang.Error: Unresolved compilation problems:
        Syntax error on token(s), misplaced construct(s)
        No exception of type Location can be thrown; an exception type must be a subclass of Throwable
        Duplicate parameter loc
        Void methods cannot return a value
        Duplicate local variable world
        Void methods cannot return a value
        Duplicate local variable mapKey
        Duplicate local variable waypoint
        Void methods cannot return a value
        Void methods cannot return a value
        Void methods cannot return a value
        Syntax error on token "}", delete this token

        at AetherGate_Plugin_1.0.2-SNAPSHOT.jar/cn.lingnc.aethergate.altar.AltarService.attemptActivation(AltarService.java:179) ~[AetherGate_Plugin_1.0.2-SNAPSHOT.jar:?]
        at AetherGate_Plugin_1.0.2-SNAPSHOT.jar/cn.lingnc.aethergate.listener.WorldAnchorListener.onRightClickAnchor(WorldAnchorListener.java:69) ~[AetherGate_Plugin_1.0.2-SNAPSHOT.jar:?]
        at co.aikar.timings.TimedEventExecutor.execute(TimedEventExecutor.java:80) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at org.bukkit.plugin.RegisteredListener.callEvent(RegisteredListener.java:71) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at io.papermc.paper.plugin.manager.PaperEventManager.callEvent(PaperEventManager.java:54) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at io.papermc.paper.plugin.manager.PaperPluginManagerImpl.callEvent(PaperPluginManagerImpl.java:131) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at org.bukkit.plugin.SimplePluginManager.callEvent(SimplePluginManager.java:628) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(CraftEventFactory.java:526) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.level.ServerPlayerGameMode.useItemOn(ServerPlayerGameMode.java:478) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.network.ServerGamePacketListenerImpl.handleUseItemOn(ServerGamePacketListenerImpl.java:2048) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.network.protocol.game.ServerboundUseItemOnPacket.handle(ServerboundUseItemOnPacket.java:45) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.network.protocol.game.ServerboundUseItemOnPacket.handle(ServerboundUseItemOnPacket.java:10) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.network.protocol.PacketUtils.lambda$ensureRunningOnSameThread$0(PacketUtils.java:29) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.TickTask.run(TickTask.java:18) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.util.thread.BlockableEventLoop.doRunTask(BlockableEventLoop.java:155) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.util.thread.ReentrantBlockableEventLoop.doRunTask(ReentrantBlockableEventLoop.java:24) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.doRunTask(MinecraftServer.java:1449) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.doRunTask(MinecraftServer.java:176) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.util.thread.BlockableEventLoop.pollTask(BlockableEventLoop.java:129) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.pollTaskInternal(MinecraftServer.java:1429) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.pollTask(MinecraftServer.java:1423) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.util.thread.BlockableEventLoop.managedBlock(BlockableEventLoop.java:139) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.managedBlock(MinecraftServer.java:1380) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.waitUntilNextTick(MinecraftServer.java:1388) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1265) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.lambda$spin$2(MinecraftServer.java:310) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at java.base/java.lang.Thread.run(Thread.java:1474) ~[?:?]
[19:52:47 ERROR]: Could not pass event PlayerInteractEvent to AetherGate v1.0.2
java.lang.Error: Unresolved compilation problems:
        Syntax error on token(s), misplaced construct(s)
        No exception of type Location can be thrown; an exception type must be a subclass of Throwable
        Duplicate parameter loc
        Void methods cannot return a value
        Duplicate local variable world
        Void methods cannot return a value
        Duplicate local variable mapKey
        Duplicate local variable waypoint
        Void methods cannot return a value
        Void methods cannot return a value
        Void methods cannot return a value
        Syntax error on token "}", delete this token

        at AetherGate_Plugin_1.0.2-SNAPSHOT.jar/cn.lingnc.aethergate.altar.AltarService.attemptActivation(AltarService.java:179) ~[AetherGate_Plugin_1.0.2-SNAPSHOT.jar:?]
        at AetherGate_Plugin_1.0.2-SNAPSHOT.jar/cn.lingnc.aethergate.listener.WorldAnchorListener.onRightClickAnchor(WorldAnchorListener.java:69) ~[AetherGate_Plugin_1.0.2-SNAPSHOT.jar:?]
        at co.aikar.timings.TimedEventExecutor.execute(TimedEventExecutor.java:80) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at org.bukkit.plugin.RegisteredListener.callEvent(RegisteredListener.java:71) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at io.papermc.paper.plugin.manager.PaperEventManager.callEvent(PaperEventManager.java:54) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at io.papermc.paper.plugin.manager.PaperPluginManagerImpl.callEvent(PaperPluginManagerImpl.java:131) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at org.bukkit.plugin.SimplePluginManager.callEvent(SimplePluginManager.java:628) ~[paper-api-1.21.7-R0.1-SNAPSHOT.jar:?]
        at org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(CraftEventFactory.java:526) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.level.ServerPlayerGameMode.useItemOn(ServerPlayerGameMode.java:478) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.network.ServerGamePacketListenerImpl.handleUseItemOn(ServerGamePacketListenerImpl.java:2048) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.network.protocol.game.ServerboundUseItemOnPacket.handle(ServerboundUseItemOnPacket.java:45) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.network.protocol.game.ServerboundUseItemOnPacket.handle(ServerboundUseItemOnPacket.java:10) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.network.protocol.PacketUtils.lambda$ensureRunningOnSameThread$0(PacketUtils.java:29) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.TickTask.run(TickTask.java:18) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.util.thread.BlockableEventLoop.doRunTask(BlockableEventLoop.java:155) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.util.thread.ReentrantBlockableEventLoop.doRunTask(ReentrantBlockableEventLoop.java:24) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.doRunTask(MinecraftServer.java:1449) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.doRunTask(MinecraftServer.java:176) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.util.thread.BlockableEventLoop.pollTask(BlockableEventLoop.java:129) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.pollTaskInternal(MinecraftServer.java:1429) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.pollTask(MinecraftServer.java:1423) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.util.thread.BlockableEventLoop.managedBlock(BlockableEventLoop.java:139) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.managedBlock(MinecraftServer.java:1380) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.waitUntilNextTick(MinecraftServer.java:1388) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1265) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at net.minecraft.server.MinecraftServer.lambda$spin$2(MinecraftServer.java:310) ~[paper-1.21.7.jar:1.21.7-32-e792779]
        at java.base/java.lang.Thread.run(Thread.java:1474) ~[?:?]
末影锭无法正确激活。