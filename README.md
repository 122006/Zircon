# Zircon 

-----------------

## Zircon可以让你在Java语言中使用内插字符串语法

----------------  

###    什么叫内插字符串？  

字符串插值功能构建在复合格式设置功能的基础之上，提供更具有可读性、更方便的语法，用于将表达式结果包括到结果字符串。


### 如何使用内插字符串

若要将字符串标识为内插字符串，可在该字符串前面加上 $或f 符号。 可嵌入任何会在内插字符串中返回值的有效 JAVA 表达式。

对某个表达式执行计算后，其结果立即转换为一个字符串并包含到结果字符串中：

###    特性  

1. 支持android、java等所有使用javac的项目
    
2. 几乎不会增加额外编译时间
    
3. 代码内容支持idea补全提示（需安装idea插件） 

###    效果图

 ![example](others/zircon_show.png)


----------------  
### 使用示例


`String text=f" this is F-$String.class.getSimpleName() ";`

`assert Objects.equals($"Zircon: [ ${text.trim()} ]","Zircon: [ "+text.trim()+" ]");`

----------------  

### 插件引入

**使用Gradle构建项目**

Step 1. 在你的根目录项目`build.gradle`文件中进行如下操作

	    allprojects {
		    repositories {
		    	...
		    	maven { url 'https://jitpack.io' }
		    }
		    //如果编译安卓项目，加入以下代码
		    gradle.projectsEvaluated {
                tasks.withType(JavaCompile) {
                     options.compilerArgs << "-Xplugin:ZrString"
                }
            }
	    }

Step 2. 在需要使用插件的module的`build.gradle`文件中进行如下操作

	    dependencies {
	        ...
	        annotationProcessor 'com.github.122006.Zircon:javac:版本号'
            implementation 'com.github.122006.Zircon:impl:版本号'
	    }

    当前版本号：[![](https://jitpack.io/v/122006/Zircon.svg)](https://jitpack.io/#122006/Zircon)
	    
	    //如果编译标准java项目(非安卓项目)，加入以下代码
	    compileJava {
            options.compilerArgs  << "-Xplugin:ZrString"
        }
        
**使用Maven构建项目**
    
Step 1. 增加依赖

	    <dependency>
            <groupId>com.github.122006.Zircon</groupId>
            <artifactId>javac</artifactId>
            <version>版本号</version>
        </dependency>
        <dependency>
            <groupId>com.github.122006.Zircon</groupId>
            <artifactId>impl</artifactId>
            <version>版本号</version>
        </dependency>
        
Step 2. 配置jitpack仓库

	    <repositories>
        	<repository>
        	    <id>jitpack.io</id>
        	    <url>https://jitpack.io</url>
        	</repository>
        </repositories>
当前版本号：[![](https://jitpack.io/v/122006/Zircon.svg)](https://jitpack.io/#122006/Zircon)
	    
Step 2. 配置javac参数("-Xplugin:ZrString")
    
    
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <configuration>
            <compilerArgs>
              <arg>-Xplugin:ZrString</arg>
            </compilerArgs>
          </configuration>
        </plugin>
        
### 安装IDEA插件

  本插件尚未发布至idea仓库，请手动下载安装

1. 点击 [这里](ijplugin/ijplugin.jar) 进行下载（或目录中`/ijplugin/ijplugin.jar`文件）
2. 下载文件后拖动至idea中自动安装
   

### 其他注意事项

1. 特殊语法：在${}代码块中，为了减少转义符的使用，你可以用单引号`'String'`来修饰字符串。如果需要使用单引号以声明`char`类型，你需要使用`\'C\'`进行转义

--------------

## TODO 后续更新计划

* ### f和$前缀差异化解析

    例如f可以忽略中文以提升使用体验
    
* ### 支持其他语言的类似高级语法功能

    例如c#的为表达式指定输出格式(使用String.format的占位符语法)