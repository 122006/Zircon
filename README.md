# Zircon 

支持在Java语言中使用内插字符串

    实现类似于kotlin、Groovy等语言中内插字符串(对java语言特异性改动)
    
        >你可以使用形如 `$""` 或`f""`的内插字符串格式 
    
    支持android、java等所有使用javac的项目
    
    几乎不会增加额外编译时间
    
    代码内容支持idea补全提示（需安装idea插件）
    
    
 ![example](https://ae01.alicdn.com/kf/U99d3e32cf6824b1d8e5bedf2248b94f5x.jpg)
 

* 使用示例
         
      String add = "test2";
      assertEquals($"test1 $add", "test1 test2");
      
      assertEquals($"test1 ${'Test,mode'.substring(0,6)}${1+2}", "test1 Test,m3");
          
* 插件引入

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

    Step 3. 在需要使用插件的module的`build.gradle`文件中进行如下操作

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
        
* 安装IDEA插件
   
   

* 其他注意事项

   * $方法中任何字符串都会被检测是否含有'${}'标识，请注意'${}'内容代码的正确性
   
   * 特殊语法：在${}代码块中，为了减少转义符的使用，你可以用单引号`'String'`来修饰字符串。如果需要使用单引号以声明`char`类型，你需要使用`\'C\'`进行转义
   
        
