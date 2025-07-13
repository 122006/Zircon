## 插件编译说明

设置Gradle jvm 运行版本为jdk11，ijplugin模块下buildPlugin进行打包，

打包成功插件文件位置 [ijplugin.zip](ijplugin/build/distributions/)

增加对应版本模块： 1.[settings.gradle](..%2Fsettings.gradle)

buildPlugin或者runIde命令前置会编译子版本模块，并class文件拷贝到ijplugin模块中