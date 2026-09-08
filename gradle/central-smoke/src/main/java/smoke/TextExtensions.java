package smoke;

import zircon.ExMethod;

public class TextExtensions {
    @ExMethod
    public static String bracketed(String value) {
        return "[" + value + "]";
    }
}
