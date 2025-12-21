# AetherGate 升级计划书 (Target: Paper 1.21.10)

## 1. 核心依赖升级 (Maven)

由于您目前使用的是 Maven 构建系统（根据 `pom.xml`），我们需要手动修改依赖版本并配置 Manifest 属性以确保兼容性。

### 1.1 修改 `pom.xml`

请在 `plugin/pom.xml` 中找到 `dependencies` 部分，将 `paper-api` 的版本号更新为目标版本。

**修改前:**

```xml
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.21.4-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>

```

**修改后:**

```xml
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.21.10-R0.1-SNAPSHOT</version> <scope>provided</scope>
</dependency>

```

### 1.2 ProtocolLib 兼容性检查

* **现状**: 您当前使用的是 `5.4.0`。
* **行动**: 随着 Minecraft 版本的提升，ProtocolLib 通常需要更新到最新的 CI 构建版本才能支持新的协议包结构。
* **建议**: 暂时保持 `5.4.0` 进行编译测试。如果在运行时出现 `NoClassDefFoundError` 或无法注入数据包，请检查 Jenkins 获取支持 1.21.10 的最新构建版本。

---

## 2. 构建配置调整 (Manifest Mappings)

根据您提供的文档，Paper 1.20.5+ 服务端运行时采用了 Mojang 原生映射。为了避免服务端在首次加载插件时进行不必要的“猜测”或重映射错误，建议显式声明插件使用的映射命名空间。

由于您使用 Maven 且依赖的是标准的 `paper-api`，您的代码实际上是基于 **Spigot 映射** (Spigot mappings) 编译的。我们需要在 JAR 的 Manifest 中声明这一点。

### 2.1 更新 `maven-shade-plugin` 配置

在 `pom.xml` 的 `build/plugins` 部分，找到 `maven-shade-plugin`，并添加 `ManifestResourceTransformer` 来写入属性。

**更新后的配置示例:**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.2</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <relocations>
                    <relocation>
                        <pattern>org.sqlite</pattern>
                        <shadedPattern>cn.lingnc.aethergate.libs.sqlite</shadedPattern>
                    </relocation>
                </relocations>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <manifestEntries>
                            <paperweight-mappings-namespace>spigot</paperweight-mappings-namespace>
                        </manifestEntries>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>

```

---

## 3. 代码适配与检查

虽然小版本更新（1.21.4 -> 1.21.10）通常不会破坏 API，但基于 `plugin_context.md` 中的代码，建议重点检查以下模块：

### 3.1 数据组件 (Data Components)

* **涉及文件**: `src/main/java/cn/lingnc/aethergate/item/CustomItems.java`
* **检查点**: 您使用了 `io.papermc.paper.datacomponent` API。请确保 1.21.10 中相关 API（如 `CustomModelData`）没有发生变更。如果有编译错误，请参考最新的 Javadoc 调整 `CustomModelData.customModelData().addString(...)` 的调用方式。

### 3.2 实体与粒子 (Entities & Particles)

* **涉及文件**: `TeleportService.java` (粒子效果) 和 `AltarService.java` (ItemDisplay/Interaction)。
* **检查点**: 检查 `Particle` 枚举是否有变动（通常很稳定）。确认 `ItemDisplay` 的 `setTransformation` 方法在 1.21.10 中是否保持行为一致。

### 3.3 配置文件版本

* **涉及文件**: `plugin.yml`
* **行动**: 将 `api-version` 保持为 `1.21` 即可，这涵盖了 1.21.x 的所有版本。无需修改为 1.21.10。

---

## 4. 执行步骤

1. **备份**: 备份整个项目目录。
2. **清理构建**: 在终端执行 `mvn clean`。
3. **修改配置**: 按照上述第 1 和第 2 步修改 `pom.xml`。
4. **编译**: 执行 `mvn package`。
* *如果在下载依赖时失败，请检查 `1.21.10-R0.1-SNAPSHOT` 是否已发布到 PaperMC 仓库。如果未发布，您可能需要回退到最新的可用版本（如 1.21.4）。*


5. **本地测试**:
* 搭建一个 Paper 1.21.10 服务端。
* 放入编译好的 `original-aether-gate-1.6.0.jar` (或者您更新版本号后的 jar)。
* 重点测试：**祭坛激活**（检查 Display 实体显示）、**传送流程**（粒子与位置计算）、**数据库读写**。



---

## 5. 待办列表 (Checklist)

* [ ] `pom.xml`: 更新 `paper-api` version 至 `1.21.10-R0.1-SNAPSHOT`
* [ ] `pom.xml`: 在 `maven-shade-plugin` 中添加 `paperweight-mappings-namespace: spigot`
* [ ] `mvn clean package` 编译成功无报错
* [ ] 启动服务器无 `NoClassDefFoundError` (ProtocolLib 检查)
* [ ] 游戏内 `/aether debug` 测试祭坛结构检查功能正常
* [ ] 游戏内 `/aether travel` 完成一次完整传送

是否需要我为您生成更新后的 `pom.xml` 完整文件内容？