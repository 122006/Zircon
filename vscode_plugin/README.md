# Zircon VS Code 扩展

为 Java 项目提供 Zircon 扩展方法、模板字符串、可选链和 Elvis 表达式的编辑器支持，原生补全与搜索通过 JDT Agent 接入。

当前扩展版本为 **0.0.1**，仍在开发中；Zircon 编译依赖版本为 **3.3.5**。项目接入说明见[项目首页](../README.md)。

## 安装与启用

先安装 VS Code 1.75 或更新版本，以及 [Language Support for Java by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java)（`redhat.java`），并配置满足该扩展要求的 Java Language Server JDK。

选择以下任意一种方式获取安装包。

### 方式一：使用已编译的 VSIX

直接使用已编译的 `vscode_plugin/zircon-vscode-<version>.vsix` 安装包。

### 方式二：从源码构建 VSIX

准备好[构建环境](#从源码构建)后，在仓库根目录执行：

```powershell
.\gradlew.bat :vscode_plugin_agent:packageVsix
```

macOS / Linux 使用 `./gradlew` 替换 `.\gradlew.bat`。构建产物位于 `vscode_plugin/zircon-vscode-<version>.vsix`。

### 安装步骤

1. 在 VS Code 扩展视图中打开 **… → Install from VSIX…**，选择通过上述任一方式获取的 VSIX 文件。
2. 打开已配置 Zircon 编译依赖的 Java 项目，按提示重启 Java Language Server。
3. 执行 `Zircon: 查看项目状态`，确认项目检测和 Agent 注入状态。

默认只对检测到 Zircon 的项目自动注入 Agent，配置写入工作区的 `java.jdt.ls.vmargs`。扩展方法调用仍需导入对应的声明类。

## 当前能力

- 自动检测当前工作区是否为 Java / Zircon 项目
- 通过 `java.jdt.ls.vmargs` 注入 Zircon JDT Agent
- 对 Zircon 模板字符串、`?.`、`?:` 提供诊断与语义高亮；字符串边界统一使用基础模块同源的 `TemplateStringSplitter`
- 提供 `@ExMethod` 修饰符/接收者检查、格式化类型检查和可选链原始类型结果检查，并附带快速修复
- 复用 JDT 的 `@ExMethod` 注解引用索引发现依赖方法，并维护工作区源码索引
- 为扩展方法提供基于继承、字面量、`new`、链式调用、类接收者 / `.class` / `::` 的补全与签名提示
- 为扩展方法提供 `hover`、定义跳转、JDT 原生引用/调用层次/CodeLens，以及覆盖扩展调用和显式静态调用的安全重命名
- 提供扩展调用与普通静态调用的双向意图转换、声明跳转、导入修复和工作区批量转换
- 格式化优先复用 JDT/Eclipse formatter；遇到 Zircon 模板字符串、可选链或 Elvis 表达式时使用可逆代理保护语法
- 导入优化复用 JDT organize-imports，并在 JDT 不可用时安全排序、去重连续 import 块
- 提供 `String.format`、Java 字符串拼接、普通字符串与 Zircon 模板字符串之间的快速转换
- 提供当前文件和整个工作区的批量语法转换
- 输入 `?.` 后自动触发成员补全，输入 `${` 时自动补全 `}`
- 模板文本中按回车会保留模板前缀并拆成可读的字符串拼接；模板表达式内提供正确的引号配对

## 内置命令

- `Zircon: 重新注入 Java Agent`
- `Zircon: 查看项目状态`
- `Zircon: 重启 Java Language Server`
- `Zircon: 刷新诊断`
- `Zircon: 验证 JDT 原生引用查询`
- `Zircon: 转换当前文件为模板字符串`
- `Zircon: 转换当前文件模板为普通 Java`
- `Zircon: 批量转换工作区为模板字符串`
- `Zircon: 批量转换工作区模板为普通 Java`
- `Zircon: 批量转换为扩展方法调用`
- `Zircon: 批量还原为普通静态调用`
- `Zircon: 刷新扩展方法索引`
- `Zircon: 格式化当前 Java 文件`
- `Zircon: 优化 Java 导入`

## 主要配置

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `zircon.enable` | `true` | 启用扩展 |
| `zircon.enableExperimentalJavaAgent` | `true` | 启用 JDT Agent |
| `zircon.autoInjectJavaAgent` | `true` | 自动配置 Agent 参数 |
| `zircon.onlyInjectWhenProjectUsesZircon` | `true` | 仅对 Zircon 项目自动注入 |
| `zircon.javaAgentConfigurationTarget` | `workspace` | Agent 配置作用域，可选 `global` |
| `zircon.enableDiagnostics` | `true` | Zircon 诊断 |
| `zircon.enableSemanticHighlighting` | `true` | 语义高亮 |
| `zircon.enableStatusBar` | `true` | 状态栏提示 |
| `zircon.enableCodeActions` | `true` | 快速修复与意图操作 |
| `zircon.enableEditorExperience` | `true` | 回车、引号与模板括号输入支持 |
| `zircon.debug` | `false` | 调试日志 |

完整配置见 [package.json](package.json)。

## 从源码构建

建议使用 Node.js 22、JDK 21 和仓库的 Gradle Wrapper。Agent 使用 JDK 17 工具链，编译依赖还需要 JDK 8 工具链；Gradle 按项目配置选择或下载。

在仓库根目录执行：

```powershell
.\gradlew.bat :vscode_plugin_agent:buildZirconVsCodePlugin
```

该任务安装 npm 锁定依赖、编译并测试 TypeScript、构建 Agent，并将以下两个 JAR 复制到 `vscode_plugin/server`：

- `zircon-agent.jar`
- `zircon-agent-bootstrap.jar`

只运行 `shadowJar` 不会同步扩展实际加载的两个 JAR。修改 Agent 或基础字符串拆分代码后，应重新运行完整构建任务。

仅修改 TypeScript 时，可在 `vscode_plugin` 目录执行：

```powershell
npm ci
npm test
npm run watch
```

macOS / Linux 的 Gradle 命令使用 `./gradlew`。

## 启动开发宿主

完成构建后，在仓库根目录执行以下命令，将最后一个参数替换为待测试项目目录：

```powershell
code --new-window --extensionDevelopmentPath="$PWD/vscode_plugin" "D:/path/to/your-java-project"
```

该方式使用当前 VS Code 的配置和扩展，请先安装 `redhat.java`。

仓库还提供 Windows 隔离宿主任务：

```powershell
.\gradlew.bat :vscode_plugin:launchVscodeDevHost "-PcodeExe=D:/path/to/Code.exe" "-PworkspaceDir=D:/path/to/your-java-project"
```

它会先构建扩展，并从用户扩展目录复制 `redhat.java`。用户数据保存在测试项目父目录的 `.zircon-vscode-devhost-data`，隔离扩展保存在同级 `.zircon-vscode-devhost-extensions`。

未传参数时，该辅助任务默认使用 `D:/VSCode/Code.exe` 和 `D:/IdeaProjects/Zircon/ZirconTest`；这些是开发宿主任务的默认路径，使用自己的环境时应显式覆盖。

## 测试与打包

在仓库根目录执行完整打包：

```powershell
.\gradlew.bat :vscode_plugin_agent:packageVsix
```

产物位于 `vscode_plugin/zircon-vscode-<version>.vsix`。该任务包含前述扩展构建；如果已经同步了最新 Agent，也可在 `vscode_plugin` 中执行 `npm run package`。

TypeScript 回归使用 `npm test`，Agent 测试使用：

```powershell
.\gradlew.bat :vscode_plugin_agent:test
```

原生 JDT 集成探针可在 `vscode_plugin` 中运行 `npm run probe:indexed-completion`、`npm run probe:native-search`、`npm run probe:template`。探针需要已安装的 `redhat.java` 和适用的 JDK；环境配置示例见 [CI 工作流](../.github/workflows/vscode-plugin.yml)。

## 常见问题

### 项目已就绪，但扩展方法没有补全？

先确认调用文件已导入声明类、方法满足 `@ExMethod` 约束，然后执行 `Zircon: 查看项目状态`。添加或更新依赖后可执行 `Zircon: 刷新扩展方法索引`。

### 更新 Agent 后没有生效？

运行完整扩展构建，确认两个 JAR 已同步，再执行 `Zircon: 重新注入 Java Agent` 并按提示重启 Java Language Server。

### 编辑器正常，Gradle / Maven 仍报错？

JDT Agent 只影响 Java Language Server。实际构建还需按[项目首页](../README.md)配置 Zircon 的 javac 插件。

## 索引与原生集成

扩展方法补全与引用优先复用 JDT CompletionEngine / SearchEngine；Agent 直接查询 JDT 已持久化的 `@ExMethod` 注解引用索引，只解析命中的声明类，并由 CompletionEngine 生成签名、泛型和自动 import。TypeScript 索引负责快速交互、意图操作和 Agent 不可用时的兼容回退。工作区源码保持即时索引；非补全操作只从当前文件显式 import 的依赖类（含静态与通配 import）加载扩展方法。只有 Agent 不可用时，成员补全才回退到 TypeScript 全量依赖索引。依赖索引不截断 Java 文件、JAR、JAR 条目或扩展类数量，并通过 ZIP 中央目录、JAR 时间戳缓存和类文件注解预筛选控制开销。
