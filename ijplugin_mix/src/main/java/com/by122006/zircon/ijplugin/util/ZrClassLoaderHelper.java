package com.by122006.zircon.ijplugin.util;

import zircon.data.ThrowSupplier;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * @ClassName: ZrClassLoaderHelper
 * @Author: 122006
 * @Date: 2025/7/2 21:40
 * @Description:
 */
public class ZrClassLoaderHelper {

    public static boolean hasClass(String checkClassName) {
        try {
            Class.forName(checkClassName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static Class<?> loadClass(String[] clazzName, String returnClazzName, String tag) throws Exception {
        for (String s : clazzName) {
            ZrClassLoaderHelper.loadClass(s, tag);
        }
        if (returnClazzName == null) return null;
        return ZrClassLoaderHelper.loadClass(returnClazzName, tag);
    }


    public static Class<?> loadClass(ThrowSupplier<Boolean> predicate, String clazzName, String tag) throws Exception {
        if (predicate.get()) {
            return ZrClassLoaderHelper.loadClass(clazzName, tag);
        } else {
            return null;
        }
    }

    public static synchronized Class<?> loadClass(String clazzName, String tag) throws Exception {
        final ClassLoader classLoader = ZrClassLoaderHelper.class.getClassLoader();
        try {
            return classLoader.loadClass(clazzName);
        } catch (ClassNotFoundException ignored) {
        }
        String path = "/clazz/" + tag + "/" + clazzName.replace('.', '/') + ".clazz";
        try (InputStream is = ZrClassLoaderHelper.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("dismiss class:  " + clazzName + ", in path:" + path);
            }
            byte[] bytes = is.readAllBytes();

            // Java 21+ no longer guarantees reflective access to
            // ClassLoader#defineClass. Define modern parser classes through a
            // package-private lookup anchored in the plugin class loader.
            int packageSeparator = clazzName.lastIndexOf('.');
            if (packageSeparator > 0) {
                String anchorName = clazzName.substring(0, packageSeparator) + ".ZrPackageAnchor";
                try {
                    Class<?> anchor = classLoader.loadClass(anchorName);
                    return MethodHandles.privateLookupIn(anchor, MethodHandles.lookup()).defineClass(bytes);
                } catch (ClassNotFoundException ignored) {
                    // Legacy parser packages do not have an anchor; use the
                    // compatibility fallback below.
                }
            }

            Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class,
                    byte[].class, int.class, int.class);
            m.setAccessible(true);
            return (Class<?>) m.invoke(classLoader, clazzName, bytes, 0, bytes.length);
        } catch (Exception e) {
            throw new Exception("Zircon加载错误。运行环境不匹配或内部错误", e);
        }

    }
}
