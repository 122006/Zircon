package dependency;

import zircon.ExMethod;

public final class BinaryExtensions {
    private BinaryExtensions() {
    }

    @ExMethod
    public static String binarySurround(String value, String left, String right) {
        return left + value + right;
    }

    @ExMethod(filterAnnotation = {BinaryTypes.Allowed.class})
    public static String filtered(Object value) {
        return String.valueOf(value);
    }
}
