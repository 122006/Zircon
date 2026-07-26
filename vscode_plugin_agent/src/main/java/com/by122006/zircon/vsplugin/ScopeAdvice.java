package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ScopeAdvice {
    public static int implicitTraceCount = 0;
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> FIELD_MISSES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> METHOD_MISSES = new ConcurrentHashMap<>();

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(
            @Advice.This Object scope,
            @Advice.Origin Method origin,
            @Advice.AllArguments Object[] args,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result
    ) {
        boolean implicitLookup = "getImplicitMethod".equals(origin.getName());
        String selector = extractSelector(args, implicitLookup);
        boolean problem = result == null || result.getClass().getSimpleName().startsWith("Problem");
        boolean traceEnabled = isTraceEnabled();
        boolean trace = traceEnabled && isSelectorTraceEnabled(selector);
        if (!problem && !trace) {
            return;
        }
        Object[] implicitReceivers = implicitLookup ? resolveImplicitReceivers(scope) : null;
        if (traceEnabled && implicitLookup && implicitTraceCount < 200) {
            implicitTraceCount++;
            System.err.println("[ScopeAdvice] implicit selector=" + selector
                    + ", receivers=" + formatReceivers(implicitReceivers)
                    + ", problem=" + problem);
        }
        if (trace && traceEnabled) {
            System.err.println("[ZirconAdvice] " + origin.getDeclaringClass().getName() + "#" + origin.getName()
                    + " selector=" + selector
                    + ", problem=" + problem
                    + ", argCount=" + (args == null ? 0 : args.length));
        }
        if (!problem) {
            return;
        }
        Object[] receiverTypes = implicitLookup
                ? implicitReceivers
                : new Object[]{args != null && args.length > 0 ? args[0] : null};
        Object[] argumentTypes = extractArgumentTypes(args, implicitLookup);
        Object invocationSite = extractInvocationSite(args, implicitLookup);
        for (Object receiverType : receiverTypes) {
            if (receiverType == null) {
                continue;
            }
            Object extensionMethod = ZirconCore.findExtension(scope, receiverType, selector, argumentTypes, invocationSite);
            if (extensionMethod != null) {
                if (trace && traceEnabled) {
                    System.err.println("[ZirconAdvice] matched extension method for " + selector);
                }
                result = extensionMethod;
                return;
            }
        }
    }

    public static String extractSelector(Object[] args, boolean implicitLookup) {
        int selectorIndex = implicitLookup ? 0 : 1;
        if (args == null || args.length <= selectorIndex || !(args[selectorIndex] instanceof char[])) {
            return "";
        }
        return new String((char[]) args[selectorIndex]);
    }

    public static Object[] extractArgumentTypes(Object[] args, boolean implicitLookup) {
        int index = implicitLookup ? 1 : 2;
        if (args == null || args.length <= index || !(args[index] instanceof Object[])) {
            return null;
        }
        return (Object[]) args[index];
    }

    public static Object extractInvocationSite(Object[] args, boolean implicitLookup) {
        int index = implicitLookup ? 2 : 3;
        if (args == null || args.length <= index) {
            return null;
        }
        return args[index];
    }

    public static Object resolveImplicitReceiver(Object scope) {
        Object[] receivers = resolveImplicitReceivers(scope);
        return receivers.length == 0 ? null : receivers[0];
    }

    public static Object[] resolveImplicitReceivers(Object scope) {
        List<Object> receivers = new ArrayList<>();
        Object currentScope = scope;
        while (currentScope != null) {
            addReceiver(receivers, invokeScopeMethod(currentScope, "enclosingReceiverType"));
            addReceiver(receivers, invokeScopeMethod(currentScope, "enclosingSourceType"));
            currentScope = readField(currentScope, "parent");
        }
        return receivers.toArray();
    }

    private static void addReceiver(List<Object> receivers, Object receiver) {
        if (receiver == null) {
            return;
        }
        for (Object existing : receivers) {
            if (existing == receiver) {
                return;
            }
        }
        receivers.add(receiver);
    }

    private static Object invokeScopeMethod(Object scope, String methodName) {
        if (scope == null) {
            return null;
        }
        try {
            Method method = findMethod(scope.getClass(), methodName);
            if (method == null) {
                return null;
            }
            return method.invoke(scope);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String fieldName) {
        Map<String, Field> fields = FIELD_CACHE.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
        Field cached = fields.get(fieldName);
        if (cached != null) {
            return cached;
        }
        Set<String> misses = FIELD_MISSES.computeIfAbsent(type, ignored -> ConcurrentHashMap.newKeySet());
        if (misses.contains(fieldName)) {
            return null;
        }
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                fields.put(fieldName, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        misses.add(fieldName);
        return null;
    }

    private static Method findMethod(Class<?> type, String methodName) {
        Map<String, Method> methods = METHOD_CACHE.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
        Method cached = methods.get(methodName);
        if (cached != null) {
            return cached;
        }
        Set<String> misses = METHOD_MISSES.computeIfAbsent(type, ignored -> ConcurrentHashMap.newKeySet());
        if (misses.contains(methodName)) {
            return null;
        }
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                methods.put(methodName, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        misses.add(methodName);
        return null;
    }

    public static String formatReceivers(Object[] receivers) {
        if (receivers == null || receivers.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < receivers.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(receivers[index]);
        }
        builder.append(']');
        return builder.toString();
    }

    // Advice is inlined into JDT's Scope class. Every helper called directly from the
    // advice body must therefore be public; otherwise the transformed JDT class cannot
    // legally invoke it when tracing is enabled.
    public static boolean isSelectorTraceEnabled(String selector) {
        if (selector == null || selector.isEmpty()) {
            return false;
        }
        String configured = System.getProperty("zircon.trace.selectors", "").trim();
        if (configured.isEmpty()) {
            return false;
        }
        for (String token : configured.split("[,;\\s]+")) {
            String candidate = token == null ? "" : token.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if ("*".equals(candidate) || selector.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTraceEnabled() {
        return getBooleanProperty("zircon.trace", false);
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getProperty(key, Boolean.toString(defaultValue)));
    }

    public static String getProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        if (key != null && key.startsWith("zircon.")) {
            value = System.getProperty("Z" + key.substring(1));
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return defaultValue;
    }
}
