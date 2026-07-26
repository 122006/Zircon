package probe;

import zircon.ExMethod;

import java.util.ArrayList;
import java.util.List;

public final class Extensions {
    @FunctionalInterface
    public interface Mapper<T, R> {
        R apply(T value);
    }

    private Extensions() {
    }

    @ExMethod
    public static String surround(String value, String left, String right) {
        return left + value + right;
    }

    public static <E> List<E> identity(List<E> values) {
        return values;
    }

    @ExMethod
    public static <E, R> List<R> map(List<E> values, Mapper<E, ? extends R> mapper) {
        List<R> result = new ArrayList<>();
        for (E value : values) {
            result.add(mapper.apply(value));
        }
        return result;
    }
}
