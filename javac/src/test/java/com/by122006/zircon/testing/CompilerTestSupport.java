package com.by122006.zircon.testing;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

/** Compiles fixtures independently of Gradle's test sources, including invalid Java. */
public final class CompilerTestSupport {
    public static final String[] STRING = {"ZrString"};
    public static final String[] ALL = {"ZrString", "ZrExMethod", "ZrOptionalChain"};

    private CompilerTestSupport() {}

    public static JavaFileObject source(String name, final String text) {
        return new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return text; }
        };
    }

    public static Result compile(File directory, String name, String text, String... plugins) throws Exception {
        return compile(directory, Collections.singletonList(source(name, text)),
                System.getProperty("zircon.test.classpath"), plugins, null);
    }

    public static Result compile(File directory, Iterable<? extends JavaFileObject> sources,
                                 String classpath, String[] plugins, Iterable<? extends Processor> processors)
            throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("Compiler tests require a JDK", compiler);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter output = new StringWriter();
        boolean success = false;
        Throwable crash = null;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            manager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(directory));
            List<String> options = new ArrayList<>(Arrays.asList("-source", "8", "-target", "8",
                    "-encoding", "UTF-8", "-classpath", classpath));
            if (processors == null) options.add("-proc:none");
            boolean java8 = System.getProperty("java.specification.version").equals("1.8");
            if (!java8) {
                for (String plugin : plugins) options.add("-Xplugin:" + plugin);
            }
            try {
                JavaCompiler.CompilationTask task = compiler.getTask(output, manager, diagnostics, options, null, sources);
                if (processors != null) task.setProcessors(processors);
                // JDK 8's command-line plugin iteration mutates its option set when
                // several plugins are requested. Initialize them through the public API.
                if (java8 && plugins.length != 0) {
                    Map<String, Plugin> available = new HashMap<>();
                    for (Plugin plugin : ServiceLoader.load(Plugin.class, CompilerTestSupport.class.getClassLoader())) {
                        available.put(plugin.getName(), plugin);
                    }
                    for (String name : plugins) {
                        Plugin plugin = available.get(name);
                        if (plugin == null) throw new IllegalStateException("Missing test plugin: " + name);
                        plugin.init((JavacTask) task);
                    }
                }
                success = task.call();
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                crash = failure;
            }
        }
        return new Result(success, crash, directory, classpath, diagnostics.getDiagnostics(), output.toString());
    }

    public enum Status { COMPILED, REJECTED, CRASHED }

    public static final class Result implements AutoCloseable {
        public final Status status;
        public final List<Diagnostic<? extends JavaFileObject>> errors;
        public final String output;
        public final Throwable crash;
        private final URLClassLoader loader;

        private Result(boolean success, Throwable crash, File directory, String classpath,
                       List<Diagnostic<? extends JavaFileObject>> diagnostics, String output) throws Exception {
            this.crash = crash;
            this.output = output;
            List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) errors.add(diagnostic);
            }
            this.errors = Collections.unmodifiableList(errors);
            // javac can catch its own internal exception and only print a stack trace.
            boolean internalFailure = output.contains("compiler.misc.msg.bug")
                    || output.contains("java.lang.ClassCastException")
                    || output.contains("\tat com.sun.tools.javac.")
                    || output.contains("\tat jdk.compiler/");
            status = crash != null || internalFailure || !success && errors.isEmpty() ? Status.CRASHED
                    : success && errors.isEmpty() ? Status.COMPILED : Status.REJECTED;
            List<URL> urls = new ArrayList<>();
            urls.add(directory.toURI().toURL());
            for (String path : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                urls.add(new File(path).toURI().toURL());
            }
            loader = new URLClassLoader(urls.toArray(new URL[0]), CompilerTestSupport.class.getClassLoader());
            loader.setDefaultAssertionStatus(true);
        }

        public Result assertCompiled() {
            assertEquals(describe(), Status.COMPILED, status);
            return this;
        }

        public Result assertRejected(int count) {
            assertEquals(describe(), Status.REJECTED, status);
            assertEquals(describe(), count, errors.size());
            return this;
        }

        public Diagnostic<? extends JavaFileObject> assertError(String code, String message,
                                                               String sourceName, long line, long column) {
            assertEquals(describe(), Status.REJECTED, status);
            for (Diagnostic<? extends JavaFileObject> error : errors) {
                if (error.getCode().equals(code) && error.getMessage(Locale.ROOT).contains(message)
                        && error.getSource() != null && error.getSource().getName().endsWith(sourceName)
                        && error.getLineNumber() == line && (column < 0 || error.getColumnNumber() == column)) {
                    return error;
                }
            }
            fail("Missing expected error " + code + " / " + message + " at " + sourceName
                    + ":" + line + ":" + column + "\n" + describe());
            return null;
        }

        public Object run(String className, String method) throws Exception {
            assertCompiled();
            return loader.loadClass(className).getMethod(method).invoke(null);
        }

        public void runInstance(String className, String method) throws Exception {
            assertCompiled();
            Class<?> type = loader.loadClass(className);
            type.getMethod(method).invoke(type.getDeclaredConstructor().newInstance());
        }

        public String describe() {
            StringWriter trace = new StringWriter();
            if (crash != null) crash.printStackTrace(new java.io.PrintWriter(trace));
            return status + "\n" + output + "\n" + errors + "\n" + trace;
        }

        @Override
        public void close() throws Exception { loader.close(); }
    }
}
