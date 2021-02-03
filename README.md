# Zircon 

支持在Java语言中使用动态字符串

    实现类似于kotlin、Groovy等语言中内插字符串
    
    支持android、java等所有使用javac的项目
    
    几乎不会增加额外编译时间
    
    代码内容支持idea补全提示（需自行配置）
    
    
 ![example](https://github.com/122006/Zircon/blob/master/others/input.png)
 

* 使用示例
         
      String add = "test2";
      assertEquals($("test1 ${add}"), "test1 test2");
      
      assertEquals($("test1 ${"Test,mode".substring(0,6)}${1+2}"), "test1 Test,m3");
          
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
            <artifactId>plugin</artifactId>
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
        
        
* 其他注意事项

   * $()方法中任何字符串都会被检测是否含有'${}'标识，请注意'${}'内容代码的正确性
        
   * 暂时不支持 `$变量名最长匹配` 解析。期望在后续版本中进行支持
   
   * idea代码补全配置 (请勾选所有方法)
   
   ![languageinjection](https://github.com/122006/Zircon/blob/master/others/languageinjections.png)
   
   * 由于使用groovy进行代码补全，'${}'内字符串'\n'会进行转义导致后续idea报错（即使运行结果不会报错）。请使用'\\\n'以替代（会转义为('\n')）