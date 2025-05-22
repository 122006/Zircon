### 什么是拓展方法


### 是否会重复触发左侧结果值？

对于语句，会创建临时变量记录可选链左侧的结果值，当不为null时，右侧直接使用该变量进行处理/
对于表达式，会使用特殊的方式生成jvm指令，当不为null时，右侧直接使用左侧结果进行处理(部分旧款反编译工具显示有误)

### 赋值语句

赋值语句很特殊，在kotlin中不允许在等式左侧使用可选链，当可选链不满足时语句无异议。/
在Zircon中，左侧可选链是允许的，当该链式的最后使用处判断为null时，根据链式的使用会有两种情况。/

1. 如果该可选链是单行语句，该赋值语句不会执行（其左侧到最后使用处已经执行）。/
2. 如果该可选链是表达式，只有等号右侧会执行（其左侧到最后使用处已经执行）。/
>     eg: \
>  1. `classA.var1?.var2=action().getResult();`=>`Object obj=classA.var1;if (obj!=null){obj.var2=action().getResult();}`
>  2. `String.valueOf(classA.var1?.var2=action().getResult())`=>`String.valueOf(Object obj=classA.var1;obj!=null?obj.var2=action().getResult():action().getResult())`

### 和拓展方法的组合使用

拓展方法的本质是方法调用类变为方法参数，但是可选链检查发生于拓展方法替换前，故而不会打断整个方法调用链路
