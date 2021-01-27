/*******************************************************************************
 * Copyright (c) 2012 Artem Melentyev <amelentev@gmail.com>.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the 
 * GNU Public License v2.0 + OpenJDK assembly exception.
 *
 * Contributors:
 *     Artem Melentyev <amelentev@gmail.com> - initial API and implementation
 ******************************************************************************/
package com.by122006.jsf;

import com.sun.source.tree.LiteralTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.OOScannerFactory;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.Scanner;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class OOProcessor extends AbstractProcessor {
    static {
//        new RuntimeException().printStackTrace();
    }
    Context context;
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        JavacProcessingEnvironment pe = (JavacProcessingEnvironment) processingEnv;
        context = pe.getContext();
        JavaCompiler compiler = JavaCompiler.instance(context);
        try {
            ClassLoader pclassloader = (ClassLoader) get(pe, "processorClassLoader");
            if (pclassloader != null && (!pclassloader.getClass().equals(ClassLoader.class)))
                // do not let compiler to close our classloader. we need it later.
                set(pe, JavacProcessingEnvironment.class, "processorClassLoader", new ClassLoader(pclassloader) {
                });
            if (pclassloader == null) { // netbeans
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Injecting OO to netbeans");
                patch(compiler, OOProcessor.class.getClassLoader());
                return;
            }
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Injecting OO to javac8");
//            final MultiTaskListener taskListener = (MultiTaskListener) get(compiler, "taskListener");
//            taskListener.add(new WaitAnalyzeTaskListener(compiler, pclassloader));
            patch(compiler, pclassloader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void set(Object obj, Class clas, String field, Object val) throws ReflectiveOperationException {
        Field f = clas.getDeclaredField(field);
        f.setAccessible(true);
        f.set(obj, val);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
//        System.out.println("process: " + roundEnv);
//        try {
//            {
//                StringBuilder builder = new StringBuilder()
//                        .append("package com.zhangjian.annotationprocessor.generated;\n\n")
//                        .append("public class GeneratedClass {\n\n")
//                        .append("\tpublic String getMessage() {\n")
//                        .append("\t\treturn \"test\";\n")
//                        .append("\t}\n")
//                        .append("}\n");
//                JavaFileObject source = processingEnv.getFiler().createSourceFile(
//                        "com.by122006.test.generated.GeneratedClass");
//                Writer writer = source.openWriter();
//                writer.write(builder.toString());
//                writer.flush();
//                writer.close();
//            }
//            roundEnv.getRootElements()
//                    .stream()
//                    .map(Symbol.ClassSymbol.class::cast)
//                    .forEach(elements -> {
//                        try {
//                            Reader reader = elements.sourcefile.openReader(true);
//                            reader.close();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    });
//        } catch (IOException e) {
//            //
//        }
        return false;
    }

    static class WaitAnalyzeTaskListener implements TaskListener {
        JavaCompiler compiler;
        ClassLoader pclassloader;
        boolean done = false;

        public WaitAnalyzeTaskListener(JavaCompiler compiler, ClassLoader pclassloader) {
            this.compiler = compiler;
            this.pclassloader = pclassloader;
        }


        @Override
        public void started(TaskEvent e) {
            System.out.println("started:" + pclassloader + "    TaskEvent.Kind." + e.getKind().name());
            if (e.getKind() == TaskEvent.Kind.ANALYZE && !done) {
                patch(compiler, pclassloader);
                e.getCompilationUnit().accept(new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitLiteral(LiteralTree node, Void unused) {
                        JCTree.JCLiteral var3 = (JCTree.JCLiteral) node;
                        System.out.println(var3.getTag() + ": " + node.getValue());
                        return super.visitLiteral(node, unused);
                    }
                }, null);
                done = true;
            }
        }

        @Override
        public void finished(TaskEvent e) {
        }
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

    static void patch(JavaCompiler compiler, ClassLoader pcl) {
        try {
            System.out.println("patch start");
            JavaCompiler delCompiler = (JavaCompiler) get(compiler, "delegateCompiler");
            if (delCompiler != null)
                compiler = delCompiler; // javac has delegateCompiler. netbeans hasn't
            Context context = (Context) get(compiler, "context");
//            if (attr instanceof OOAttr)
//                return;
            ClassLoader classLoader = Attr.class.getClassLoader();

            // hack: load classes to the same classloader so they will be able to use and override default accessor members
            Class<?> attrClass = reloadClass("com.sun.tools.javac.comp.OOAttr", pcl, Attr.class.getClassLoader());
            Class<?> resolveClass = reloadClass("com.sun.tools.javac.comp.OOResolve", pcl, Resolve.class.getClassLoader());
            Class<?> transTypesClass = reloadClass("com.sun.tools.javac.comp.OOTransTypes", pcl, TransTypes.class.getClassLoader());
            reloadClass("javaoo.OOMethods", pcl, TransTypes.class.getClassLoader());
            reloadClass("javaoo.OOMethods$1", pcl, TransTypes.class.getClassLoader());
            reloadClass("javaoo.OOMethods$2", pcl, TransTypes.class.getClassLoader());

            getInstance(resolveClass, context);
            Object attr = getInstance(attrClass, context);
            Object transTypes = getInstance(transTypesClass, context);

            set(compiler, "attr", attr);
            set(MemberEnter.instance(context), "attr", attr);
            set(compiler, "transTypes", transTypes);

            reloadClass("com.sun.tools.javac.parser.OOJavaTokenizer", pcl, classLoader);
            Class<?> OOScannerClass = reloadClass("com.sun.tools.javac.parser.OOScanner", pcl, classLoader);
            Class<?> OOScannerFactoryClass = reloadClass("com.sun.tools.javac.parser.OOScannerFactory", pcl, classLoader);

//            context.put(ScannerFactory.scannerFactoryKey, (ScannerFactory) null);
            ScannerFactory var1 = (ScannerFactory) context.get(ScannerFactory.scannerFactoryKey);
            ParserFactory parserFactory = (ParserFactory) get(compiler, "parserFactory");
            System.out.println("OOScannerFactoryClass=" + OOScannerFactoryClass);
            System.out.println(Stream.of(OOScannerFactoryClass.getDeclaredMethods()).map(Method::getName).collect(Collectors.joining("    ,")));
            Object instance = getInstance(OOScannerFactoryClass, context);
            System.out.println("ScannerFactory=" + instance);
            set(parserFactory, "scannerFactory", instance);
            System.out.println("patch end");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
//    static <T> Class reloadClass2(String clazz) throws Exception {
//        if (clazz.getClassLoader() == Attr.class.getClassLoader()) {
//            System.out.println(clazz.getName() + ": already loaded");
//            return clazz;
//        }
//        System.out.println(clazz.getName() + ": will loaded");
//        String name = clazz.getName().replace('.', '/') + ".class";
//        URL resource = clazz.getClassLoader().getResource(name);
//        InputStream is = null;
//        try {
//            is = resource.openStream();
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException(clazz.getName()+": 在["+OOProcessor.class.getClassLoader()+"]中异常 ("+ resource);
//        }
//
//        byte[] bytes = new byte[is.available()];
//        is.read(bytes);
//        Method m = ClassLoader.class.getDeclaredMethod("defineClass", new Class[]{
//                String.class, byte[].class, int.class, int.class});
//        m.setAccessible(true);
//        return (Class) m.invoke(Attr.class.getClassLoader(), clazz.getName(), bytes, 0, bytes.length);
//
//    }

    @SuppressWarnings("unchecked")
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
        Method m = ClassLoader.class.getDeclaredMethod("defineClass", new Class[]{
                String.class, byte[].class, int.class, int.class});
        m.setAccessible(true);
        return (Class<T>) m.invoke(outcl, claz, bytes, 0, bytes.length);


//        String path = claz.replace('.', '/') + ".class";
//        System.out.println("path=" + path);
//        System.out.println("outcl=" + outcl);
//        System.out.println("incl=" +incl);
//
//        System.out.println(OOProcessor.class.getResource("").getPath());
//        System.out.println(incl.getResource("/"));
//        try { // already loaded?
//            Class<T> tClass = (Class<T>) outcl.loadClass(claz);
//            System.out.println(outcl.getClass() + " " + outcl.getClass().hashCode() + ": already loaded");
//            return tClass;
//        } catch (ClassNotFoundException e) {
//        }
//        try {
//            InputStream is =incl.getResourceAsStream(path);
//            byte[] bytes = new byte[is.available()];
//            is.read(bytes);
//            Method m = ClassLoader.class.getDeclaredMethod("defineClass", new Class[]{
//                    String.class, byte[].class, int.class, int.class});
//            m.setAccessible(true);
//            return (Class<T>) m.invoke(outcl, claz, bytes, 0, bytes.length);
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException(claz + ": 在[" + OOProcessor.class.getClassLoader() + "]中异常 (" + path);
//        }
    }
}
