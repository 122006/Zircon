package com.sun.tools.javac.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared lexical splitter for Zircon's interpolated string syntaxes.
 *
 * <p>This class deliberately has no javac or IDE dependencies. javac, the IDEA
 * plugin (through the base formatters), and the VS Code JDT agent all consume
 * the same ranges.</p>
 */
public final class TemplateStringSplitter {
    public static final int STRING = 0;
    public static final int CODE = 1;
    public static final int FORMAT = 2;

    public enum Syntax {
        DOLLAR,
        FORMAT,
        STR
    }

    private TemplateStringSplitter() {
    }

    public static Result split(String text, String prefix, Syntax syntax) {
        if (text == null || prefix == null || syntax == null) {
            return new Result(Collections.emptyList(), text == null ? "" : text, -1);
        }
        return syntax == Syntax.STR
                ? splitStr(text, prefix)
                : splitDollar(text, prefix, syntax == Syntax.FORMAT);
    }

    private static Result splitDollar(String text, String prefix, boolean formatted) {
        List<Range> ranges = new ArrayList<>();
        int start = prefix.length() + 1;
        int mode = -1;
        int parenthesisCount = 0;
        for (int cursor = start; cursor < text.length() - 1; cursor++) {
            char current = text.charAt(cursor);
            if (isSinglyEscaped(text, cursor)) {
                continue;
            }
            if (mode == 2) {
                if (current == '{') {
                    parenthesisCount++;
                }
                if (current == '}' && --parenthesisCount == 0) {
                    addCodeRange(ranges, text, start, cursor, formatted);
                    start = cursor + 1;
                    mode = -1;
                }
                continue;
            }
            if (mode == 1) {
                if (isShorthandCodeCharacter(current, parenthesisCount > 0)) {
                    continue;
                }
                if (current == '(' && text.substring(cursor).matches("^\\([^)]*\\).*$")) {
                    parenthesisCount++;
                    continue;
                }
                if (current == ')' && parenthesisCount > 0) {
                    if (text.substring(cursor).matches("^\\)\\.[A-Za-z_\\u4e00-\\u9fa5$]+.*")) {
                        parenthesisCount--;
                        continue;
                    }
                    ranges.add(new Range(CODE, start, cursor + 1));
                    start = cursor + 1;
                    mode = -1;
                    continue;
                }
                ranges.add(new Range(CODE, start, cursor));
                mode = -1;
                start = cursor;
                cursor--;
                continue;
            }
            if (current == '$'
                    && !isDoublyEscapedDollar(text, cursor)
                    && cursor + 1 < text.length()
                    && isInterpolationStart(text.charAt(cursor + 1))) {
                if (cursor != start) {
                    ranges.add(new Range(STRING, start, cursor));
                }
                if (text.charAt(cursor + 1) == '{') {
                    int codeStart = cursor + 2;
                    int codeEnd = findEmbeddedExpressionEnd(text, codeStart);
                    if (codeEnd < 0) {
                        start = codeStart;
                        mode = 2;
                        parenthesisCount = 0;
                    } else {
                        addCodeRange(ranges, text, codeStart, codeEnd, formatted);
                        start = codeEnd + 1;
                        cursor = codeEnd;
                        mode = -1;
                    }
                } else {
                    start = cursor + 1;
                    mode = 1;
                    parenthesisCount = 0;
                }
                continue;
            }
            if (mode == -1 && current == '"') {
                if (cursor > start) {
                    ranges.add(new Range(STRING, start, cursor));
                }
                return new Result(ranges, text.substring(0, cursor + 1), cursor);
            }
        }
        int fallbackEnd = Math.max(0, text.length() - 1);
        if (fallbackEnd > start) {
            if (mode > 0) {
                addCodeRange(ranges, text, start, fallbackEnd, formatted);
            } else {
                ranges.add(new Range(STRING, start, fallbackEnd));
            }
        }
        return new Result(ranges, text, fallbackEnd);
    }

    private static Result splitStr(String text, String prefix) {
        List<Range> ranges = new ArrayList<>();
        int start = prefix.length() + 1;
        int mode = -1;
        int braceCount = 0;
        for (int cursor = start; cursor < text.length() - 1; cursor++) {
            char current = text.charAt(cursor);
            if (current != '{' && isSinglyEscaped(text, cursor)) {
                continue;
            }
            if (mode == 2) {
                if (current == '{') {
                    braceCount++;
                }
                if (current == '}' && --braceCount == 0) {
                    if (cursor > start) {
                        ranges.add(new Range(CODE, start, cursor));
                    }
                    start = cursor + 1;
                    mode = -1;
                }
                continue;
            }
            if (current == '\\'
                    && !isDoublyEscapedDollar(text, cursor)
                    && cursor + 1 < text.length()
                    && text.charAt(cursor + 1) == '{') {
                if (cursor != start) {
                    ranges.add(new Range(STRING, start, cursor));
                }
                int codeStart = cursor + 2;
                int codeEnd = findEmbeddedExpressionEnd(text, codeStart);
                if (codeEnd < 0) {
                    start = codeStart;
                    mode = 2;
                    braceCount = 0;
                } else {
                    ranges.add(new Range(CODE, codeStart, codeEnd));
                    start = codeEnd + 1;
                    cursor = codeEnd;
                    mode = -1;
                }
                continue;
            }
            if (mode == -1 && current == '"') {
                if (cursor > start) {
                    ranges.add(new Range(STRING, start, cursor));
                }
                return new Result(ranges, text.substring(0, cursor + 1), cursor);
            }
        }
        int fallbackEnd = Math.max(0, text.length() - 1);
        if (fallbackEnd > start) {
            ranges.add(new Range(mode > 0 ? CODE : STRING, start, fallbackEnd));
        }
        return new Result(ranges, text, fallbackEnd);
    }

