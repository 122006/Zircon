package com.sun.tools.javac.parser;


import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    static HashMap<String, Constructor<?>[]> constructorsCache = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <T, M> M getDeclaredField(T t, Class<?> tClazz, String fieldName) {
        try {
            final String key = tClazz.getClassLoader().hashCode() + ":" + tClazz.getName() + "#" + fieldName;

            if (fieldCache.containsKey(key)) {
                final Field field = fieldCache.get(key);
                return (M) field.get(t);
            }
            Class<?> _clazz = tClazz;
            Field declaredField = null;
            w:
            while (true) {
                final Field[] declaredFields = _clazz.getDeclaredFields();
                for (Field field : declaredFields) {
                    if (field.getName().equals(fieldName)) {
                        declaredField = field;
                        break w;
                    }
                }
                _clazz = _clazz.getSuperclass();
                if (_clazz == null) {
                    break;
                }
            }
            if (declaredField == null) {
                throw new NoSuchFieldException("类" + tClazz.getName() + "及向上父类中未发现变量" + fieldName);
            }
            declaredField.setAccessible(true);
            fieldCache.put(key, declaredField);
            return (M) declaredField.get(t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<? super T> tClazz, Object... args) {
        try {
            String clazz = "";
            for (Object arg : args) {
                clazz += (arg == null ? null : arg.getClass().getName()) + "-";
            }
            final String key = tClazz.getClassLoader().hashCode() + ":" + tClazz.getName() + "#" + clazz;
            if (constructorsCache.containsKey(key)) {
                final Constructor<?>[] constructors = constructorsCache.get(key);
                con:
                for (Constructor<?> constructor : constructors) {
                    final Class<?>[] parameterTypes = constructor.getParameterTypes();
                    if (parameterTypes.length != args.length) {
                        continue;
                    }
                    for (int i = 0; i < parameterTypes.length; i++) {
                        if (args[i] == null) continue;
                        if (parameterTypes[i].isAssignableFrom(getPrimitiveType(args[i].getClass()))) {
                            continue;
                        } else {
                            continue con;
                        }
                    }
                    constructor.setAccessible(true);
                    return (T) constructor.newInstance(args);
                }
                return null;
            }
            Constructor<?>[] constructors = tClazz.getDeclaredConstructors();
            constructorsCache.put(key, constructors);
            return newInstance(tClazz, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static HashMap<String, Method> methodCache = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <T, R> R invokeMethod(T instance, Class<?> clazz, String methodName, Object... args) {
        try {
            String argsClazz = "";
            for (Object arg : args) {
                argsClazz += (arg == null ? null : arg.getClass().getName()) + "-";
            }
            final String key = clazz.getClassLoader().hashCode() + ":" + clazz.getName() + "#" + argsClazz;
            if (methodCache.containsKey(key)) {
                final Method method = methodCache.get(key);
                return (R) method.invoke(instance, args);
            }
            Method method = findMethod(clazz, methodName, args);
            if (method == null) {
                throw new RuntimeException(new NoSuchMethodException("No suitable method found for " + methodName));
            }
            method.setAccessible(true);
            methodCache.put(key, method);
            return (R) method.invoke(instance, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Method findMethod(Class<?> clazz, String methodName, Object[] args) {
        con:
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != args.length) {
                    continue;
                }
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (args[i] == null) continue;
                    if (parameterTypes[i].isAssignableFrom(getPrimitiveType(args[i].getClass()))) {
                        continue;
                    } else {
                        continue con;
                    }
                }
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    public static Class<?> getPrimitiveType(Class<?> wrapperClass) {
        if (wrapperClass == Integer.class) {
            return int.class;
        } else if (wrapperClass == Double.class) {
            return double.class;
        } else if (wrapperClass == Boolean.class) {
            return boolean.class;
        } else if (wrapperClass == Character.class) {
            return char.class;
        } else if (wrapperClass == Byte.class) {
            return byte.class;
        } else if (wrapperClass == Short.class) {
            return short.class;
        } else if (wrapperClass == Long.class) {
            return long.class;
        } else if (wrapperClass == Float.class) {
            return float.class;
        } else {
            return wrapperClass;
        }
    }


}
