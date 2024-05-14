package com.by122006.zircon;

import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.UnicodeReader;
import com.sun.tools.javac.util.Context;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class ZirconPlugin extends TreeScanner<Void, Void> implements Plugin {

    public abstract String getName();

    public abstract String getCName();

    boolean isLoad = false;
    JavaCompiler compiler = null;

    Context context;

    @Override
    public void init(JavacTask task, String... args) {
        supportJava9OrUpper();
        System.out.println("inject [" + getCName() + "] jdk:" + System.getProperty("java.version"));
        BasicJavacTask javacTask = (BasicJavacTask) task;
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent event) {
                if (context != javacTask.getContext()) {
                    context = javacTask.getContext();
                    isLoad = false;
                }
                if (!isLoad) {
                    try {
                        compiler = JavaCompiler.instance(context);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                    isLoad = true;
                    try {
                        reloadClass("com.sun.tools.javac.parser.ReflectionUtil", ZirconPlugin.class.getClassLoader(), Attr.class.getClassLoader());
                        reloadClass("com.sun.tools.javac.parser.ZrUnSupportCodeError", ZirconPlugin.class.getClassLoader(), Attr.class.getClassLoader());
                        reloadClass("com.sun.tools.javac.parser.ZrConstants", ZirconPlugin.class.getClassLoader(), Attr.class.getClassLoader());
                        startTask(context, compiler, ZirconPlugin.class.getClassLoader(), Attr.class.getClassLoader());
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        throw new RuntimeException(exception);
                    }
                }


            }

            @Override
            public void finished(TaskEvent e) {

            }
        });
    }


    public abstract void startTask(Context context, JavaCompiler compiler, ClassLoader classLoader, ClassLoader classLoader1) throws Exception;


    public static Object get(Object obj, String field) {
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);

        }
    }

    public static Object get(Class<?> clazz, String field) {
        try {
            Field f = clazz.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);

        }
    }

    public static void set(Object obj, String field, Object val) {
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(obj, val);
        } catch (Exception e) {
            throw new RuntimeException(e);

        }
    }


    public static Object getInstance(Class<?> clas, Context context) throws ReflectiveOperationException {
        return clas.getDeclaredMethod("instance", Context.class).invoke(null, context);
    }

    public static boolean checkJavaTokenizerVersionNew() {
        return UnicodeReader.class.isAssignableFrom(JavaTokenizer.class);
    }

    static <T> Class<T> reloadClass(String claz, ClassLoader incl, ClassLoader outcl) throws Exception {
        return reloadClass(claz, incl, outcl, null);
    }

    static String dir;

    public static boolean javaVersionUpper(int versionCode) {
        final String version = System.getProperty("java.version");
        return Integer.parseInt(version.split("\\.")[0]) >= versionCode;
    }

    static boolean hasAddOpen = false;

    /**
     * thanks lombok
     */
    public static void supportJava9OrUpper() {
        if (hasAddOpen) return;
        hasAddOpen = true;
        Class<?> cModule;
        try {
            cModule = Class.forName("java.lang.Module");
        } catch (ClassNotFoundException e) {
            return; //jdk8-; this is not needed.
        }
        try {
            Class<?> cModuleLayer = null;
            try {
                cModuleLayer = Class.forName("java.lang.ModuleLayer");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            Method mBoot = cModuleLayer.getDeclaredMethod("boot");
            Object bootLayer = mBoot.invoke(null);
            Class<?> cOptional = Class.forName("java.util.Optional");
            Method mFindModule = cModuleLayer.getDeclaredMethod("findModule", String.class);
            Object oCompilerO = mFindModule.invoke(bootLayer, "jdk.compiler");
            final Object jdkCompilerModule = cOptional.getDeclaredMethod("get").invoke(oCompilerO);
            String[] allPkgs = {
                    "com.sun.tools.javac.code",
                    "com.sun.tools.javac.comp",
                    "com.sun.tools.javac.file",
                    "com.sun.tools.javac.main",
                    "com.sun.tools.javac.model",
                    "com.sun.tools.javac.parser",
                    "com.sun.tools.javac.processing",
                    "com.sun.tools.javac.tree",
                    "com.sun.tools.javac.util",
                    "com.sun.tools.javac.jvm",
                    "com.sun.tools.javac.api",
            };
            try {
                final Field allUnnamedModuleField = cModule.getDeclaredField("ALL_UNNAMED_MODULE");
                allUnnamedModuleField.setAccessible(true);
                Method m = cModule.getDeclaredMethod("implAddOpens", String.class, cModule);
                setAccessibleTrue(m);
                final Object allUnnamedModule = allUnnamedModuleField.get(null);
                for (String p : allPkgs) {
                    m.invoke(jdkCompilerModule, p, allUnnamedModule);
                }
            } catch (NoSuchFieldException e) {
                Method m = cModule.getDeclaredMethod("implAddOpensToAllUnnamed", String.class);
                setAccessibleTrue(m);
                for (String p : allPkgs) {
                    m.invoke(jdkCompilerModule, p);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private static void setAccessibleTrue(Method m) throws Exception {
        try {
            m.setAccessible(true);
        } catch (Exception e) {
            final Class<?> aClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = aClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            final Method objectFieldOffset = aClass.getMethod("objectFieldOffset", Field.class);
            long firstFieldOffset = (long) objectFieldOffset.invoke(unsafe,Parent.class.getDeclaredField("first"));
            final Method putBooleanVolatile = aClass.getMethod("putBooleanVolatile", Object.class,long.class,boolean.class);
            putBooleanVolatile.invoke(unsafe,m,firstFieldOffset,true);
        }
    }

    static <T> Class<T> reloadClassJavacVersion(String claz, ClassLoader incl, ClassLoader outcl) throws Exception {
        final String[] split = claz.split("\\.");
        final String simpleClassName = split[split.length - 1];
        if (dir == null) {
            if (javaVersionUpper(16)) {
                dir = "java16";
            } else if (javaVersionUpper(11)) {
                dir = "java11";
            } else {
                dir = "java7";
            }
        }
        final String oPath = "clazz/" + dir + "/" + simpleClassName + ".clazz";
        return reloadClass(claz, incl, outcl, oPath);
    }

    // <editor-fold defaultstate="collapsed" desc="reloadClass">
    @SuppressWarnings("unchecked")
    static <T> Class<T> reloadClass(String claz, ClassLoader incl, ClassLoader outcl, String oPath) throws Exception {
        try {
            return (Class<T>) outcl.loadClass(claz);
        } catch (ClassNotFoundException e) {
        }
        try {
            String path = oPath != null ? oPath : (claz.replace('.', '/') + ".class");
            InputStream is = path.startsWith("/") ? ZirconPlugin.class.getResourceAsStream(path) : incl.getResourceAsStream(path);
            if (is == null) {
                throw new RuntimeException("找不到对应类:" + claz);
            }
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            Method m = ClassLoader.class.getDeclaredMethod("defineClass", new Class[]{
                    String.class, byte[].class, int.class, int.class});
            setAccessibleTrue(m);
            return (Class<T>) m.invoke(outcl, claz, bytes, 0, bytes.length);
        } catch (Exception e) {
            throw new Exception("Zircon编译错误，可能是使用了无法支持的java编译器", e);
        }
    }
    //</editor-fold>
}
