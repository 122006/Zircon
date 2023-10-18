### 什么是拓展方法

> 能够向现有类型直接“添加”方法，而无需创建新的派生类型、重新编译或以其他方式修改现有类型。调用扩展方法的时候，与调用在类型中实际定义的方法相比没有明显的差异。


### 拓展方法的必要性
> 拓展方法没有必要性。拓展方法只是为了能让你更简单地完成你的代码，专注于业务逻辑而不是各种工具类的记忆中。
> 在任何场景，你都可以用工具类的静态方法以替换拓展方法的实现，但是拓展方法可以让你写得更嗨以及代码更加简洁。
> 欢迎使用`Zircon`，希望能给你带来更好的java代码体验。

### 使用方法及注意事项

> 在项目任意位置中，声明一个包含注解`@ExMethod`的方法。后续你可以在项目任何地方中使用这个方法。

#### 1.添加静态方法

``` 
    @ExMethod(ex = {List.class})
    public static <T> List<T> synchronizedList(T... data){
        return Collections.synchronizedList(Arrays.asList(data));
    }
```

> 以上方法声明了一个静态拓展方法：为List增加了一个传入泛型可变参数并返回同步列表的静态方法。
> 在任何地方，你可以`List.synchronizedList(xxxx)`以使用该方法
> 你可以声明多个类，以同时实现同个静态方法。也可以不使用大括号直接声明单个类

#### 2.添加动态方法

``` 
    @ExMethod
    public static <T> T find(Collection<T> collection, Predicate<T> predicate) {
        return collection.stream().filter(predicate).findFirst().orElse(null);
    }
``` 

> 以上方法声明了一个动态拓展方法：为集合对象增加了一个方法以筛选出其中第一个符合条件的值。
> 在方法外，你可以对集合对象调用`.find(it->it!=null)`以使用该方法。

``` 
    @ExMethod
    public static boolean isNull(Object obj) {
        return obj == null;
    }
```

> 动态拓展方法的本质是将动态调用在编译时静态替换为静态方法。故而对null对象使用将不会抛出空指针异常。

#### 3.已有方法替换

``` 
    @ExMethod(ex = {PrintStream.class},cover = true)
    public static void println(String a) {
        System.out.print("\n ex:  success hook static method:" + a);
    }
```

> 如果你声明的方法已有实现，你的方法不会生效。特别的，你可以指定`cover=true`以强制指向至你声明的方法

----------------  

### FAQ

1. 定义的方法可以影响到引用的依赖么？

   > 不可以，已经编译为class的代码无法进行修改。但是，如果在依赖中定义的拓展方法可以继续传递到你当前的项目中。
   > 你可以封装一个独立的拓展项目以应用到其他项目中。
   > 当然，后续更新中也会增加对已有class的修改需求(asm)、拓展方法作用域定义

2. 为什么我声明在`List<String>`对象的方法在`List<Integer>`上也能看到？
   > 声明的动态方法不会被类型参数限制。但是定义完备其代理对象的类型参数后，可以参与其他参数的类型推断。
   > idea插件会下划线高亮该方法代理的对象

3. 我可以代理数组么？
   > 数组是一个特殊的对象，可以代理数组。但是无法通过数组的类型来区分不同的对象（idea插件会下划线高亮数组中括号以提示）
   > 以下提供了一个为数组对象增加add方法的拓展方法声明

``` 
    @ExMethod
    public static <T> T[] add(T[] array,T... add) {
        final T[] nArray = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length + add.length);
        System.arraycopy(array,0,nArray,0,array.length);
        System.arraycopy(add,0,nArray,array.length,add.length);
        return nArray;
    }
```

4. 我声明了动态拓展方法为什么没有反应（或者需要重启生效）？
   > 1. 收集拓展方法需要全局遍历，且该方法通常并不需要经常修改，故而拓展方法的缓存只有在部分时候进行更新。当你更新了拓展方法后，请sync项目而无需重新打开。
   > 2. 插件已提供手动同步方法与未同步警告，请根据idea提示进行修复即可

5. 为什么拓展方法需要import相关声明类？
   > 1. 在实际注入点声明前，引入涉及到的相关类，以保证能够正确替换
   > 2. 插件已支持自动引包

6. 相比lombok、manifold等有什么优势呢
   > 1. 更简单的声明：在任意代码地方可声明。只需要方法为静态且增加`@ExMethod`方法注解。不需要特殊包名或者引用处声明
   > 2. 更易用的泛型支持：支持方法参数泛型推导，不需要将方法泛型维护为原有类泛型名，且不需要手动绑定泛型关系。
   > 3. idea插件支持更强：输入时支持方法匹配及提示
   > 4. lambda表达式、成员方法引用支持：支持面更广
   > 
   > 但是lombok真的好用，manifold其他功能是真的强，推荐搭配使用 ^^
