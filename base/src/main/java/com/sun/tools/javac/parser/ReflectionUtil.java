package com.sun.tools.javac.parser;


import java.lang.reflect.Field;
import java.util.HashMap;

public class ReflectionUtil {
    public static <T> void setDeclaredField(T t, Class<? super T> tClazz, String fieldName, Object object) {
        try {
            final String key = tClazz.getClassLoader().hashCode() + ":" + tClazz.getName() + "#" + fieldName;
            if (fieldCache.containsKey(key)) {
                final Field field = fieldCache.get(key);
                field.set(t, object);
                return;
            }
            Field declaredField = tClazz.getDeclaredField(fieldName);
            declaredField.setAccessible(true);
            fieldCache.put(key, declaredField);
            declaredField.set(t, object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static HashMap<String, Field> fieldCache = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <T, M> M getDeclaredField(T t, Class<? super T> tClazz, String fieldName) {
        try {
            final String key = tClazz.getClassLoader().hashCode() + ":" + tClazz.getName() + "#" + fieldName;
            if (fieldCache.containsKey(key)) {
                final Field field = fieldCache.get(key);
                return (M) field.get(t);
            }
            Field declaredField = tClazz.getDeclaredField(fieldName);
            declaredField.setAccessible(true);
            fieldCache.put(key, declaredField);
            return (M) declaredField.get(t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
