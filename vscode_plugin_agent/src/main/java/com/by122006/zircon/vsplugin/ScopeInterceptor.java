package com.by122006.zircon.vsplugin;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class ScopeInterceptor {

    @RuntimeType
    public static Object intercept(
            @This Object scope,
            @Origin Method origin,
            @SuperCall Callable<?> zuper,
            @AllArguments Object[] args
    ) throws Exception {
        String selector = extractSelector(args);
        if (isDebugEnabled()) {
            log("[ScopeInterceptor] " + origin.getDeclaringClass().getName() + "#" + origin.getName()
                    + " selector=" + selector
                    + ", argCount=" + (args == null ? 0 : args.length));
        }

        Object result = zuper.call();
        if (isProblem(result)) {
            Object receiverType = args != null && args.length > 0 ? args[0] : null;
            Object[] argumentTypes = args != null && args.length > 2 && args[2] instanceof Object[]
                    ? (Object[]) args[2]
                    : null;
            Object invocationSite = args != null && args.length > 3 ? args[3] : null;
            Object extensionMethod = ZirconCore.findExtension(scope, receiverType, selector, argumentTypes, invocationSite);
            if (extensionMethod != null) {
                log("[ScopeInterceptor] matched extension method: " + selector);
                return extensionMethod;
            }
        }
        return result;
    }

    private static boolean isProblem(Object binding) {
        if (binding == null) {
            return true;
        }
        return binding.getClass().getSimpleName().startsWith("Problem");
    }

    private static String extractSelector(Object[] args) {
        if (args == null || args.length <= 1 || !(args[1] instanceof char[])) {
            return "";
        }
        return new String((char[]) args[1]);
    }

    private static final java.nio.file.Path LOG_PATH = resolveLogPath();

    public static void log(String msg) {
        try {
            java.nio.file.Files.write(
                    LOG_PATH,
                    (msg + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {
        }
    }

    public static boolean isDebugEnabled() {
        return getBooleanProperty("zircon.debug", false);
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

    public static java.nio.file.Path resolveLogPath() {
        String explicit = getProperty("zircon.log.path", "").trim();
        if (!explicit.isEmpty()) {
            return java.nio.file.Paths.get(explicit);
        }
        String tempDir = System.getProperty("java.io.tmpdir", ".");
        return java.nio.file.Paths.get(tempDir, "zircon_vscode_agent.log");
    }
}
