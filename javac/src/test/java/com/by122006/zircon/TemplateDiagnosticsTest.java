package com.by122006.zircon;

import com.by122006.zircon.testing.CompilerTestCase;
import com.by122006.zircon.testing.CompilerTestSupport;
import org.junit.Test;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class TemplateDiagnosticsTest extends CompilerTestCase {
    @Test
    public void invalidFixturesProduceExactlyTheirExpectedDiagnostic() throws Exception {
        Path root = Paths.get(getClass().getResource("/compiler/negative").toURI());
        List<Path> cases;
        try (Stream<Path> paths = Files.list(root)) {
            cases = paths.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        }
        assertFalse("No negative fixtures found", cases.isEmpty());
        for (Path fixture : cases) {
            Properties expected = new Properties();
            try (Reader reader = Files.newBufferedReader(fixture.resolve("expected.properties"), StandardCharsets.UTF_8)) {
                expected.load(reader);
            }
            String source = new String(Files.readAllBytes(fixture.resolve("CacheCase.java")), StandardCharsets.UTF_8);
            String anchor = expected.getProperty("anchor");
            int position = source.indexOf(anchor);
            assertTrue(fixture + ": missing diagnostic anchor", position >= 0);
            assertEquals(fixture + ": diagnostic anchor must be unique", position, source.lastIndexOf(anchor));
            position += Integer.parseInt(expected.getProperty("offset", "0"));
            int line = 1, column = 1;
            for (int i = 0; i < position; i++) {
                if (source.charAt(i) == '\n') { line++; column = 1; } else { column++; }
            }
            try (CompilerTestSupport.Result result = compile(source, CompilerTestSupport.ALL)) {
                result.assertRejected(1);
                Diagnostic<? extends JavaFileObject> error = result.assertError(
                        expected.getProperty("javacCode", "compiler.err.proc.messager"),
                        expected.getProperty("code", expected.getProperty("message", "")),
                        "CacheCase.java", line, column);
                assertTrue(error.toString(), error.getMessage(null).contains(expected.getProperty("message", "")));
                assertTrue(error.toString(), error.getMessage(null).contains(expected.getProperty("hint", "")));
                if (expected.containsKey("length")) {
                    assertEquals(error.toString(), position, error.getStartPosition());
                    assertEquals(error.toString(), position + Integer.parseInt(expected.getProperty("length")), error.getEndPosition());
                }
            }
        }
    }

    @Test
    public void reportsIndependentErrorsAndRecoversForFollowingStatements() throws Exception {
        String source = "public class CacheCase {\n"
                + "  void test() {\n"
                + "    String one = $\"${%s:1}\";\n"
                + "    String two = f\"${%Q:2}\";\n"
                + "    String valid = $\"${3}\";\n"
                + "  }\n}\n";
        try (CompilerTestSupport.Result result = compile(source, CompilerTestSupport.ALL)) {
            result.assertRejected(2).assertError("compiler.err.proc.messager", "ZR1003", "CacheCase.java", 3, -1);
            result.assertError("compiler.err.proc.messager", "ZR1006", "CacheCase.java", 4, -1);
        }
    }
}
