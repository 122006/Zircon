package com.sun.tools.javac.parser;


import java.lang.reflect.Field;

public class ReflectionUtil {
    public static <T> void setDeclaredField(T t,Class<? super T> tClazz,String fieldName,Object object){
        try {
            Field declaredField = tClazz.getDeclaredField(fieldName);
            declaredField.setAccessible(true);
            declaredField.set(t,object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @SuppressWarnings("unchecked")
    public static <T,M> M getDeclaredField(T t, Class<? super T> tClazz, String fieldName){
        try {
            Field declaredField = tClazz.getDeclaredField(fieldName);
            declaredField.setAccessible(true);
            return (M) declaredField.get(t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
