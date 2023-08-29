package com.by122006.zircon;

import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.UnicodeReader;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class ZirconPlugin extends TreeScanner<Void, Void> implements Plugin {

    public abstract String getName();

    public abstract String getCName();

    boolean isLoad = false;
    JavaCompiler compiler = null;

    Context context;

    @Override
    public void init(JavacTask task, String... args) {
        System.out.println("inject [" + getCName() + "] jdk:" + System.getProperty("java.version"));
        BasicJavacTask javacTask = (BasicJavacTask) task;
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent event) {
//                System.out.println("event:" + event.getSourceFile() + " " + event.getKind());
                if (context != javacTask.getContext()) {
                    System.out.println(javacTask.getContext());
                    context = javacTask.getContext();
                    isLoad=false;
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
                        startTask(context, compiler, ZirconPlugin.class.getClassLoader(), Attr.class.getClassLoader());
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        throw new RuntimeException(exception);
                    }
                }


            }

            @Override
            public void finished(TaskEvent e) {

//                if (e.getKind() == TaskEvent.Kind.ANALYZE) {
//                    e.getCompilationUnit().accept(ZirconPlugin.this, null);
//                }
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

    static <T> Class<T> reloadClassJavacVersion(String claz, ClassLoader incl, ClassLoader outcl) throws Exception {
        final String[] split = claz.split("\\.");
        final String simpleClassName = split[split.length - 1];
        return reloadClass(claz, incl, outcl, "clazz/" + (checkJavaTokenizerVersionNew() ? "java16" : "java7") + "/" + simpleClassName + ".clazz");
    }

    // <editor-fold defaultstate="collapsed" desc="reloadClass">
    @SuppressWarnings("unchecked")
    static <T> Class<T> reloadClass(String claz, ClassLoader incl, ClassLoader outcl, String oPath) throws Exception {
        try {
            return (Class<T>) outcl.loadClass(claz);
        } catch (ClassNotFoundException e) {
        }
        String path = oPath != null ? oPath : (claz.replace('.', '/') + ".class");
        InputStream is = path.startsWith("/") ? ZirconPlugin.class.getResourceAsStream(path) : incl.getResourceAsStream(path);
        if (is == null) {
            throw new RuntimeException("找不到对应类:" + claz);
        }
        byte[] bytes = new byte[is.available()];
        is.read(bytes);
        Method m = ClassLoader.class.getDeclaredMethod("defineClass", new Class[]{
                String.class, byte[].class, int.class, int.class});
        m.setAccessible(true);
        return (Class<T>) m.invoke(outcl, claz, bytes, 0, bytes.length);
    }
    //</editor-fold>
}
