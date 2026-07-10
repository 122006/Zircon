package probe;

import zircon.ExMethod;

public final class Extensions {
    private Extensions() {
    }

    @ExMethod
    public static String surround(String value, String left, String right) {
        return left + value + right;
    }
}
