# Gradle 插件接入与测试

使用 `mavenCentral()` 获取 `io.github.122006.Zircon:gradle:3.3.5`，并在项目中应用 `zircon` 插件。

## 通过 Maven Central 接入

在根项目声明构建依赖，在使用 Zircon 的模块应用插件：

```groovy
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'io.github.122006.Zircon:gradle:3.3.5'
    }
}

apply plugin: 'java' // Android 项目保留已有的 Android 插件
apply plugin: 'zircon'

repositories {
    mavenCentral()
}
```

插件会自动添加 `io.github.122006.Zircon` 下与插件版本一致的 `javac`、`base`、`zircon`。
支持在根项目声明 classpath、在子模块应用插件。构建依赖仓库和模块依赖仓库都需要配置 `mavenCentral()`。

## 显式指定依赖版本

默认依赖版本与插件保持一致。如需显式指定，可在 `apply plugin: 'zircon'` 后配置：

```groovy
zircon {
    version.set('3.3.5')
}
```

Kotlin DSL 使用同一个配置类型：

```kotlin
configure<com.by122006.zircon.ZirconExtension> {
    version.set("3.3.5")
}
```

也可在应用插件前设置 `zircon_version` 项目属性；`zircon {}` 中的显式配置优先。组名统一使用 `io.github.122006.Zircon`。

## 依赖如何注入

| 路径 | Zircon 模块 | 用途 |
| --- | --- | --- |
| `annotationProcessor`、`*AnnotationProcessor` | `javac`、`base`、`zircon` | javac 插件、解析器及编译时需要的类型 |
| `implementation`、`*Implementation` | `zircon` | 注解及可选链需要的运行时辅助类型 |

插件通过 `zirconCompiler` 和 `zirconRuntime` 两个不可解析、不可发布的依赖配置共享依赖。
配置在创建时接入，因此支持 Java/Android 插件的不同应用顺序、自定义 source set，以及后创建的 Android variant、单元测试和 instrumentation test 配置。

插件保留现有注解处理器、编译参数和 JVM 参数。
如果项目还手动声明了 Zircon 编译模块，请统一使用 `io.github.122006.Zircon` 和相同版本，避免依赖冲突。

JDK 模块访问参数按 `JavaCompile` 实际使用的 toolchain 生成，而不是按启动 Gradle 的 JDK 或 `sourceCompatibility` 判断。
支持 JVM 参数 provider 的 Gradle 会将其作为任务输入；旧 Gradle 在项目配置结束后设置参数，不在 `doFirst` 中反复追加。

## 回归测试

```shell
./gradlew -PcentralRelease :gradle_plugin:test :gradle_plugin:validatePlugins
```

测试使用 JDK 17 运行 TestKit，在临时 Maven 仓库放入本地构建的 jar，再启动实际 Gradle 项目。
Java 编译任务分别使用 JDK 8、11、17、25，验证扩展方法、可选链、模板字符串、其他注解处理器、自定义 source set、预期编译失败，以及配置缓存和 up-to-date 复用。

JDK 25 场景单独使用 Gradle 9.1.0（首个正式支持 JDK 25 的 Gradle 版本），分别设置 source/target 为 21、25，并校验生成 class 的版本。默认首次下载该发行版；可用 `-PzirconModernGradleHome=/path/to/gradle-9.1.0` 指定已有安装，或用 `-PzirconModernGradleVersion=...` 选择其他兼容版本。外层仓库仍使用现有 Wrapper 和 JDK 17，不影响旧版本构建。
还覆盖分支/提交版本、Central 坐标、父项目继承、插件 DSL、应用后覆盖坐标和 Android 依赖配置的作用域。
如果设置 `-PzirconAndroidSdk`，还会尝试用 AGP 4.0.2 编译 Android 主模块、单元测试和 instrumentation test；本测试要求 SDK 中有 `platforms/android-29` 和 `build-tools/29.0.2`，缺少时会跳过。

本次已验证 Gradle 8.14.4 配合 JDK 8/11/17 编译及配置缓存复用，以及 Gradle 6.7.1 配合 JDK 8/11 编译。本机缺少旧 Android SDK/build-tools，AGP 4.0.2 的实际编译测试未完成；Android 配置作用域由单元测试覆盖。

可额外运行旧 Gradle 的 JDK 8/11 编译测试：

```shell
./gradlew -PcentralRelease :gradle_plugin:test -PzirconLegacyGradleVersion=6.7.1
```

首次运行可能下载该 Gradle 发行版，也可以用 `-PzirconLegacyGradleHome=/path/to/gradle-6.7.1` 指定已有安装。
这里的 `-PcentralRelease` 只用于加载编译模块及适配层；上述命令只构建测试所需的 jar，不执行发布任务。
测试报告位于 `gradle_plugin/build/reports/tests/test/index.html`。
