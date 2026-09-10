# Gradle 插件接入与测试

本文说明当前分支的 Gradle 插件行为。已发布的 `3.3.3` 插件不会因分支提交而更新。

## 通过 JitPack 测试分支

在根项目声明构建依赖，在使用 Zircon 的模块应用插件：

```groovy
buildscript {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.github.122006.Zircon:gradle:master-SNAPSHOT'
    }
}

apply plugin: 'java' // Android 项目保留已有的 Android 插件
apply plugin: 'zircon'

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

插件会使用 `com.github.122006.Zircon` 下同一版本的 `javac`、`base`、`zircon`。
也识别 `io.github.122006.Zircon` 坐标，支持在父项目声明 classpath、在子模块应用插件，以及通过 plugin marker 使用 `plugins {}`。
插件不自动添加仓库：构建依赖仓库和模块依赖仓库都需要配置 JitPack。

使用 JitPack 的 `io.github.122006` 别名时，也可以声明：

```groovy
classpath 'io.github.122006.Zircon:gradle:master-SNAPSHOT'
```

自动注入的 `javac`、`base`、`zircon` 会保留 `io.github.122006.Zircon` 组名和插件实际解析到的版本。
使用提交 SHA 时同理；分支和提交版本需要从 JitPack 获取，Maven Central 提供已发布的版本。

`master-SNAPSHOT` 是变化中的分支。重新测试最新提交时，在项目原有的编译命令后加 `--refresh-dependencies`；复现某次结果时，使用该次提交的 SHA 作为版本。

## 显式覆盖坐标

可以在 `apply plugin: 'zircon'` 后配置，依赖解析时才读取：

```groovy
zircon {
    group.set('com.github.122006.Zircon')
    version.set('master-SNAPSHOT')
}
```

Kotlin DSL 使用同一个配置类型：

```kotlin
configure<com.by122006.zircon.ZirconExtension> {
    group.set("com.github.122006.Zircon")
    version.set("master-SNAPSHOT")
}
```

显式扩展配置优先，其次是应用插件前设置的 `zircon_group` / `zircon_version` 项目属性。
默认读取 buildscript classpath 最终选中的插件模块，包含 Gradle 版本冲突处理的结果；父项目的插件类加载器优先。
无法从模块解析结果取到坐标时，才回退到 classpath 声明和 jar 内版本。单独设置 `zircon_version` 会保留推断出的组名；跨仓库切换时请同时设置组名。

不需要修改根项目的发布版本，也不需要创建 tag 或 Release。

## 依赖如何注入

| 路径 | Zircon 模块 | 用途 |
| --- | --- | --- |
| `annotationProcessor`、`*AnnotationProcessor` | `javac`、`base`、`zircon` | javac 插件、解析器及编译时需要的类型 |
| `implementation`、`*Implementation` | `zircon` | 注解及可选链需要的运行时辅助类型 |

插件通过 `zirconCompiler` 和 `zirconRuntime` 两个不可解析、不可发布的依赖配置共享依赖。
配置在创建时接入，因此支持 Java/Android 插件的不同应用顺序、自定义 source set，以及后创建的 Android variant、单元测试和 instrumentation test 配置。
Android 测试运行时不再自动添加 `javac`，应用运行时不再自动添加 `base`。

插件保留现有注解处理器、编译参数和 JVM 参数。
JitPack 生成的 `javac` POM 即使仍引用 Central 的 `3.3.3`，其中的 `base`、`zircon` 边也会被同组同版本的显式依赖替代；其他传递依赖保留。
如果项目还手动声明了旧的 Zircon 编译模块，应统一这些声明，避免同时引入不同组名的重复类。

JDK 模块访问参数按 `JavaCompile` 实际使用的 toolchain 生成，而不是按启动 Gradle 的 JDK 或 `sourceCompatibility` 判断。
支持 JVM 参数 provider 的 Gradle 会将其作为任务输入；旧 Gradle 在项目配置结束后设置参数，不在 `doFirst` 中反复追加。

## 回归测试

```shell
./gradlew -PcentralRelease :gradle_plugin:test :gradle_plugin:validatePlugins
```

测试使用 JDK 17 运行 TestKit，在临时 Maven 仓库放入本地构建的 jar，再启动实际 Gradle 项目。
Java 编译任务分别使用 JDK 8、11、17，验证扩展方法、可选链、模板字符串、其他注解处理器、自定义 source set、预期编译失败，以及配置缓存和 up-to-date 复用。
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
