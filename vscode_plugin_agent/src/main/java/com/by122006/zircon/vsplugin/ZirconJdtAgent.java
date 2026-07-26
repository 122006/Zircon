package com.by122006.zircon.vsplugin;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.jar.JarFile;

public class ZirconJdtAgent {

    public static void premain(String arguments, Instrumentation instrumentation) {
        Util.log("[Zircon] Starting VSCode JDT agent...");
        boolean syntaxServer = isSyntaxServerProcess();
        if (syntaxServer) {
            Util.log("[Zircon] Red Hat Java syntax server detected; installing scanner-only advice.");
        }

        try {
            appendBootstrapHelpers(instrumentation);
            File agentJar = locateAgentJar();

            AgentBuilder builder = new AgentBuilder.Default()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(new AgentBuilder.Listener() {
                        @Override
                        public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
                        }

                        @Override
                        public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
                            Util.log("[Zircon] transformed type: " + typeDescription.getName() + ", loaded=" + loaded);
                        }

                        @Override
                        public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
                            Util.log("[Zircon] transform error: " + typeName + " -> " + throwable.getClass().getName() + ": " + throwable.getMessage());
                        }

                        @Override
                        public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
                        }
                    })
                    .ignore(ElementMatchers.nameStartsWith("net.bytebuddy.")
                            .or(ElementMatchers.nameStartsWith("com.by122006.zircon.vsplugin.")));

            builder = builder.type(ElementMatchers.named("org.eclipse.jdt.internal.compiler.problem.ProblemReporter"))
                    .transform(new AgentBuilder.Transformer.ForAdvice()
                            .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                            .include(ClassFileLocator.ForJarFile.of(agentJar))
                            .advice(ElementMatchers.named("handle"),
                                    ProblemReporterHandleAdvice.class.getName())
                            .advice(ElementMatchers.named("nonStaticAccessToStaticMethod")
                                            .or(ElementMatchers.named("indirectAccessToStaticMethod"))
                                            .or(ElementMatchers.named("methodMustBeAccessedStatically"))
                                            .and(ElementMatchers.takesArguments(2)),
                                    ProblemReporterStaticAccessAdvice.class.getName()));

            builder = builder.type(ElementMatchers.named("org.eclipse.jdt.internal.compiler.CompilationResult"))
                    .transform(new AgentBuilder.Transformer.ForAdvice()
                            .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                            .include(ClassFileLocator.ForJarFile.of(agentJar))
                            .advice(ElementMatchers.named("record")
                                            .and(ElementMatchers.takesArguments(2)
                                                    .or(ElementMatchers.takesArguments(3))),
                                    CompilationResultRecordAdvice.class.getName())
                            .advice(ElementMatchers.namedOneOf("getProblems", "getErrors", "getAllProblems")
                                            .and(ElementMatchers.takesArguments(0))
                                            .and(ElementMatchers.returns(ElementMatchers.isArray())),
                                    CompilationResultProblemsAdvice.class.getName()));

            if (!syntaxServer) {
                builder = builder.type(ElementMatchers.named("org.eclipse.jdt.internal.codeassist.CompletionEngine"))
                        .transform(new AgentBuilder.Transformer.ForAdvice()
                                .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                                .include(ClassFileLocator.ForJarFile.of(agentJar))
                                .advice(ElementMatchers.named("findMethods")
                                                .and(ElementMatchers.takesArguments(20)),
                                        CompletionEngineFindMethodsAdvice.class.getName()));

                builder = builder.type(ElementMatchers.named(
                                "org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalRequestor"))
                        .transform(new AgentBuilder.Transformer.ForAdvice()
                                .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                                .include(ClassFileLocator.ForJarFile.of(agentJar))
                                .advice(ElementMatchers.named("accept")
                                                .and(ElementMatchers.takesArguments(1)),
                                        JdtCompletionProposalRequestorAdvice.class.getName()));

                builder = builder.type(ElementMatchers.named(
                                "org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalReplacementProvider"))
                        .transform(new AgentBuilder.Transformer.ForAdvice()
                                .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                                .include(ClassFileLocator.ForJarFile.of(agentJar))
                                .advice(ElementMatchers.named("updateReplacement")
                                                .and(ElementMatchers.takesArguments(3)),
                                        JdtCompletionReplacementAdvice.class.getName()));

                builder = builder.type(ElementMatchers.named("org.eclipse.jdt.internal.compiler.lookup.Scope")
                                .or(ElementMatchers.named("org.eclipse.jdt.internal.compiler.lookup.BlockScope"))
                                .or(ElementMatchers.named("org.eclipse.jdt.internal.compiler.lookup.ClassScope"))
                                .or(ElementMatchers.named("org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope"))
                                .or(ElementMatchers.named("org.eclipse.jdt.internal.compiler.lookup.MethodScope")))
                        .transform(new AgentBuilder.Transformer.ForAdvice()
                                .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                                .include(ClassFileLocator.ForJarFile.of(agentJar))
                                .advice(ElementMatchers.named("getMethod")
                                                .or(ElementMatchers.named("getImplicitMethod")),
                                        ScopeAdvice.class.getName()));

                builder = builder.type(ElementMatchers.named("org.eclipse.jdt.internal.compiler.ast.MessageSend"))
                        .transform(new AgentBuilder.Transformer.ForAdvice()
                                .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                                .include(ClassFileLocator.ForJarFile.of(agentJar))
                                .advice(ElementMatchers.named("resolveType"),
                                        MessageSendResolveTypeAdvice.class.getName())
                                .advice(ElementMatchers.named("analyseCode"),
                                        MessageSendAnalyseCodeAdvice.class.getName())
                                .advice(ElementMatchers.named("nullStatus"),
                                        MessageSendNullStatusAdvice.class.getName())
                                .advice(ElementMatchers.named("generateCode"),
                                        MessageSendGenerateCodeAdvice.class.getName()));

                builder = builder.type(ElementMatchers.named("org.eclipse.jdt.internal.core.search.matching.MethodLocator"))
                        .transform(new AgentBuilder.Transformer.ForAdvice()
                                .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                                .include(ClassFileLocator.ForJarFile.of(agentJar))
                                .advice(ElementMatchers.takesArgument(0,
                                                ElementMatchers.named("org.eclipse.jdt.internal.compiler.lookup.MethodBinding")),
                                        JdtSearchBindingAdvice.class.getName())
                                .advice(ElementMatchers.named("resolveLevel")
                                                .and(ElementMatchers.takesArguments(1))
                                                .and(ElementMatchers.takesArgument(0,
                                                        ElementMatchers.named("org.eclipse.jdt.internal.compiler.lookup.Binding"))),
                                        JdtSearchBindingAdvice.class.getName())
                                .advice(ElementMatchers.named("resolveLevel")
                                                .and(ElementMatchers.takesArguments(1))
                                                .and(ElementMatchers.takesArgument(0,
                                                        ElementMatchers.named("org.eclipse.jdt.internal.compiler.ast.MessageSend")
                                                                .or(ElementMatchers.named("org.eclipse.jdt.internal.compiler.ast.ReferenceExpression")))),
                                        JdtSearchNodeAdvice.class.getName())
                                .advice(ElementMatchers.named("match")
                                                .and(ElementMatchers.takesArguments(2))
                                                .and(ElementMatchers.takesArgument(0,
                                                        ElementMatchers.named("org.eclipse.jdt.internal.compiler.ast.MessageSend")))
                                                .and(ElementMatchers.takesArgument(1,
                                                        ElementMatchers.named("org.eclipse.jdt.internal.core.search.matching.MatchingNodeSet"))),
                                        JdtSearchCandidateAdvice.class.getName())
                                .advice(ElementMatchers.named("matchReportReference")
                                                .and(ElementMatchers.takesArguments(4))
                                                .and(ElementMatchers.takesArgument(0,
                                                        ElementMatchers.named("org.eclipse.jdt.internal.compiler.ast.MessageSend"))),
                                        JdtSearchReportAdvice.class.getName()));

                builder = builder.type(ElementMatchers.named("org.eclipse.jdt.internal.compiler.ast.LambdaExpression"))
                        .transform(new AgentBuilder.Transformer.ForAdvice()
                                .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                                .include(ClassFileLocator.ForJarFile.of(agentJar))
                                .advice(ElementMatchers.named("analyseCode"),
                                        LambdaExpressionAnalyseCodeAdvice.class.getName())
                                .advice(ElementMatchers.named("generateCode"),
                                        LambdaExpressionGenerateCodeAdvice.class.getName()));
            }

            builder = builder.type(ElementMatchers.named("org.eclipse.jdt.internal.compiler.ast.ConditionalExpression"))
                    .transform(new AgentBuilder.Transformer.ForAdvice()
                            .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                            .include(ClassFileLocator.ForJarFile.of(agentJar))
                            .advice(ElementMatchers.named("resolveType"),
                                    ConditionalExpressionResolveTypeAdvice.class.getName())
                            .advice(ElementMatchers.named("analyseCode"),
                                    ConditionalExpressionAnalyseCodeAdvice.class.getName())
                            .advice(ElementMatchers.named("generateCode"),
                                    ConditionalExpressionGenerateCodeAdvice.class.getName())
                            .advice(ElementMatchers.named("generateOptimizedBoolean"),
                                    ConditionalExpressionGenerateOptimizedBooleanAdvice.class.getName()));

            builder.type(ElementMatchers.named("org.eclipse.jdt.internal.compiler.parser.Scanner")
                            .or(ElementMatchers.named("org.eclipse.jdt.internal.compiler.parser.RecoveryScanner"))
                            .or(ElementMatchers.named("org.eclipse.jdt.internal.codeassist.complete.CompletionScanner"))
                            .or(ElementMatchers.named("org.eclipse.jdt.internal.codeassist.select.SelectionScanner")))
                    .transform(new AgentBuilder.Transformer.ForAdvice()
                            .withExceptionHandler(net.bytebuddy.asm.Advice.ExceptionHandler.Default.PRINTING)
                            .include(ClassFileLocator.ForJarFile.of(agentJar))
                            .advice(ElementMatchers.named("getNextToken")
                                            .or(ElementMatchers.named("getNextToken0")),
                                    TemplateScannerAdvice.class.getName())
                            .advice(ElementMatchers.named("getCurrentTokenSource")
                                            .or(ElementMatchers.named("getCurrentIdentifierSource"))
                                            .or(ElementMatchers.named("getCurrentTokenSourceString"))
                                            .or(ElementMatchers.named("getCurrentStringLiteral"))
                                            .or(ElementMatchers.named("getRawTokenSource"))
                                            .or(ElementMatchers.named("getCurrentTokenStartPosition"))
                                            .or(ElementMatchers.named("getCurrentTokenEndPosition")),
                                    TemplateScannerAccessorAdvice.class.getName())
                            .advice(ElementMatchers.named("resetTo")
                                            .or(ElementMatchers.named("setSource")),
                                    TemplateScannerResetAdvice.class.getName()))
                    .installOn(instrumentation);

            if (!syntaxServer) {
                writeRuntimeHeartbeat(agentJar);
            }
            Util.log("[Zircon] Agent installed successfully.");
        } catch (Throwable e) {
            Util.log("[Zircon] Agent install failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void appendBootstrapHelpers(Instrumentation instrumentation) throws Exception {
        File helperJar = locateBootstrapHelperJar();
        if (helperJar == null || !helperJar.isFile()) {
            Util.log("[Zircon] Bootstrap helper jar not found.");
            return;
        }
        JarFile jarFile = new JarFile(helperJar);
        instrumentation.appendToBootstrapClassLoaderSearch(jarFile);
        instrumentation.appendToSystemClassLoaderSearch(jarFile);
        Util.log("[Zircon] Bootstrap helper jar appended: " + helperJar.getAbsolutePath());
    }

    private static File locateBootstrapHelperJar() throws URISyntaxException {
        File agentJar = locateAgentJar();
        File parent = agentJar == null ? null : agentJar.getParentFile();
        if (parent == null) {
            return null;
        }
        return new File(parent, "zircon-agent-bootstrap.jar");
    }

    private static File locateAgentJar() throws URISyntaxException {
        CodeSource codeSource = ZirconJdtAgent.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return null;
        }
        return new File(codeSource.getLocation().toURI());
    }

    private static void writeRuntimeHeartbeat(File agentJar) {
        String configuredPath = System.getProperty("zircon.agent.heartbeat", "").trim();
        if (configuredPath.isEmpty()) {
            return;
        }
        try {
            writeRuntimeHeartbeat(
                    Paths.get(configuredPath),
                    agentJar,
                    ProcessHandle.current().pid(),
                    System.currentTimeMillis()
            );
        } catch (Throwable error) {
            Util.log("[Zircon] Agent heartbeat failed: "
                    + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    static void writeRuntimeHeartbeat(Path heartbeat, File agentJar, long pid, long startedAtMillis)
            throws IOException {
        Path absolute = heartbeat.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String agentPath = agentJar == null
                ? ""
                : agentJar.toPath().toAbsolutePath().normalize().toString();
        String content = "pid=" + pid + "\n"
                + "startedAt=" + startedAtMillis + "\n"
                + "agentJar=" + agentPath + "\n"
                + "mode=full\n";
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp." + pid);
        Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(
                    temporary,
                    absolute,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException atomicMoveError) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isSyntaxServerProcess() {
        String command = System.getProperty("sun.java.command", "");
        return command.contains("config_ss_") || command.contains("ss_ws");
    }
}
