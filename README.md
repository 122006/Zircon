# Zircon [![](https://jitpack.io/v/122006/Zircon.svg)](https://jitpack.io/#122006/Zircon)

<a href="https://github.com/122006/Zircon/releases"><img src="https://img.shields.io/github/release/122006/Zircon.svg?style=flat-square"></a>
<a href="https://plugins.jetbrains.com/plugin/19146-zircon"><img src="https://img.shields.io/jetbrains/plugin/v/19146-zircon.svg?style=flat-square"></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html"><img src="https://img.shields.io/badge/JDK-8-green.svg" alt="jdk-8" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-11-green.svg" alt="jdk-11" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-17-green.svg" alt="jdk-17" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-21-green.svg" alt="jdk-21" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk22-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-22-green.svg" alt="jdk-22" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk22-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-23-green.svg" alt="jdk-23" /></a>
-----------------

## Zircon可以让你在Java语言代码中直接使用一些特殊的语法

*  **快速接入**: 在已有java项目使用，最快**2**行代码引入。
*  **无缝衔接**: 所有新增语法和java8-23版本基础语法完全兼容，无需更换语言提升开发体验。
*  **依赖安全**: 不依赖第三方库，且构建结果为正常jar文件，无依赖传染

----------------

#### 已支持的语法特性：

### 1. 全局拓展方法

> 自由拓展已有代码的实现方法。可以实现诸如顶级方法、方法替换等功能<p>
> 特别的，支持限制仅对含有指定注解的类进行拓展（例@Service、@Repository、@Data）

![](others/exmethod_show4.gif)

### 2. 可选链 <kbd>_v3.3.0预览支持_</kbd>

`String text=XXX.returnNull()?.getText(); //不会抛出空指针异常，而是返回null`

> 简化了在中间属性可能为null时访问嵌套对象或数组的属性和方法的过程。
>
> 可选链运算符（?.）允许您在不需要显式null检查的情况下访问属性或方法。如果链中的任何中间属性为null，则表达式会短路，并将结果设置为null。
>
> 在编程中，“短路”是指当沿着正在访问的属性或方法链遇到null值时，表达式的评估会立即停止的行为。与继续评估表达式不同，结果会立即设置为null，并跳过任何后续的属性或方法访问。
>
> 如果可选链后续使用了`elvis`表达式，`elvis`表达式将同时作为可选链的默认值。 特别的，对于<kbd>单赋值语句</kbd>，链式不满足时直接跳过该语句执行

### 3. `elvis`表达式 <kbd>_v3.3.0预览支持_</kbd>

`String text= xxxx.returnNull() ?: "默认值"; //简单使用`

> 当左侧表达式返回结果为null时，返回右侧表达式的值

### 4. 内插模板字符串

`String text=$"My name is $ID.name ";//简单使用`

`String text=f"My age is ${%02d:ID.age} ";//带格式化的模板字符串`
> 字符串插值功能构建在复合格式设置功能的基础之上，提供更具有可读性、更方便的语法，用于将表达式结果包括到结果字符串。

---------------

1. 支持android、springboot、javaFX等所有使用java语言的项目（javac）

2. 支持java8~java23

---------------

### 使用说明

#### [内插模板字符串（点击跳转）](mds/README_ZrString.md)

#### [全局拓展方法（点击跳转）](mds/README_ZrExMethod.md)

