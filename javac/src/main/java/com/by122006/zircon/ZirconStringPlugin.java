/*
 * Copyright (C) 2009-2013 The Project Lombok Authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.by122006.zircon;

import com.sun.source.tree.LiteralTree;
import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.util.Context;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class ZirconStringPlugin extends TreeScanner<Void, Void> implements Plugin {

    public static final String NAME = "ZrString";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(JavacTask task, String... args) {
        System.out.println( "inject [动态字符串插件]" );
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
        reloadClass( "com.sun.tools.javac.parser.Item", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.parser.ReflectionUtil", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.parser.Formatter", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.parser.SStringFormatter", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.parser.FStringFormatter", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.parser.StringRange", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.parser.ZrJavaTokenizer$JavaCException", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.parser.ZrJavaTokenizer", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.util.ZrJavadocTokenizer", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.parser.ZrParserFactory", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.util.ZrJavadocTokenizer", pcl, classLoader);
        reloadClass( "com.sun.tools.javac.util.ZrScanner", pcl, classLoader);
        Class<?> OOScannerFactoryClass = reloadClass( "com.sun.tools.javac.util.ZrScannerFactory", pcl, classLoader);
//        ScannerFactory var1 = (ScannerFactory) context.get(ScannerFactory.scannerFactoryKey);
        ParserFactory parserFactory = (ParserFactory) get(compiler, "parserFactory" );
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
        return clas.getDeclaredMethod( "instance", Context.class).invoke(null, context);
    }

    @SuppressWarnings( "unchecked" )
    /** add class claz to outClassLoader */
    static <T> Class<T> reloadClass(String claz, ClassLoader incl, ClassLoader outcl) throws Exception {
        try { // already loaded?
            return (Class<T>) outcl.loadClass(claz);
        } catch (ClassNotFoundException e) {
        }
        String path = claz.replace('.', '/') + ".class";
        InputStream is = incl.getResourceAsStream(path);
        byte[] bytes = new byte[is.available()];
        is.read(bytes);
        Method m = ClassLoader.class.getDeclaredMethod( "defineClass", new Class[]{
                String.class, byte[].class, int.class, int.class});
        m.setAccessible(true);
        return (Class<T>) m.invoke(outcl, claz, bytes, 0, bytes.length);


    }
}