    private static void addCodeRange(List<Range> ranges, String text, int start, int end, boolean formatted) {
        if (end < start) {
            return;
        }
        if (end == start) {
            ranges.add(new Range(CODE, start, end));
            return;
        }
        String code = text.substring(start, end);
        if (formatted && code.startsWith("%")) {
            int separator = code.indexOf(':');
            if (separator >= 0) {
                ranges.add(new Range(FORMAT, start, start + separator));
                ranges.add(new Range(CODE, start + separator + 1, end));
                return;
            }
        }
        ranges.add(new Range(CODE, start, end));
    }

    private static boolean isInterpolationStart(char value) {
        return value == '{' || value == '$' || value == '_'
                || (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')
                || (value >= '\u4e00' && value <= '\u9fa5');
    }

    private static boolean isShorthandCodeCharacter(char value, boolean insideParentheses) {
        if (insideParentheses) {
            return value != ')';
        }
        return value == '.' || value == '$' || value == '_'
                || (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9') || (value >= '\u4e00' && value <= '\u9fa5');
    }

    private static boolean isSinglyEscaped(String text, int index) {
        return index >= 2 && text.charAt(index - 1) == '\\' && text.charAt(index - 2) != '\\';
    }

    private static boolean isDoublyEscapedDollar(String text, int index) {
        return index >= 2 && text.charAt(index - 1) == '\\' && text.charAt(index - 2) == '\\';
    }

    /**
     * Finds the matching interpolation brace while ignoring braces inside Java
     * literals and comments. Quotes in Zircon template expressions may be
     * written either directly or escaped for the surrounding template. The
     * latter are interpreted with the same two-step backslash reduction used
     * by the base formatter's {@code codeTransfer} method.
     */
    private static int findEmbeddedExpressionEnd(String text, int codeStart) {
        final int normal = 0;
        final int character = 1;
        final int string = 2;
        final int lineComment = 3;
        final int blockComment = 4;
        int state = normal;
        int braceDepth = 1;
        for (int index = codeStart; index < text.length(); index++) {
            char current = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
            if (state == lineComment) {
                if (current == '\r' || current == '\n') {
                    state = normal;
                }
                continue;
            }
            if (state == blockComment) {
                if (current == '*' && next == '/') {
                    state = normal;
                    index++;
                }
                continue;
            }
            if (state == character) {
                if (current == '\'' && isTransferredQuoteDelimiter(text, index)) {
                    state = normal;
                }
                continue;
            }
            if (state == string) {
                if (current == '"' && isTransferredQuoteDelimiter(text, index)) {
                    state = normal;
                }
                continue;
            }
            if (current == '/' && next == '/') {
                state = lineComment;
                index++;
            } else if (current == '/' && next == '*') {
                state = blockComment;
                index++;
            } else if (current == '\'' && isTransferredQuoteDelimiter(text, index)) {
                state = character;
            } else if (current == '"' && isTransferredQuoteDelimiter(text, index)) {
                state = string;
            } else if (current == '{') {
                braceDepth++;
            } else if (current == '}' && --braceDepth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isTransferredQuoteDelimiter(String text, int quoteIndex) {
        int backslashes = 0;
        for (int index = quoteIndex - 1; index >= 0 && text.charAt(index) == '\\'; index--) {
            backslashes++;
        }
        int afterOptionalEscapeRemoval = Math.max(0, backslashes - 1);
        int afterBackslashPairReduction = (afterOptionalEscapeRemoval + 1) / 2;
        return afterBackslashPairReduction % 2 == 0;
    }

    public static final class Range {
        public final int style;
        public final int startIndex;
        public final int endIndex;

        public Range(int style, int startIndex, int endIndex) {
            this.style = style;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    public static final class Result {
        public final List<Range> ranges;
        public final String originalString;
        public final int endQuoteIndex;

        Result(List<Range> ranges, String originalString, int endQuoteIndex) {
            this.ranges = Collections.unmodifiableList(new ArrayList<>(ranges));
            this.originalString = originalString;
            this.endQuoteIndex = endQuoteIndex;
        }
    }
}
