# Zircon IntelliJ IDEA 插件

为 Java 提供扩展方法、模板字符串、可选链和 Elvis 表达式的编辑器支持。项目仍需配置 Zircon 编译依赖，接入步骤见[项目首页](../README.md)。

当前 IDEA 插件版本为 **4.9**，编译依赖版本为 **3.3.5**，两者独立管理。

## 安装与兼容范围

下载 [ijplugin-4.9.zip](build/distributions/ijplugin-4.9.zip)，在 **Settings → Plugins → 齿轮 → Install Plugin from Disk…** 中选择 ZIP，重启 IDEA。

只发布 `:ijplugin` 生成的一个 ZIP，使用者无需按 IDEA 版本选择或组合内部模块。

| IDEA build | 包内适配方式 |
| --- | --- |
| 212–239 | `ZrClassLoaderHelper` 动态加载内嵌的 `ijplugin_223` 解析器 |
| 240–252 | `ZrClassLoaderHelper` 动态加载内嵌的 `ijplugin_241` 解析器 |
| 253+ | modern content module 接入 Syntax API |
| 261+ | 额外加载 modern-highlighting 模块，适配 CodeServer 高亮接口 |

## 构建环境

使用 **JDK 21** 运行仓库的 Gradle Wrapper。构建涉及 JDK 8、17、21 工具链，Gradle 按各模块配置选择或下载对应 JDK；首次构建还会下载 IDEA 平台及依赖。

发布包中可被旧版 IDEA 加载的代码保持 Java 11 字节码。构建所用 JDK 与用户项目的 Java 语言版本是不同配置。

以下命令均在仓库根目录执行；macOS / Linux 将 `.\gradlew.bat` 换成 `./gradlew`。

## 构建与验证

生成插件安装包：

```powershell
.\gradlew.bat :ijplugin:buildPlugin
```

产物为 `ijplugin/build/distributions/ijplugin-4.9.zip`。该任务会从编译输出嵌入旧版解析器 `.clazz`，并执行 IDEA 261 测试。

完整发布前检查：

```powershell
.\gradlew.bat releaseIjPlugin
```

该任务运行检查、构建唯一 ZIP，并执行多版本 Plugin Verifier；不会上传插件市场。

单独运行语法回归：

```powershell
.\gradlew.bat :ijplugin:test :ijplugin_261:test --tests com.by122006.zircon.ijplugin.ZrSyntaxCompatibilityTest
```

Verifier 报告位于 `ijplugin/build/reports/pluginVerifier`；测试结果位于对应模块的 `build/test-results/test`。

已有本地 IDEA 安装时，可为上述命令追加属性，避免重复下载平台：

| Gradle 属性 | 对应平台与用途 |
| --- | --- |
| `-Pzircon.idea223.path=<IDEA 安装目录>` | 2022.3.3 |
| `-Pzircon.idea242.path=<IDEA 安装目录>` | 2024.2.1 |
| `-Pzircon.idea251.path=<IDEA 安装目录>` | 2025.1，用于编译 240–252 的旧版解析器适配 |
| `-Pzircon.idea253.path=<IDEA 安装目录>` | 2025.3.1.1 |
| `-Pzircon.idea261.path=<IDEA 安装目录>` | 2026.1.4 |

路径应指向完整的对应版本 IDEA 安装目录。依赖已缓存时可追加 `--offline`。

## 启动调试 IDE

```powershell
.\gradlew.bat :ijplugin:runIde
```

该任务打开带插件的独立 IDEA 沙盒，默认使用 2025.3.1.1。

## 内部模块约定

`ijplugin_223`、`ijplugin_241` 提供旧版解析器适配；`ijplugin_252_ex` 提供 modern 实现；`ijplugin_mix` 放置共享代码。它们由主插件统一打包。

`ijplugin_261`、`ijplugin_261_ex` 用于新版 API 编译与测试检查，不生成第二个发布包。新增兼容模块时，需同步检查 [settings.gradle](../settings.gradle)、[主插件构建配置](build.gradle)和[根构建任务](../build.gradle)。

旧版加载机制与 modern 模块的版本分支必须保留，避免在旧版 IDEA 中解析不存在的 API，或在新版中重复安装解析器。
