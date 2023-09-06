### 什么是拓展方法

> 能够向现有类型直接“添加”方法，而无需创建新的派生类型、重新编译或以其他方式修改现有类型。调用扩展方法的时候，与调用在类型中实际定义的方法相比没有明显的差异。

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
> 但是，请不要将动态拓展方法使用在成员方法引用中，无法正确替换。在这种情况下编译器及ide会提示并引导你替换为lambda表达式

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
   > 声明的动态方法不会被类型参数限制。但是定义完备其代理对象的类型参数后，可以参与其他参数的类型推断

3. 我可以代理数组么？
   > 数组是一个特殊的对象，可以代理数组。但是无法通过数组的类型来区分不同的对象
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

4. 为什么动态拓展方法声明成员方法引用时报错了？
   > 拓展方法的本质是将动态调用在编译时静态替换为静态方法。方法引用替换会导致其降级为lambda表达式，会在部分场景出现问题
   > 请根据引导替换为lambda表达式，以确定你真的需要这么做