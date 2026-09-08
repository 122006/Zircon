# Zircon

**简体中文** | [English](README_EN.md)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.122006.Zircon/zircon)](https://central.sonatype.com/artifact/io.github.122006.Zircon/zircon)
[![JitPack](https://jitpack.io/v/122006/Zircon.svg)](https://jitpack.io/#io.github.122006/Zircon)
[![GitHub Release](https://img.shields.io/github/v/release/122006/Zircon)](https://github.com/122006/Zircon/releases)
[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/19146-zircon.svg)](https://plugins.jetbrains.com/plugin/19146-zircon)
![Java 8–23](https://img.shields.io/badge/Java-8%E2%80%9323-green)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

Zircon 为 Java 增加**扩展方法、可选链、Elvis 表达式和模板字符串**。通过 javac 编译插件接入已有项目，并使用 IntelliJ IDEA 或 VS Code 插件获得补全、导航和语义检查。

编译结果仍是标准 Java 字节码，无需更换 JVM。适用于使用 javac 构建的 Java、Android、Spring Boot、JavaFX 等项目；运行时仍需保留项目实际使用的依赖。

[快速接入](#快速接入) · [编辑器支持](#编辑器支持) · [语法文档](#语法文档) · [常见问题](#常见问题) · [更新记录](CHANGELOG.md)

## 版本

| 组件 | 当前版本 | 用途 |
| --- | --- | --- |
| Zircon 编译与基础依赖 | **3.3.3** | `gradle`、`javac`、`zircon`、`base` |
| IntelliJ IDEA 插件 | 4.9 | 一个 ZIP，按 IDEA 版本自动选择兼容实现 |
| VS Code 扩展 | 0.0.1 | 开发中的编辑器支持，安装方式见下文 |

编译依赖与编辑器插件独立版本管理，无需使用相同的版本号。

## 语法预览

### 扩展方法

给已有类型增加调用方式，无需修改原类。实例扩展方法必须是静态方法，第一个参数表示接收者：

```java
package demo;

import zircon.ExMethod;

public class TextExtensions {
    @ExMethod
    public static boolean isBlankText(String text) {
        return text == null || text.trim().isEmpty();
    }
}
```

在调用处导入扩展方法的声明类：

```java
import demo.TextExtensions;

boolean empty = "  ".isBlankText(); // 等价于 TextExtensions.isBlankText("  ")
```

还支持静态扩展、泛型、方法引用、`cover` 方法覆盖和 `filterAnnotation` 注解约束。详见[扩展方法文档](mds/README_ZrExMethod.md)。

![扩展方法演示](others/exmethod_show4.gif)

### 可选链与 Elvis 表达式

```java
String text = null;
String trimmed = text?.trim();          // null
String display = text?.trim() ?: "默认值";
Integer count = null;
int size = count ?: 0;
```

`?.` 在接收者为 `null` 时短路当前链；`?:` 在左侧结果为 `null` 时使用右侧默认值。若可选链最终返回基本类型，应使用 `?:` 提供默认值，避免空值路径抛出空指针异常。

括号会影响短路范围，赋值左侧也有专门的规则，详见[可选链与 Elvis 文档](mds/README_ZrOptionalChaining.md)。

### 模板字符串

```java
String name = "Zircon";
int age = 7;
String greeting = $"Hello, ${name.toUpperCase()}!";
String formatted = f"Age: ${%02d:age}"; // Age: 07
```

`$"…"` 使用字符串拼接；`f"…"` 支持 `String.format` 格式符。复杂表达式建议放在 `${…}` 中，详见[模板字符串文档](mds/README_ZrString.md)。

## 快速接入

先配置构建依赖，再按需安装编辑器插件。javac 插件负责实际编译，编辑器插件提供补全、导航和语义检查。

从 **3.3.3** 起，四个编译模块发布到 Maven Central，统一使用 `io.github.122006.Zircon`。
Gradle 只需 `mavenCentral()`，Maven 默认即可解析，无需添加额外仓库。
JitPack 的 `io.github.122006` 别名也使用这些模块坐标；旧版本是否可用取决于 JitPack 的构建产物。
维护者发布流程见 [Maven Central 发布指南](gradle/CENTRAL_PUBLISHING.md)。

### Gradle（Groovy DSL）

单模块 Java 项目可在 `build.gradle` 中加入：

```groovy
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'io.github.122006.Zircon:gradle:3.3.3'
    }
}

apply plugin: 'java'
apply plugin: 'zircon'

repositories {
    mavenCentral()
}
```

多模块项目将 `buildscript` 放在根项目中，在需要 Zircon 的模块中应用 `zircon` 插件，并确保模块的依赖仓库包含 `mavenCentral()`。已有 Java 或 Android 插件配置的模块保留原配置即可。

Gradle 插件会添加 Zircon 依赖及 `ZrOptionalChain`、`ZrExMethod`、`ZrString` 编译参数。配置后重新同步项目。

### Maven

将以下配置合并到 `pom.xml`。这里以 Java 8 为编译目标，可按项目需要调整：

```xml
<properties>
    <zircon.version>3.3.3</zircon.version>
    <maven.compiler.source>8</maven.compiler.source>
    <maven.compiler.target>8</maven.compiler.target>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.122006.Zircon</groupId>
        <artifactId>javac</artifactId>
        <version>${zircon.version}</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>io.github.122006.Zircon</groupId>
        <artifactId>zircon</artifactId>
        <version>${zircon.version}</version>
    </dependency>
    <dependency>
        <groupId>io.github.122006.Zircon</groupId>
        <artifactId>base</artifactId>
        <version>${zircon.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <compilerArgs>
                    <arg>-Xplugin:ZrOptionalChain</arg>
                    <arg>-Xplugin:ZrExMethod</arg>
                    <arg>-Xplugin:ZrString</arg>
                </compilerArgs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

如果项目已配置 `annotationProcessorPaths`，还需把同版本的 `io.github.122006.Zircon:javac` 加入该路径，并保留已有处理器。只安装编辑器插件无法让 Maven 或 CI 识别 Zircon 语法。

## 编辑器支持

### IntelliJ IDEA

推荐下载 [Zircon IDEA 插件 4.9](ijplugin/build/distributions/ijplugin-4.9.zip)，在 **Settings → Plugins → 齿轮 → Install Plugin from Disk…** 中选择 ZIP，安装后重启 IDEA。也可在 [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/19146-zircon) 搜索 Zircon；市场版本可能因审核而滞后。

只需安装一个插件 ZIP。包内保留旧版 `ZrClassLoaderHelper` 加载机制，并为新版 IDEA 提供 Syntax API 适配。

声明兼容范围为 IDEA 2021.2–2026.1（build `212–261.*`）。已有 Plugin Verifier 检查覆盖 2022.3.3、2024.2.1、2025.3.1.1、2026.1.4；2025.3.1.1 与 2026.1.4 各通过 8 项语法回归测试。2021.2 尚未实测，静态兼容检查也不等同于全部编辑器功能验证。

构建、测试和版本适配说明见 [IDEA 插件 README](ijplugin/README.md)。

### VS Code

先安装 [Language Support for Java by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java)（`redhat.java`），并按它的要求配置 Java Language Server 的 JDK。

从源码构建 VSIX，在仓库根目录执行：

```powershell
.\gradlew.bat :vscode_plugin_agent:packageVsix
```

在 VS Code 扩展视图中选择 **Install from VSIX…**，安装 `vscode_plugin/zircon-vscode-<version>.vsix`。打开已配置 Zircon 的项目，按提示重启 Java Language Server。

扩展通过 JDT Agent 接入原生补全、引用、重命名、CodeLens 和调用层次，并提供语法检查、意图操作、批量转换、格式化和导入优化。构建环境、命令和排查方式见 [VS Code 扩展 README](vscode_plugin/README.md)。

## 语法文档

- [扩展方法](mds/README_ZrExMethod.md)：声明、导入、泛型、覆盖规则与注解约束。
- [可选链与 Elvis 表达式](mds/README_ZrOptionalChaining.md)：短路、默认值、赋值与括号边界。
- [模板字符串](mds/README_ZrString.md)：插值、格式符、引号与表达式范围。
- [更新记录](CHANGELOG.md)：3.3.3、IDEA 插件及历史版本变更。

## 常见问题

**安装编辑器插件后，命令行构建仍报语法错误？**

检查项目是否引入同版本的 Zircon 编译依赖，以及 javac 是否加载了上述三个插件。VS Code 使用 JDT 提供编辑服务，项目构建仍需使用配置好的 javac。

**扩展方法没有提示或无法解析？**

确认方法是 `static`、声明了 `@ExMethod`，并在调用文件中导入声明类；还应检查接收者类型、泛型约束和 `filterAnnotation`。添加或更新依赖后重新同步项目。VS Code 可执行 `Zircon: 查看项目状态` 和 `Zircon: 刷新扩展方法索引`。

**能直接使用 `List.create(...).map(...)` 吗？**

Zircon 本身不预置扩展方法。可自行声明，或引入 [ExMethodUtil](https://github.com/122006/ExMethodUtil) 并导入对应的扩展声明类：

```groovy
implementation 'com.github.122006:ExMethodUtil:1.1.8'
```

`ExMethodUtil` 是单独的旧扩展库，仍需 JitPack 仓库；上述 Zircon 3.3.3 编译依赖本身只需 Maven Central。

## 参与开发

基础语法与字符串拆分位于 `base`，javac 注入位于 `javac` 和 `inject_java*`；编辑器代码分别位于 `ijplugin*`、`vscode_plugin` 和 `vscode_plugin_agent`。

请使用仓库的 Gradle Wrapper。IDEA 与 VS Code 的构建步骤分别见各自 README；[编译回归用例](test/src/test/java/test)可用于查阅语法行为。

## 许可证

[Apache License 2.0](LICENSE)
