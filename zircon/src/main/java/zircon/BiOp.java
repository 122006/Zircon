package zircon;

@SuppressWarnings("unchecked")
public class BiOp {
    public static Object $$elvisExpr;

    public static void sInvoke(Object obj, Void cName) {
    }

    public static <T> T sec(Object obj, T obj2) {
        return obj2;
    }

    public static <T> T self(T obj) {
        return obj;
    }

    //危险(该方法会后置补充dup指令。请一定不要直接调用该方法)
    public static <T> T $$dup(T t) {
        return null;
    }

    //危险(该方法及参数会被忽视。请一定不要直接调用该方法)
    public static <T> T $$ignore(T t) {
        return t;
    }

    public static <T, T2 extends T> T $$elvisExpr(T t, T2 t2) {
        return t2;
    }

    //危险(该方法会前置补充dup指令。请一定不要直接调用该方法)
    public static <T> T $$pop(T t) {
        return (T) t;
    }

    //危险(该方法会替换为dup指令。请一定不要直接调用该方法)
    public static boolean $$pop() {
        return true;
    }


    //危险(该方法仅使用参数2并强转为参数1类型。请一定不要直接调用该方法)
    public static <T> T $$useParam2WithParam1Type(T t, Object t2) {
        return (T) t2;
    }

    //危险(该方法前置补充dup指令、并仅使用参数2并强转为参数1类型。请一定不要直接调用该方法)
    public static <T> T $$pop$$useParam2WithParam1Type(T t, Object t2) {
        return (T) t2;
    }

    //该方法仅在编译中使用，在gen阶段会自动替换为入参（防止部分编译期优化）
    public static <T> T $$wrap(T value) {
        return value;
    }
}
