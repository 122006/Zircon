package com.by122006.zircon;

import com.sun.source.tree.LiteralTree;
import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.UnicodeReader;
import com.sun.tools.javac.util.Context;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ZirconStringPlugin extends TreeScanner<Void, Void> implements Plugin {

    public static final String NAME = "ZrString";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(JavacTask task, String... args) {
        System.out.println("inject [动态字符串插件] jdk:" + System.getProperty("java.version"));
        System.out.println(task.toString());

        BasicJavacTask javacTask = (BasicJavacTask) task;
        Context context = javacTask.getContext();
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                if (e.getKind() != TaskEvent.Kind.PARSE) {
                    return;
                }
                try {
                    startTask(context, ZirconStringPlugin.class.getClassLoader(), Attr.class.getClassLoader());
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }

            @Override
            public void finished(TaskEvent e) {
//                System.out.println("end: TaskEvent.Kind."+e.getKind());
                if (e.getKind() != TaskEvent.Kind.PARSE) {
                    return;
                }
                e.getCompilationUnit().accept(ZirconStringPlugin.this, null);
            }
        });
    }

    private void startTask(Context context, ClassLoader pcl, ClassLoader classLoader) throws Exception {
        JavaCompiler compiler = null;
        try {
            compiler = JavaCompiler.instance(context);
        } catch (Exception e) {
//            e.printStackTrace();
            return;
        }

        reloadClass("com.sun.tools.javac.parser.Item", pcl, classLoader);
        reloadClass("com.sun.tools.javac.parser.ReflectionUtil", pcl, classLoader);
        reloadClass("com.sun.tools.javac.parser.Formatter", pcl, classLoader);
        reloadClass("com.sun.tools.javac.parser.SStringFormatter", pcl, classLoader);
        reloadClass("com.sun.tools.javac.parser.FStringFormatter", pcl, classLoader);
        reloadClass("com.sun.tools.javac.parser.ZrStringModel", pcl, classLoader);
        reloadClass("com.sun.tools.javac.parser.StringRange", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.parser.ZrJavaTokenizer$JavaCException", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.parser.ZrJavaTokenizer", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.parser.ZrParserFactory", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.util.ZrJavadocTokenizer", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.util.ZrScanner", pcl, classLoader);
        Class<?> OOScannerFactoryClass = reloadClassJavacVersion("com.sun.tools.javac.util.ZrScannerFactory", pcl, classLoader);
//        ScannerFactory var1 = (ScannerFactory) context.get(ScannerFactory.scannerFactoryKey);
        ParserFactory parserFactory = (ParserFactory) get(compiler, "parserFactory");
        Object instance = getInstance(OOScannerFactoryClass, context);
        set(parserFactory, "scannerFactory", instance);
    }

    @Override
    public Void visitLiteral(LiteralTree node, Void unused) {
        return super.visitLiteral(node, unused);
    }

    public static Object get(Object obj, String field) {
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(obj);
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

    @SuppressWarnings("unchecked")
    static <T> Class<T> reloadClass(String claz, ClassLoader incl, ClassLoader outcl, String oPath) throws Exception {
        try {
            return (Class<T>) outcl.loadClass(claz);
        } catch (ClassNotFoundException e) {
        }
        String path = oPath != null ? oPath : (claz.replace('.', '/') + ".class");
        System.out.println("path=" + path);
        InputStream is = path.startsWith("/")?ZirconStringPlugin.class.getResourceAsStream(path):incl.getResourceAsStream(path);

        if (is == null) {
            final File file = new File("");
            System.out.println(file.getAbsolutePath());
            System.out.println(file.exists());
            for (String s : file.getParentFile().list()) {
                System.out.println(s);
            }
            throw new RuntimeException("找不到对应类:" + claz);
        }
        byte[] bytes = new byte[is.available()];
        is.read(bytes);
        Method m = ClassLoader.class.getDeclaredMethod("defineClass", new Class[]{
                String.class, byte[].class, int.class, int.class});
        m.setAccessible(true);
        return (Class<T>) m.invoke(outcl, claz, bytes, 0, bytes.length);
    }
}
