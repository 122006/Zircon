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
package com.by122006.jsf;

import com.sun.source.tree.LiteralTree;
import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.util.Context;

import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//@ProviderFor(Plugin.class)
public class JavaOOPlugin extends TreeScanner<Void, Void> implements Plugin {
    static {
//        new Exception().printStackTrace();
    }

    public static final String NAME = "JavaOO";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void init(JavacTask task, String... args) {
        System.out.println("init JavaOOPlugin");
        BasicJavacTask javacTask = (BasicJavacTask) task;
        Context context = javacTask.getContext();
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
//                if (e.getKind() != TaskEvent.Kind.PARSE) {
//                    return;
//                }
//                e.getSourceFile();
                System.out.println("start: TaskEvent.Kind."+e.getKind());
                if (e.getKind() != TaskEvent.Kind.PARSE) {
                    return;
                }
                startTask(context,JavaOOPlugin.class.getClassLoader(), Attr.class.getClassLoader());
            }

            @Override
            public void finished(TaskEvent e) {
                System.out.println("end: TaskEvent.Kind."+e.getKind());
                if (e.getKind() != TaskEvent.Kind.PARSE) {
                    return;
                }
                e.getCompilationUnit().accept(JavaOOPlugin.this, null);
            }
        });
    }

    private void startTask(Context context, ClassLoader pcl, ClassLoader classLoader) {
        try {
            JavaCompiler compiler=JavaCompiler.instance(context);
            OOProcessor.reloadClass("com.sun.tools.javac.parser.OOJavaTokenizer", pcl, classLoader);
            OOProcessor.reloadClass("com.sun.tools.javac.parser.OOJavadocTokenizer", pcl, classLoader);
            OOProcessor.reloadClass("com.sun.tools.javac.parser.OOJavaTokenizer$StringGroup", pcl, classLoader);
            OOProcessor.reloadClass("com.sun.tools.javac.parser.OOJavaTokenizer$StringGroup$DynamicCode", pcl, classLoader);
            Class<?> OOScannerClass = OOProcessor.reloadClass("com.sun.tools.javac.parser.OOScanner", pcl, classLoader);
            Class<?> OOScannerFactoryClass = OOProcessor.reloadClass("com.sun.tools.javac.parser.OOScannerFactory", pcl, classLoader);

//            context.put(ScannerFactory.scannerFactoryKey, (ScannerFactory) null);
            ScannerFactory var1 = (ScannerFactory) context.get(ScannerFactory.scannerFactoryKey);
            ParserFactory parserFactory = (ParserFactory) OOProcessor.get(compiler, "parserFactory");
            System.out.println("OOScannerFactoryClass=" + OOScannerFactoryClass);
            System.out.println(Stream.of(OOScannerFactoryClass.getDeclaredMethods()).map(Method::getName).collect(Collectors.joining("    ,")));
            Object instance = OOProcessor.getInstance(OOScannerFactoryClass, context);
            System.out.println("ScannerFactory=" + instance);
            OOProcessor.set(parserFactory, "scannerFactory", instance);
            System.out.println("patch end");
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public Void visitLiteral(LiteralTree node, Void unused) {
        System.out.println(node.getValue());
        return super.visitLiteral(node, unused);
    }

}
