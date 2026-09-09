package com.by122006.zircon;

import com.by122006.zircon.testing.CompilerTestCase;
import com.by122006.zircon.testing.CompilerTestSupport;
import org.junit.Test;
import java.util.Arrays;

public class TemplateArgumentCacheTest extends CompilerTestCase {
    @Test
    public void mixedTemplatesInThrownMethodChain() throws Exception {
        String source = "public class CacheCase {\n"
                + "  static class Failure extends RuntimeException {\n"
                + "    String detail;\n"
                + "    Failure(String message) { super(message); }\n"
                + "    Failure detail(String value) { detail = value; return this; }\n"
                + "  }\n"
                + "  public static String run() {\n"
                + "    int code = 42;\n"
                + "    try {\n"
                + "      throw new Failure(f\"code=${code}\").detail($\"detail=${code}\");\n"
                + "    } catch (Failure failure) {\n"
                + "      return failure.getMessage() + \"|\" + failure.detail;\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
        assertRuns("code=42|detail=42", source, false);
        // The string plugin must work both on its own and with ZrAttr installed.
        assertRuns("code=42|detail=42", source, true);
    }

    @Test
    public void multipleTemplatesAndEmbeddedExpressionsKeepTheirValuesAndOrder() throws Exception {
        assertRuns("1|2|34|[5,6]", "public class CacheCase {\n"
                + "  static int counter;\n"
                + "  static int next() { return ++counter; }\n"
                + "  static <T> T id(T value) { return value; }\n"
                + "  static String join(String... values) { return String.join(\"|\", values); }\n"
                + "  public static String run() {\n"
                + "    return join($\"${next()}\", f\"${next()}\",\n"
                + "        $\"${id(next())}${id(next())}\", j\"[next(),next()]\");\n"
                + "  }\n"
                + "}\n", false);
    }

    @Test
    public void genericCallsAndOverloadsCanSpeculateOverTemplates() throws Exception {
        assertRuns("string:12|string:34", "public class CacheCase {\n"
                + "  static <T> T id(T value) { return value; }\n"
                + "  static String choose(String value) { return \"string:\" + value; }\n"
                + "  static String choose(Object value) { return \"object\"; }\n"
                + "  static String join(String left, String right) { return left + \"|\" + right; }\n"
                + "  public static String run() {\n"
                + "    return join(choose(id($\"${id(1)}${id(2)}\")),\n"
                + "        choose(id(f\"${id(3)}${id(4)}\")));\n"
                + "  }\n"
                + "}\n", false);
    }

    @Test
    public void invalidTemplateArgumentReportsItsActualSourceLine() throws Exception {
        for (String template : Arrays.asList("f\"value=${1}\"", "$\"${1}\"", "j\"[1]\"", "$\"\"")) {
            try (CompilerTestSupport.Result compilation = compile("public class CacheCase {\n"
                    + "  static void acceptsInt(int value) {}\n"
                    + "  static void run() {\n"
                    + "    acceptsInt(" + template + ");\n"
                    + "  }\n"
                    + "}\n", CompilerTestSupport.STRING)) {
                compilation.assertRejected(1).assertError("compiler.err.prob.found.req", "String", "CacheCase.java", 4, -1);
            }
        }
    }

    @Test
    public void escapedCodeKeepsNumericTokensAndOptionalChains() throws Exception {
        assertRuns("text10.5|4", "public class CacheCase {\n"
                + "  public static String run() {\n"
                + "    return f\"$" + "{\\\"text\\\" + (1 + 0x2 + 3L + 4.5)}\""
                + " + \"|\" + $\"$" + "{\\\"text\\\"?.length()}\";\n"
                + "  }\n}\n", true);
    }

    @Test
    public void jsonAndFormatFieldsRetainTheirEstablishedSyntax() throws Exception {
        assertRuns("[\"]\",\"[}\",2]|002ms|v/v", "public class CacheCase {\n"
                + "  public static String run() {\n"
                + "    return j\"[String.valueOf(']'),\"[}\",(1 /* ] } */ + 1)]\""
                + " + f\"|$" + "{%03dms:2}|$" + "{%s/%<s:\"v\"}\";\n"
                + "  }\n}\n", true);
    }

}
