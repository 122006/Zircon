# MagicJavaString 
通过对Javac的改写，提供Java对动态字符串的支持

    实现类似于kotlin、Groovy等语言中格式化字符串
    
    支持android、java等所有使用javac的项目
    
    几乎不会增加额外编译时间
    
    代码内容支持idea提示（需自行配置）

* 使用示例
         
      String add = "test2";
      assertEquals($("test1 ${add}"), "test1 test2");
      
      assertEquals($("test1 ${"Test,mode".substring(0,6)}${1+2}"), "test1 Test,m3");
          
* 插件引入

    Step 1. 在你的根目录项目`build.gradle`文件中加入以下仓库目录及插件依赖

	    allprojects {
		    repositories {
		    	...
		    	maven { url 'https://jitpack.io' }
		    }
		    //如果编译安卓项目，加入以下代码
		    gradle.projectsEvaluated {
                tasks.withType(JavaCompile) {
                     options.compilerArgs << "-Xplugin:JavaOO"
                }
            }
	    }
    Step 2. 在需要使用插件的module的`build.gradle`文件中增加工具类依赖

	    dependencies {
	        ...
	        annotationProcessor 'com.by122006.jsf.MagicJavaString:code:版本号'
            compile 'com.by122006.jsf.MagicJavaString:impl:版本号'
	    }
    当前版本号：[![](https://jitpack.io/v/122006/MagicJavaString.svg)](https://jitpack.io/#122006/ASM_SmartRunPluginImp)
	    
	    //如果编译标准java项目(非安卓项目)，加入以下代码
	    compileJava {
            options.compilerArgs  << "-Xplugin:JavaOO"
        }

* 其他注意事项

   * $()中参数只允许纯字符串或纯代码，混合模式展示无法解析 
            
        eg: $(~~"${123}"+123~~,"123"+"123")
   
