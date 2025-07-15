package zircon;

@SuppressWarnings("unchecked")
public class BiOp {
    public static Object $$elvisExpr;

    public static void sInvoke(Object obj, Void cName) {
    }

    public static <T> T sec(Object obj, T obj2) {
        return obj2;
    }

    //该方法仅在编译中使用，在gen阶段会自动替换为入参（防止部分编译期优化）
    public static <T> T $$wrap(T value) {
        return value;
    }

    //elvisExpr标识
    public static boolean $$elvisExpr() {
        return true;
    }

    public static <T> T $$NullSafe(T o) {
        throw new RuntimeException("异常链路：" + o);
//        return o;
    }
}
