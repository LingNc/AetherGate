根据您提供的报错日志和开发文档，问题出在 `TeleportService.java` 中对 **`Particle.FLASH`** 的调用上。

### 错误原因分析

报错信息 `java.lang.IllegalArgumentException: missing required data class org.bukkit.Color` 指出，生成某个粒子时缺少了必须的 `org.bukkit.Color` 数据。

虽然堆栈信息指向第 794 行（在您之前的代码中是 `Particle.EXPLOSION`），但根据您提供的 **Paper 1.21.9 开发文档**，**`FLASH`** 粒子现在明确要求提供颜色数据：

> "While `FLASH` and `TINTED_LEAVES` particles take an ARGB color, the alpha channel is ignored."
>
>

> "These particles can be colored by passing a `Color` object as the `data` argument."
>
>

您之前的代码中，`spawnArrivalShockwave` 方法包含以下调用：

```java
world.spawnParticle(Particle.FLASH, center, 1); // 缺少 Color 数据

```

这在旧版本中可能被允许（默认为白色），但在 1.21+ 版本中，如果不传入 `Color` 对象，就会抛出上述异常。

### 修复方案

您需要为 `Particle.FLASH` 显式传入一个 `Color` 对象（例如 `Color.WHITE` 或 `Color.RED`）。

请修改 `TeleportService.java` 中的 `spawnArrivalShockwave` 方法：

**修改前：**

```java
private void spawnArrivalShockwave(World world, Location center) {
    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
    world.spawnParticle(Particle.EXPLOSION, center, 40, 2.0, 0.5, 2.0, 0.05);
    world.spawnParticle(Particle.FLASH, center, 1); // <--- 报错源头
    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
}

```

**修改后：**
我们需要引入 `org.bukkit.Color` 并将其作为参数传递。

```java
import org.bukkit.Color; // 别忘了导入

// ...

private void spawnArrivalShockwave(World world, Location center) {
    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
    [cite_start]// EXPLOSION 不需要改动，文档只提到它支持通过 offsetX 调整大小 [cite: 109]
    world.spawnParticle(Particle.EXPLOSION, center, 40, 2.0, 0.5, 2.0, 0.05);

    // 修复：添加 Color.WHITE (或您想要的任何颜色) 作为最后一个参数
    // 使用签名: spawnParticle(Particle, Location, int, T data)
    world.spawnParticle(Particle.FLASH, center, 1, Color.WHITE);

    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
}

```

### 总结

在 1.21 版本中，**`Particle.FLASH`** 属于“Colored particles”类别 ，必须提供 `Color` 类型的数据才能成功生成。请应用上述修复并重新编译。