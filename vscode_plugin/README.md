# Zircon VSCode Extension

Zircon 的 VSCode 扩展，面向 Java 项目提供一版可运行的 MVP 编辑器支持。

## 当前能力

- 自动检测当前工作区是否为 Java / Zircon 项目
- 通过 `java.jdt.ls.vmargs` 注入 Zircon JDT Agent
- 对 Zircon 模板字符串、`?.`、`?:` 提供诊断与语义高亮；字符串边界统一使用基础模块同源的 `TemplateStringSplitter`
- 提供 `@ExMethod` 修饰符/接收者检查、格式化类型检查和可选链原始类型结果检查，并附带快速修复
- 基于源码扫描建立 `@ExMethod` 索引
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

- `zircon.enable`
- `zircon.autoInjectJavaAgent`
- `zircon.onlyInjectWhenProjectUsesZircon`
- `zircon.javaAgentConfigurationTarget`
- `zircon.enableDiagnostics`
- `zircon.enableSemanticHighlighting`
- `zircon.enableStatusBar`
- `zircon.enableCodeActions`
- `zircon.enableEditorExperience`
- `zircon.debug`

## 开发方式

1. 在 `vscode_plugin` 目录执行 `npm install`
2. 执行 `npm run compile`
3. 如需同时更新 Agent，回到仓库根目录执行 `./gradlew.bat :vscode_plugin_agent:shadowJar`
4. 在 VSCode 中按 `F5` 启动扩展开发宿主

## 启动开发宿主

在仓库根目录执行：

```bash
.\gradlew.bat :vscode_plugin:launchVscodeDevHost
```

默认使用 `D:\VSCode\Code.exe` 与工作区根目录下的 `.vscode-devhost-data`。如需覆盖 VSCode 路径，可追加 `-PcodeExe=你的Code.exe路径`。

## 打包

在 `vscode_plugin` 目录执行：

```bash
npm run package
```

打包产物为 `zircon-vscode-<version>.vsix`。

## 说明

扩展方法补全与引用优先复用 JDT CompletionEngine / SearchEngine；Agent 直接查询 JDT 已持久化的 `@ExMethod` 注解引用索引，只解析命中的声明类，并由 CompletionEngine 生成签名、泛型和自动 import。TypeScript 索引负责快速交互、意图操作和 Agent 不可用时的兼容回退。工作区源码保持即时索引；非补全操作只从当前文件显式 import 的依赖类（含静态与通配 import）加载扩展方法。只有 Agent 不可用时，成员补全才回退到 TypeScript 全量依赖索引。依赖索引不截断 Java 文件、JAR、JAR 条目或扩展类数量，并通过 ZIP 中央目录、JAR 时间戳缓存和类文件注解预筛选控制开销。