> 如何定义一个拓展方法？[
*快速跳转至示例`ExMethodUtil`*](https://github.com/122006/ExMethodUtil/tree/main/impl/src/main/java/zircon/example)

#### [可选链 & `elvis`表达式（点击跳转）](mds/README_ZrOptionalChaining.md)
### 插件引入

<details>
  <summary>使用Gradle构建项目(点击展开)</summary>

#### 使用ZrString插件自动引入依赖

Step 1.在你的根项目`build.gradle`文件中进行如下操作

````
buildscript {
    repositories {
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.github.122006.Zircon:gradle:3.2.6'
    }
}
````

当前版本号：[![](https://jitpack.io/v/122006/Zircon.svg)](https://jitpack.io/#122006/Zircon)

Step 2.在需要使用插件的module的`build.gradle`首行引入插件`apply plugin: 'zircon'`

</details>
<details>
  <summary>使用Maven构建项目(点击展开)</summary>
Step 1. 增加依赖

	    <dependency>
            <groupId>com.github.122006.Zircon</groupId>
            <artifactId>javac</artifactId>
            <version>3.2.6</version>
            <scope>provided</scope>
        </dependency>
	    <dependency>
            <groupId>com.github.122006.Zircon</groupId>
            <artifactId>zircon</artifactId>
            <version>3.2.6</version>
        </dependency>

Step 2. 配置jitpack仓库

	    <repositories>
        	<repository>
        	    <id>jitpack.io</id>
        	    <url>https://jitpack.io</url>
        	</repository>
        </repositories>

当前版本号：[![](https://jitpack.io/v/122006/Zircon.svg)](https://jitpack.io/#122006/Zircon)

Step 3. 配置javac参数 `-Xplugin:ZrExMethod -Xplugin:ZrString`

        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <configuration>
            <compilerArgs>
              <arg>-Xplugin:ZrExMethod</arg>
              <arg>-Xplugin:ZrString</arg>
            </compilerArgs>
          </configuration>
        </plugin>

</details>

### 安装IDEA插件

#### 手动安装（推荐）

1. 点击 [这里\[ijplugin.zip\]](ijplugin/build/distributions/ijplugin-4.5.zip)
   进行下载（或目录中`/ijplugin/build/distributions/ijplugin-xxx.zip`文件）'
2. 下载文件后 拖动至idea中自动安装 或 idea中指定路径加载
   > For Windows & Linux - <kbd>File</kbd> > <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>齿轮图标</kbd> > <kbd>
   Install Plugin from Disk...</kbd>\
   > For Mac - <kbd>IntelliJ IDEA</kbd> > <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>齿轮图标</kbd> > <kbd>
   Install Plugin from Disk...</kbd>

#### ide内插件仓库加载

For Windows & Linux - <kbd>File</kbd> > <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search
for "Zircon"</kbd> > <kbd>Install Plugin</kbd> > <kbd>Restart IntelliJ IDEA</kbd>

For Mac - <kbd>IntelliJ IDEA</kbd> > <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search
for "Zircon"</kbd> > <kbd>Install Plugin</kbd>  > <kbd>Restart IntelliJ IDEA</kbd>

#### 网页加载

<a href="https://plugins.jetbrains.com/plugin/19146-zircon">
    <img src="https://user-images.githubusercontent.com/12044174/123105697-94066100-d46a-11eb-9832-338cdf4e0612.png" width="300"/>
</a>

### 其他注意事项

1. 请注意保持idea插件更新到最新。插件仓库审核有可能有滞后，请优先手动安装

--------------

## ChangeLog

### v3.2.6

1. 现在拓展方法会根据import列表进行导入，修复偶现的编译期问题
2. 对实例拓展方法拓展`Class<?>`时，允许省略`.class`，类似于静态方法效果但可以获得实际类型
3. 优化编译速度
4. 增加`@ExMethodIDE`注解，以增强ide的提示特性

<details>
  <summary>历史依赖更新</summary>

### v2.2

1. 重构已有代码，提高编译性能及拓展性
2. 使用gradle编译idea插件

### v2.4

1. 支持jdk11、android30

### v2.5

1. 支持内部代码段中使用不转义的引号

### v2.7

1. 不再支持使用单引号转义双引号语法
2. 支持使用gradle插件配置项目
3. 重构以支持jdk16、jdk17

### v3.0

1. 支持拓展方法

### v3.1.2

1. 支持在成员方法引用中对外部引用调用拓展方法的情况

### v3.1.3

1. 修复了一个导致编译时间过长的问题

### v3.1.4

1. gradle插件支持使用id方式引入

### v3.1.6

1. 修复一个特殊情况下与已有方法同名异参的会解析错误问题
2. 修复强制覆盖原有实现方法时，使用方法引用会提示引用重复的问题

### v3.1.8

1. 修复idea中使用Maven构建项目build错误的问题

### v3.2.0

1. 重用已解析的参数类型提高编译速度。
2. 修复罕见情况下的多层匿名类指向错误的问题
3. 现在如果存在多个匹配的拓展方法实现，会自动使用路径最相近的实现

### v3.2.2

1. 优化项目依赖结构

### v3.2.3

1. 支持java21、java22
2. 优化项目编译结构

</details>

### idea插件4.4

1. 提供对可选链 &`elvis` 支持

<details>
  <summary>历史idea插件更新</summary>

### idea插件2.0

1. 支持`f-string`自动提示格式符及类型匹配错误
2. 普通字符串支持自动识别转化为`$-string`

### idea插件2.1

1. 模板字符串结构字符会用特殊颜色标出

### idea插件2.3

1. 修复启动后一段时间代码异常检查失效的问题

### idea插件2.4

1. 支持拓展方法
2. 在未引入该项目的代码中，不再提示模板字符串功能

### idea插件2.5

1. 拓展方法显示优化

### idea插件2.6

1. 拓展方法显示优化

### idea插件2.7

1. 拓展方法自动引包相关支持

### idea插件2.8

1. 支持在成员方法引用中对外部引用调用拓展方法的情况

### idea插件2.9

1. 在idea 203以上版本支持了拓展方法引用处点击跳转。203以下版本会跳转到代理对象

### idea插件3.0

1. 重构了拓展方法及自动提示。现在已支持代理泛型推断及泛型数组推断

### idea插件3.1

1. 增强了模板字符串和拓展函数的联合效果。使用拓展函数支持自动引包

### idea插件3.2

1. 修复idea2023.3版本的兼容性问题
2. 当输入于变量后自动补全时，不再提示其静态方法

### idea插件3.3

1. 修复部分问题

### idea插件3.4

1. 强化自动补全功能对代理类泛型的支持

### idea插件3.5

1. 强化自动补全功能对代理类泛型的支持：优化泛型继承解析
2.

### idea插件3.6

1. 功能性优化

### idea插件3.8

1. 支持同名方法自动解析
2. 原有方法冲突时，自动使用原有方法

### idea插件4.1

1. 拓展方法注解能力拓展，支持3.2.5新增`@ExMethod`注解属性、及`@ExMethodIDE`注解
2. 现在插件的检测范围只限制于当前已申明插件的module，并只会提供其引入的拓展方法

### idea插件4.2

1. 修复4.1版本对基本类型数组拓展方法无法补全的问题
2. 对cover类型拓展方法使用处增加提示信息及自动import


</details>

## 相关项目

### ExMethodUtil

项目[ExMethodUtil](https://github.com/122006/ExMethodUtil)封装了常见的java工具方法，可用于体验或者测试拓展方法功能。

> Zircon主体项目中不包含任何预定义的拓展方法，你可以引入该项目快速体验Zircon

`implementation 'com.github.122006:ExMethodUtil:1.1.8'`
