package com.by122006.zircon;

import com.by122006.zircon.testing.CompilerTestCase;
import com.by122006.zircon.testing.CompilerTestSupport;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

/** Runs on the selected javac, independently of the source/target language level. */
public class CompilerCompatibilityTest extends CompilerTestCase {
    @Test
    public void ordinaryQualifiedAndUnqualifiedCallsStillResolve() throws Exception {
        // The issue crashes on an ordinary unqualified call, even without extension syntax.
        assertRuns("string:ok|42|a,b", "public class CacheCase {\n"
                + "  String value = choose(identity(\"ok\"));\n"
                + "  static <T> T identity(T value) { return value; }\n"
                + "  static String choose(String value) { return \"string:\" + value; }\n"
                + "  static String choose(Object value) { return \"object\"; }\n"
                + "  static int boxed(Integer value) { return value; }\n"
                + "  public static String run() {\n"
                + "    return new CacheCase().value + \"|\" + boxed(42) + \"|\" + String.join(\",\", \"a\", \"b\");\n"
                + "  }\n}\n", true);
    }

    @Test
    public void extensionsResolveReceiversGenericsAndVarargs() throws Exception {
        assertRuns("self|first|x:1:2", "import zircon.ExMethod;\n"
                + "import java.util.*;\n"
                + "public class CacheCase {\n"
                + "  @ExMethod public static String label(CacheCase self) { return \"self\"; }\n"
                + "  @ExMethod public static <T> T first(List<T> self) { return self.get(0); }\n"
                + "  @ExMethod public static String append(String self, Integer... values) {\n"
                + "    for (Integer value : values) self += \":\" + value; return self;\n"
                + "  }\n"
                + "  String implicitReceiver() { return label(); }\n"
                + "  public static String run() {\n"
                + "    return new CacheCase().implicitReceiver() + \"|\" + Arrays.asList(\"first\").first()\n"
                + "        + \"|\" + \"x\".append(1, 2);\n"
                + "  }\n}\n", true);
    }

    @Test
    public void extensionAndConstructorReferencesKeepTheirResults() throws Exception {
        assertRuns("[bound]|[unbound]|built", "import zircon.ExMethod;\n"
                + "import java.util.function.*;\n"
                + "public class CacheCase {\n"
                + "  @ExMethod public static String bracketed(String self) { return \"[\" + self + \"]\"; }\n"
                + "  public static String run() {\n"
                + "    Supplier<String> bound = \"bound\"::bracketed;\n"
                + "    Function<String, String> unbound = String::bracketed;\n"
                + "    Supplier<StringBuilder> constructor = StringBuilder::new;\n"
                + "    return bound.get() + \"|\" + unbound.apply(\"unbound\") + \"|\" + constructor.get().append(\"built\");\n"
                + "  }\n}\n", true);
    }

    @Test
    public void coverStillOverridesAnExistingMethod() throws Exception {
        assertRuns("covered:x", "import zircon.ExMethod;\n"
                + "public class CacheCase {\n"
                + "  @ExMethod(cover = true) public static String trim(String self) { return \"covered:\" + self; }\n"
                + "  public static String run() { return \"x\".trim(); }\n}\n", true);
    }

    @Test
    public void inheritedAbstractMethodsKeepCovariantReturnTypes() throws Exception {
        // javac 24 moved abstract-method merging out of BasicLookupHelper.lookup.
        assertRuns("ok", "public class CacheCase {\n"
                + "  interface A { CharSequence value(); }\n"
                + "  interface B { String value(); }\n"
                + "  interface C extends A, B {}\n"
                + "  static String read(C value) { return value.value(); }\n"
                + "  public static String run() { return read(new C() { public String value() { return \"ok\"; } }); }\n"
                + "}\n", true);
    }

    @Test
    public void ambiguousJavaOverloadsProduceADiagnostic() throws Exception {
        try (CompilerTestSupport.Result result = compile("public class CacheCase {\n"
                + "  static void pick(String value) {}\n"
                + "  static void pick(Integer value) {}\n"
                + "  void run() { pick(null); }\n}\n", CompilerTestSupport.ALL)) {
            result.assertRejected(1).assertError("compiler.err.ref.ambiguous", "pick", "CacheCase.java", 4, -1);
        }
    }

    @Test
    public void optionalChainsAndTemplatesEvaluateSideEffectsOnce() throws Exception {
        assertRuns("fallback|[ok!]|2|1|1", "import zircon.ExMethod;\n"
                + "public class CacheCase {\n"
                + "  static int receivers, arguments, fallbacks;\n"
                + "  static String receiver(String value) { receivers++; return value; }\n"
                + "  static String argument() { arguments++; return \"!\"; }\n"
                + "  static String fallback() { fallbacks++; return \"fallback\"; }\n"
                + "  @ExMethod public static String bracketed(String self) { return \"[\" + self + \"]\"; }\n"
                + "  public static String run() {\n"
                + "    String absent = receiver(null)?.concat(argument()).bracketed() ?: fallback();\n"
                + "    String present = receiver(\"ok\")?.concat(argument()).bracketed() ?: fallback();\n"
                + "    return $\"${absent}|${present}|${receivers}|${arguments}|${fallbacks}\";\n"
                + "  }\n}\n", true);
    }

    @Test
    public void generatedBytecodeUsesTheRequestedTarget() throws Exception {
        File directory = temporary.newFolder();
        try (CompilerTestSupport.Result result = CompilerTestSupport.compile(directory, "CacheCase",
                "public class CacheCase { public static String run() { return String.valueOf(25); } }",
                CompilerTestSupport.ALL)) {
            assertEquals("25", result.assertCompiled().run("CacheCase", "run"));
            try (DataInputStream bytecode = new DataInputStream(Files.newInputStream(new File(directory, "CacheCase.class").toPath()))) {
                assertEquals(0xCAFEBABE, bytecode.readInt());
                bytecode.readUnsignedShort();
                String release = System.getProperty("zircon.test.release", "");
                int target = Integer.parseInt(release.isEmpty() ? System.getProperty("zircon.test.target", "8") : release);
                assertEquals(target + 44, bytecode.readUnsignedShort());
            }
        }
    }
}
