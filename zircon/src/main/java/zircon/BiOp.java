package zircon;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.function.Function;

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

    public static Function<Object, String> jStringFormat = null;

    /**
     * 对于j"{a:intValue,b:stringValue}"形式的字符串，对对象进行解析返回json中字符串。</br>
     * 本库不引入外部json库，仅提供简单解析能力。外部使用时需要自行设置jStringFormat。</br>
     */
    public static String jString(Object o) {
        if (jStringFormat != null) return jStringFormat.apply(o);
        if (o == null) return null;
        if (o instanceof BigDecimal) return ((BigDecimal) o).toPlainString();
        if (o instanceof BigInteger) return ((BigInteger) o).toString();
        if (o instanceof Number) return String.valueOf(o);
        if (o instanceof Boolean) return String.valueOf(o);
        if (o.getClass().isArray()) {
            return Arrays.deepToString((Object[]) o);
        }
        return '"' + String.valueOf(o) + '"';
    }
}
