# 编译器回归测试与诊断

统一入口是 `:javac:compilerRegression`，包含 `:base:test` 和 `:javac:test`。Gradle 用 JDK 17 启动；本地工具链需要 JDK 8、11、17（分别编译三个适配层）。

```powershell
.\gradlew.bat -PcentralRelease -Ptjv=8 :javac:compilerRegression
.\gradlew.bat -PcentralRelease -Ptjv=11 :javac:compilerRegression
.\gradlew.bat -PcentralRelease -Ptjv=17 :javac:compilerRegression
```

`-Ptjv` 选择编译器测试进程使用的 JDK，默认 11。共享解析器单测用 JDK 8 运行，其实现也会由三个 JDK 的编译器用例覆盖。`-PcentralRelease` 只配置编译器相关模块，从源码重建适配层，不依赖仓库中预编译的 `.clazz`，也不改写这些开发资源。

普通开发模式中的 `:test:test` 和 `testWithDiffJavaVersion -Ptjv=...` 已转到同一个入口；前者也会重建所选 JDK 的适配层。原来的 `test/src/test/java` 示例由 `LegacyCompilerTest` 作为资源编译并运行，Java `assert` 已启用。Spring 独立示例 `TestClass2.java` 和全注释文件 `TextStringFormat2.java` 不属于回归用例。

## 测试约定

`testing/CompilerTestSupport` 统一管理源码、临时输出、插件初始化、诊断收集和运行时加载。`CompilerTestCase` 提供通用的编译和运行断言。测试结果分为：

- `COMPILED`：编译成功且没有 ERROR 诊断；可继续断言运行结果。
- `REJECTED`：编译器通过 ERROR 诊断正常拒绝源码。
- `CRASHED`：抛出内部异常、输出编译器堆栈，或失败但没有 ERROR 诊断；不能满足预期失败断言。

`assertRejected(n)` 校验结果类型和错误数量，随后用 `assertError(...)` 校验错误码、消息、文件、行列。框架自测同时覆盖“本应失败却编译成功”“错误码或位置不符”以及“已经产生 ERROR 后又崩溃”，避免把任何非零结果都当作负例通过。JDK 8 的 JavaCompiler API 多插件选项存在迭代问题，因此测试通过公开的 `Plugin.init(JavacTask)` 初始化所选插件。

## 新增负例

在 `javac/src/test/resources/compiler/negative/<名称>/` 下添加 `CacheCase.java` 和 UTF-8 的 `expected.properties`。源码不会进入 Gradle 的 `compileTestJava`，每个目录单独编译。`TemplateDiagnosticsTest` 自动枚举目录，默认严格要求一个错误：

```properties
code=ZR1006
anchor=%Q
length=2
message=无效的格式说明符
hint=检查格式
```

`anchor` 必须在源码中唯一，驱动据此计算文件、行列和字符偏移；`offset` 可在锚点基础上增加偏移。`length` 可选，指定时还会严格检查起止位置。Java 原生错误可设置 `javacCode=compiler.err.cant.resolve.location` 等；Zircon 诊断默认通过 `compiler.err.proc.messager` 输出，稳定的 ZR 编号包含在消息中。标准 properties 转义规则仍然适用，例如匹配反斜杠时应写双反斜杠。

编译成功但运行语义需要验证的回归放在 `TemplateArgumentCacheTest` 等正例中，使用 `assertRuns`。多个独立错误及恢复行为可以调用通用编译接口后分别断言。

JDK 8 的可选链适配层现在通过 classpath 主动加载 `zircon.BiOp`，不再把尚未导入的包误判为缺少依赖。`ZrUnSupportCodeError` 保留堆栈；传入 Context 和 AST 时补充源码位置、表达式与 JDK，加载失败还保留原始 cause。

## 当前诊断范围

| 编号 | 含义 |
| --- | --- |
| ZR1001 | 模板结束引号缺失 |
| ZR1002 | 插值表达式结束符缺失 |
| ZR1003 | 当前前缀不支持格式化说明符 |
| ZR1004 | 格式说明符后缺少冒号 |
| ZR1005 | 格式说明符后缺少表达式 |
| ZR1006 | 格式转换符、标志、宽度或精度语法无效 |
| ZR1010 | JSON 模板根结构、括号或引号错误 |
| ZR9001 | tokenizer 内部异常，保留阶段、文件、行列、JDK、原始模板和 cause |
| ZR9002 | 格式化适配器加载失败，保留类名、JDK 和 cause |

已知模板错误通过 javac 的诊断系统输出，可显示文件、行列、源码标记和修复建议；适配层跳过出错模板并继续解析。共享模型保留诊断与已解析范围，便于 IDE 处理尚未输入完整的代码。

转义后的 Java token 和词法错误位置映射回原始源码；数值 token 的类型与进制会保留，可选链生成的 token 也使用该映射。未解析变量等语义错误仍由 javac 报告。展开生成的 `String.format` 等调用没有逐字符对应的原始代码，相关错误定位到模板或对应表达式的边界。

此轮不进行运行时格式参数类型推断，也不做完整 JSON schema 校验。缺失结束符时，恢复到引号、分号或当前行边界属于尽力恢复，复杂的残缺语法仍可能产生后续 javac 诊断。ZR9001 的上下文覆盖模板扫描、切分、展开和词法处理；其他编译阶段的内部异常仍保留原有堆栈，并由测试框架识别为崩溃。
