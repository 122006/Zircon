package com.sun.tools.javac.parser;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TemplateStringSplitterTest {
    @Test
    public void keepsEmptyDollarExpression() {
        TemplateStringSplitter.Result result = TemplateStringSplitter.split(
                "$\"x=${}\"", "$", TemplateStringSplitter.Syntax.DOLLAR);

        assertEquals(2, result.ranges.size());
        assertRange(result.ranges.get(0), TemplateStringSplitter.STRING, 2, 4);
        assertRange(result.ranges.get(1), TemplateStringSplitter.CODE, 6, 6);
    }

    @Test
    public void keepsEmptyStrExpression() {
        TemplateStringSplitter.Result result = TemplateStringSplitter.split(
                "STR.\"x=\\{}\"", "STR.", TemplateStringSplitter.Syntax.STR);

        assertEquals(2, result.ranges.size());
        assertRange(result.ranges.get(0), TemplateStringSplitter.STRING, 5, 7);
        assertRange(result.ranges.get(1), TemplateStringSplitter.CODE, 9, 9);
    }

    @Test
    public void invalidTrailingDotDoesNotConsumeFollowingExpression() {
        String source = "$\"before=${local.}, after=${value}\"";
        TemplateStringSplitter.Result result = TemplateStringSplitter.split(
                source, "$", TemplateStringSplitter.Syntax.DOLLAR);

        List<String> expressions = result.ranges.stream()
                .filter(range -> range.style == TemplateStringSplitter.CODE)
                .map(range -> source.substring(range.startIndex, range.endIndex))
                .collect(Collectors.toList());
        assertEquals(java.util.Arrays.asList("local.", "value"), expressions);
    }

    @Test
    public void ignoresBracesInsideLiteralsAndComments() {
        String source = "$\"${call(\\\"}\\\", '{') /* } */ + value}\"";
        TemplateStringSplitter.Result result = TemplateStringSplitter.split(
                source, "$", TemplateStringSplitter.Syntax.DOLLAR);

        assertEquals(1, result.ranges.size());
        TemplateStringSplitter.Range code = result.ranges.get(0);
        assertEquals(
                "call(\\\"}\\\", '{') /* } */ + value",
                source.substring(code.startIndex, code.endIndex));
    }

    @Test
    public void incompleteTemplatesKeepPartialRangesAndDiagnostics() {
        TemplateStringSplitter.Result literal = TemplateStringSplitter.split(
                "$\"unfinished", "$", TemplateStringSplitter.Syntax.DOLLAR);
        assertEquals("ZR1001", literal.diagnostics.get(0).code);
        assertRange(literal.ranges.get(0), TemplateStringSplitter.STRING, 2, 12);

        TemplateStringSplitter.Result expression = TemplateStringSplitter.split(
                "$\"x=$" + "{value\"", "$", TemplateStringSplitter.Syntax.DOLLAR);
        assertEquals("ZR1002", expression.diagnostics.get(0).code);
        assertEquals(4, expression.diagnostics.get(0).start);
        assertEquals(6, expression.diagnostics.get(0).end);
        assertRange(expression.ranges.get(1), TemplateStringSplitter.CODE, 6, 11);
    }

    @Test(timeout = 5000)
    public void formatValidationPreservesSuffixesReusedArgumentsAndLargeWidths() {
        for (String format : new String[] {"%03dms", "%s / %<s", "%1$s", "%1$%", "%n%s",
                "%2147483647%", "%2147483647s"}) {
            TemplateStringSplitter.Result result = TemplateStringSplitter.split(
                    "f\"$" + "{" + format + ":value}\"", "f", TemplateStringSplitter.Syntax.FORMAT);
            assertTrue(format, result.diagnostics.isEmpty());
        }
        for (String format : new String[] {"%Q", "%--s", "%0s", "%.s", "%2147483648%", "%s %Q"}) {
            TemplateStringSplitter.Result result = TemplateStringSplitter.split(
                    "f\"$" + "{" + format + ":value}\"", "f", TemplateStringSplitter.Syntax.FORMAT);
            assertEquals(format, "ZR1006", result.diagnostics.get(0).code);
        }
    }

    @Test(timeout = 5000)
    public void jsonKeepsPartialRangesWithoutThrowingOrLooping() {
        JStringFormatter formatter = new JStringFormatter();
        for (String source : new String[] {"j\"\"", "j\"oops\"", "j\"{id:1]\"", "j\"{id:1\"", "j\"[1"}) {
            ZrStringModel result = formatter.build(source);
            assertEquals(source, "ZR1010", result.getDiagnostics().get(0).code);
        }
        assertEquals("ZR1001", formatter.build("j\"{};").getDiagnostics().get(0).code);
        assertTrue(formatter.build("j\"{id:1\"").getList().size() > 0);
    }

    @Test
    public void jsonIgnoresBracketsInLiteralsAndCommentsAndStopsAtItsOwnQuote() {
        JStringFormatter formatter = new JStringFormatter();
        String source = "j\"{a:'}',b:(1 /* ] } */ + 2),c:\"[\"}\"";
        ZrStringModel result = formatter.build(source + " + $\"$" + "{1}\";");
        assertTrue(result.getDiagnostics().toString(), result.getDiagnostics().isEmpty());
        assertEquals(source, result.getOriginalString());
        assertEquals(source.length() - 1, result.getEndQuoteIndex());
    }

    @Test
    public void transferredOffsetsMapPastEscapesBackToRawSource() {
        String source = "\\\"text\\\" + missing";
        Formatter.CodeTransferResult result = new FStringFormatter().codeTransferWithOffsets(source);
        assertEquals("\"text\" + missing", result.getText());
        int converted = result.getText().indexOf("missing");
        assertEquals(source.indexOf("missing"), result.rawStartOffset(converted));
        assertEquals(source.length(), result.rawEndOffset(converted + 7));
    }

    private static void assertRange(
            TemplateStringSplitter.Range range,
            int style,
            int start,
            int end
    ) {
        assertEquals(style, range.style);
        assertEquals(start, range.startIndex);
        assertEquals(end, range.endIndex);
    }
}
