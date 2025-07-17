package zircon;

@SuppressWarnings("unchecked")
public class BiOp {

    public static <T> T sec(Object obj, T obj2) {
        return obj2;
    }

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
