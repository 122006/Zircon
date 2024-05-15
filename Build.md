## 构建

### `gradle buildClazzByJava8`

> 生成java7~10版本的clazz文件

### `gradle buildClazzByJava11`

> 生成java16~15版本的clazz文件

### `gradle buildClazzByJava17`

> 生成java16及以上版本的clazz文件

### `gradle buildClazzByAllJavaVersion`

> 生成全部3个版本的clazz文件

## 测试

### `gradle testWithDiffJavaVersion`

> 生成对应版本clazz文件，对test模块以指定java版本编译，并在对应java环境下运行test模块测试内容。
> 可使用`-Ptjv=`指定版本号，默认java版本环境为java22
> ### `gradle testWithDiffJavaVersion -Ptjv=8`
> ### `gradle testWithDiffJavaVersion -Ptjv=11`
> ### `gradle testWithDiffJavaVersion -Ptjv=17`
> ### `gradle testWithDiffJavaVersion -Ptjv=21`
> ### `gradle testWithDiffJavaVersion -Ptjv=22`