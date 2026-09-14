# Maven Central 发布

四个模块通过 Maven Central 发布，组名统一为 `io.github.122006.Zircon`：

| groupId | artifactId | 说明 |
| --- | --- | --- |
| `io.github.122006.Zircon` | `zircon` | 注解 |
| `io.github.122006.Zircon` | `base` | 共享语法模型 |
| `io.github.122006.Zircon` | `javac` | 编译器插件，POM 传递引用同版本 `base` 和 `zircon` |
| `io.github.122006.Zircon` | `gradle` | Gradle 接入插件 |

IDEA、VS Code 插件及 Gradle 短插件 ID 的 marker 不在此发布包中。使用 `buildscript` 引入 `gradle` 模块，再 `apply plugin: 'zircon'`，详见项目 README。

## 1. 构建与验证

在干净的发布工作树中构建。`build.gradle` 是版本的唯一来源；Gradle 插件自动把同一版本写入资源文件，不需要修改默认版本常量。

```powershell
.\gradlew.bat -PcentralRelease stageCentral --no-daemon
```

Gradle 使用 JDK 17 运行，编译器适配模块分别使用 JDK 8、11、17、25 工具链；发布字节码目标均为 Java 8。JDK 25 工具链编译 `inject_java24` 的两个方法查找类，供 JDK 24/25 使用；其余类复用 `java16`。发布构建不配置 IDE 插件和演示项目。`javac` 包中的 `.clazz` 从源码重新生成，源码包同时包含适配模块源码，开发目录中的已跟踪资源不会被覆盖。

Gradle 插件回归中的 JDK 25 场景使用 Gradle 9.1.0，首次运行需要下载发行版；可用 `-PzirconModernGradleHome=/path/to/gradle-9.1.0` 指定本地安装。发布前应另行运行 [编译器回归矩阵](../javac/src/test/README.md)，包括 javac 25 编译目标 21、25 和 `--release 21`。

输出为 `build/central/repository`，只包含四个模块的 Maven 发布任务。构建会运行基础解析和 Gradle 插件回归测试。

独立 Gradle 验证（依次设置 `smokeJava` 为 `8`、`11`、`17`、`23`）：

```powershell
.\gradlew.bat -p gradle/central-smoke clean smoke `
  '-PzirconRepository=../../build/central/repository' `
  '-PzirconVersion=3.3.5' '-PsmokeJava=8'
```

如果工具链不在 Gradle 自动发现路径中，传入 `-Porg.gradle.java.installations.paths=...`。该独立项目只从指定仓库解析 Zircon，验证扩展方法、可选链短路、Elvis 默认值和模板字符串。

独立 Maven 验证（在 JDK 8 和 17 分别运行，仓库参数使用实际绝对文件 URI）：

```powershell
mvn -f gradle/central-smoke/pom.xml clean package `
  '-DzirconRepository=file:///absolute/path/to/build/central/repository/' `
  '-DzirconVersion=3.3.5'
java -cp gradle/central-smoke/target/classes smoke.Main
```

## 2. 签名和生成发布包

签名公钥保存在 [keys/zircon-central.asc](keys/zircon-central.asc)，指纹为：

```text
582E8FEC363958282843D0AE374A1352D1A8AD02
```

公钥已上传 `keyserver.ubuntu.com`，有效期至 2028-09-07。更新有效期后须重新上传公钥。

私钥、撤销证书和口令必须保存在仓库外的安全位置，不得提交到 Git。公开文档仅提供公钥和签名验证信息。

PowerShell 7.3+ 打包示例（路径为占位符，须替换为本机仓库外的位置）：

```powershell
.\gradle\central-bundle.ps1 `
  -SigningKey '582E8FEC363958282843D0AE374A1352D1A8AD02' `
  -GpgExecutable 'gpg' `
  -GpgHome '<GPG 主目录>' `
  -PassphraseFile '<DPAPI 加密口令文件>'
```

按本机配置指定 GPG 可执行文件和主目录。`PassphraseFile` 使用当前 Windows 用户的 DPAPI 加密格式；使用已解锁的 GPG agent 时可省略该参数。脚本不上传私钥，也不会读取 Central 登录凭据。

脚本检查坐标、POM 元数据、传递依赖、源码/Javadoc 内容及 Java 8 字节码，然后对 JAR、POM、`.module` 逐一签名并验证，生成 MD5/SHA1/SHA256/SHA512。ZIP 只包含指定版本的四个模块，不携带仓库根元数据、密钥或测试文件。

输出：`build/central/zircon-3.3.5-central.zip`。输出目录已存在时脚本停止，以免覆盖已审核的包；重试前把旧输出移走。

## 3. 发布

1. 登录 [Central Portal](https://central.sonatype.com/publishing)，确认 `io.github.122006` 为 Verified。
2. 点击 **Publish Component**，上传签名 ZIP。
3. 等待服务器校验通过，检查四个模块的名称和版本，再点击 **Publish**。
4. 等待 Published，并从 [Central 仓库](https://repo.maven.apache.org/maven2/io/github/122006/Zircon/) 验证 POM/JAR 可下载。Central 搜索索引可能晚于实际仓库文件同步。
5. 推送对应代码与版本标签。已发布的版本不可覆盖；内容有改动须使用新版本。

后续可使用 [Central Publisher API](https://central.sonatype.org/publish/publish-portal-api/) 自动上传。令牌应存入本机安全配置或 CI Secrets，不提交仓库；首次发布使用网页上传，无需创建 API Token。
