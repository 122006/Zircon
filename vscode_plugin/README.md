# Zircon VSCode Extension

Zircon 的 VSCode 扩展，面向 Java 项目提供一版可运行的 MVP 编辑器支持。

## 当前能力

- 自动检测当前工作区是否为 Java / Zircon 项目
- 通过 `java.jdt.ls.vmargs` 注入 Zircon JDT Agent
- 对 Zircon 模板字符串、`?.`、`?:` 提供基础诊断与语义高亮
- 基于源码扫描建立 `@ExMethod` 索引
- 为扩展方法提供基于继承、字面量、`new`、链式调用、类接收者 / `.class` / `::` 的补全与签名提示
- 为扩展方法提供更强的 `hover`、定义跳转、引用查询和工作区符号搜索

## 内置命令

- `Zircon: 重新注入 Java Agent`
- `Zircon: 查看项目状态`
- `Zircon: 重启 Java Language Server`
- `Zircon: 刷新诊断`

## 主要配置

- `zircon.enable`
- `zircon.autoInjectJavaAgent`
- `zircon.onlyInjectWhenProjectUsesZircon`
- `zircon.javaAgentConfigurationTarget`
- `zircon.enableDiagnostics`
- `zircon.enableSemanticHighlighting`
- `zircon.enableStatusBar`
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

当前版本优先完成“源码级索引 + 编辑器交互 + Agent 注入骨架”的闭环；`@ExMethod` 已补强轻量级接收者推断与继承匹配，但真正的 JDT 语义级扩展方法绑定仍在后续演进范围内。
