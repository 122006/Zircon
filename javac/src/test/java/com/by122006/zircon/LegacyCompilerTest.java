package com.by122006.zircon;

import com.by122006.zircon.testing.CompilerTestCase;
import com.by122006.zircon.testing.CompilerTestSupport;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;

public class LegacyCompilerTest extends CompilerTestCase {
    @Test
    public void existingFeatureExamplesCompileAndRun() throws Exception {
        Path root = Paths.get(getClass().getResource("/compiler/legacy").toURI());
        List<JavaFileObject> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path -> {
                try {
                    String name = root.relativize(path).toString().replace('\\', '/').replaceAll("\\.java$", "");
                    sources.add(CompilerTestSupport.source(name, new String(Files.readAllBytes(path), StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot read legacy fixture " + path, e);
                }
            });
        }
        assertFalse("Legacy fixture directory is empty", sources.isEmpty());
        try (CompilerTestSupport.Result result = CompilerTestSupport.compile(temporary.newFolder(), sources,
                System.getProperty("zircon.test.legacyClasspath"), CompilerTestSupport.ALL, null)) {
            result.assertCompiled();
            result.runInstance("test.TextStringFormat", "test");
            result.runInstance("test.TextStringFormat", "test1");
            result.runInstance("test.TestExMethodImpl", "test");
            result.runInstance("test.TestOptionalChaining", "testBasic");
            result.runInstance("test.TestImport", "test");
        }
    }
}
