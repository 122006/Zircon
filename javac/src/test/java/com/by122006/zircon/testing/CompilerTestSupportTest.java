package com.by122006.zircon.testing;

import org.junit.Test;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.*;

public class CompilerTestSupportTest extends CompilerTestCase {
    @Test
    public void rejectsUnexpectedCompilationSuccess() throws Exception {
        try (CompilerTestSupport.Result result = compile("public class CacheCase {}")) {
            result.assertCompiled();
            assertThrows(AssertionError.class, () -> result.assertRejected(1));
        }
    }

    @Test
    public void requiresTheExpectedDiagnosticAndLocation() throws Exception {
        try (CompilerTestSupport.Result result = compile("public class CacheCase {\n"
                + "  int value = missing;\n}\n")) {
            result.assertRejected(1).assertError("compiler.err.cant.resolve.location", "missing", "CacheCase.java", 2, 15);
            assertThrows(AssertionError.class, () -> result.assertError("wrong.code", "missing", "CacheCase.java", 2, 15));
            assertThrows(AssertionError.class, () -> result.assertError("compiler.err.cant.resolve.location", "missing", "CacheCase.java", 1, 15));
        }
    }

    @Test
    public void processorCrashIsNeverAnExpectedCompileFailure() throws Exception {
        try (CompilerTestSupport.Result result = CompilerTestSupport.compile(temporary.newFolder(),
                Collections.singletonList(CompilerTestSupport.source("CacheCase", "public class CacheCase {}")),
                System.getProperty("zircon.test.classpath"), new String[0], Collections.singletonList(new BrokenProcessor()))) {
            assertEquals(result.describe(), CompilerTestSupport.Status.CRASHED, result.status);
            assertEquals(result.describe(), 1, result.errors.size());
            assertThrows(AssertionError.class, () -> result.assertRejected(1));
        }
    }

    @SupportedAnnotationTypes("*")
    @SupportedSourceVersion(SourceVersion.RELEASE_8)
    public static class BrokenProcessor extends AbstractProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR, "diagnostic before crash");
            throw new IllegalStateException("deliberate processor crash");
        }
    }
}
