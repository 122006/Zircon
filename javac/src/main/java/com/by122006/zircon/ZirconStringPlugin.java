package com.by122006.zircon;

import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.UnicodeReader;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ZirconStringPlugin extends TreeScanner<Void, Void> implements Plugin {

    public static final String NAME = "ZrString";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(JavacTask task, String... args) {
        System.out.println("inject [动态字符串插件] jdk:" + System.getProperty("java.version"));
        BasicJavacTask javacTask = (BasicJavacTask) task;
        Context context = javacTask.getContext();
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.PARSE) {
                    try {
                        startTask(context, ZirconStringPlugin.class.getClassLoader(), Attr.class.getClassLoader());
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }

            }

            @Override
            public void finished(TaskEvent e) {
                System.out.println("end: TaskEvent.Kind." + e.getKind());
                if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                    e.getCompilationUnit().accept(ZirconStringPlugin.this, null);
                }
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

        try {

            reloadClass("com.sun.tools.javac.parser.Item", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.ReflectionUtil", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.Formatter", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.SStringFormatter", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.FStringFormatter", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.ZrStringModel", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.StringRange", pcl, classLoader);


            reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$NeedRedirectMethod", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ExMethodInfo", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ZrMethodReferenceLookupHelper", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ZrLookupHelper", pcl, classLoader);
//            reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$1", pcl, classLoader);
            final Class<?> OOZrAttrClass = reloadClassJavacVersion("com.sun.tools.javac.comp.ZrAttr", pcl, classLoader);
            set(compiler, "attr", getInstance(OOZrAttrClass, context));
            final Class<?> ZrResolve = reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve", pcl, classLoader);
            resolve = (Resolve) getInstance(ZrResolve, context);



            reloadClassJavacVersion("com.sun.tools.javac.parser.ZrJavaTokenizer$JavaCException", pcl, classLoader);




            reloadClassJavacVersion("com.sun.tools.javac.parser.ZrJavaTokenizer", pcl, classLoader);


            reloadClassJavacVersion("com.sun.tools.javac.parser.ZrParserFactory", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.util.ZrJavadocTokenizer", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.util.ZrScanner", pcl, classLoader);
            Class<?> OOScannerFactoryClass = reloadClassJavacVersion("com.sun.tools.javac.util.ZrScannerFactory", pcl, classLoader);
//            ((Map)get(context, "ht")).remove(OOMemberClass,"memberEnterKey");
            ScannerFactory var1 = (ScannerFactory) context.get(ScannerFactory.scannerFactoryKey);
            ParserFactory parserFactory = (ParserFactory) get(compiler, "parserFactory");
            Object instance = getInstance(OOScannerFactoryClass, context);
            set(parserFactory, "scannerFactory", instance);
            treeMaker = (TreeMaker) get(parserFactory, "F");
            types = (Types) get(treeMaker, "types");
            names = (Names) get(treeMaker, "names");
            syms = (Symtab) get(treeMaker, "syms");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    TreeMaker treeMaker;
    Resolve resolve;
    Types types;
    Names names;
    Symtab syms;

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
        InputStream is = path.startsWith("/") ? ZirconStringPlugin.class.getResourceAsStream(path) : incl.getResourceAsStream(path);
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
