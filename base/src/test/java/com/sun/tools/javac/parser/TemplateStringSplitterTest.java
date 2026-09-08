package com.sun.tools.javac.parser;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

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
