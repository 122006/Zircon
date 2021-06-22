package com.by122006.zircon.ijplugin;


import java.lang.reflect.Field;

public class ReflectionUtil {
    public static <T> void set(T t,Class<? super T> tClazz,String fieldName,Object object){
        try {
            Field declaredField = tClazz.getDeclaredField(fieldName);
            declaredField.setAccessible(true);
            declaredField.set(t,object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static <T> Object get(T t,Class<? super T> tClazz,String fieldName){
        try {
            Field declaredField = tClazz.getDeclaredField(fieldName);
            declaredField.setAccessible(true);
            return declaredField.get(t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
