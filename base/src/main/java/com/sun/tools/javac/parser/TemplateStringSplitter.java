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
        List<Diagnostic> diagnostics = new ArrayList<>();
        int start = prefix.length() + 1;
        int mode = -1;
        int parenthesisCount = 0;
        for (int cursor = start; cursor < text.length(); cursor++) {
            char current = text.charAt(cursor);
            if (isSinglyEscaped(text, cursor)) {
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
                        diagnostics.add(new Diagnostic("ZR1002", cursor, cursor + 2,
                                "插值表达式缺少结束符 }", "在表达式末尾补上 }。"));
                        return incomplete(ranges, diagnostics, text, prefix, codeStart, CODE);
                    } else {
                        addCodeRange(ranges, text, codeStart, codeEnd, formatted, diagnostics);
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
                return new Result(ranges, text.substring(0, cursor + 1), cursor, diagnostics);
            }
        }
        diagnostics.add(new Diagnostic("ZR1001", 0, prefix.length() + 1,
                "插值字符串的引号未闭合", "在当前行内补上结束引号。"));
        return incomplete(ranges, diagnostics, text, prefix, start, mode > 0 ? CODE : STRING);
    }

    private static Result splitStr(String text, String prefix) {
        List<Range> ranges = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        int start = prefix.length() + 1;
        for (int cursor = start; cursor < text.length(); cursor++) {
            char current = text.charAt(cursor);
            if (current != '{' && isSinglyEscaped(text, cursor)) {
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
                    diagnostics.add(new Diagnostic("ZR1002", cursor, cursor + 2,
                            "插值表达式缺少结束符 }", "在表达式末尾补上 }。"));
                    return incomplete(ranges, diagnostics, text, prefix, codeStart, CODE);
                } else {
                    addCodeRange(ranges, text, codeStart, codeEnd, false, diagnostics);
                    start = codeEnd + 1;
                    cursor = codeEnd;
                }
                continue;
            }
            if (current == '"') {
                if (cursor > start) {
                    ranges.add(new Range(STRING, start, cursor));
                }
                return new Result(ranges, text.substring(0, cursor + 1), cursor, diagnostics);
            }
        }
        diagnostics.add(new Diagnostic("ZR1001", 0, prefix.length() + 1,
                "插值字符串的引号未闭合", "在当前行内补上结束引号。"));
        return incomplete(ranges, diagnostics, text, prefix, start, STRING);
    }

    private static Result incomplete(List<Range> ranges, List<Diagnostic> diagnostics, String text,
                                     String prefix, int rangeStart, int style) {
        int quote = text.lastIndexOf('"');
        int recoveryEnd = quote > prefix.length() ? quote + 1 : text.indexOf(';', prefix.length() + 1);
        if (recoveryEnd < 0) recoveryEnd = text.length();
        int rangeEnd = quote > prefix.length() ? recoveryEnd - 1 : recoveryEnd;
        if (rangeStart >= 0 && rangeStart < rangeEnd) {
            ranges.add(new Range(style, rangeStart, rangeEnd));
        }
        return new Result(ranges, text.substring(0, recoveryEnd), recoveryEnd - 1, diagnostics);
    }

    private static void addCodeRange(List<Range> ranges, String text, int start, int end, boolean formatted,
                                     List<Diagnostic> diagnostics) {
        if (end < start) {
            return;
        }
        if (end == start) {
            ranges.add(new Range(CODE, start, end));
            return;
        }
        int formatStart = start;
        while (formatStart < end && Character.isWhitespace(text.charAt(formatStart))) formatStart++;
        if (formatStart < end && text.charAt(formatStart) == '%') {
            int separator = text.indexOf(':', formatStart);
            if (separator >= end) separator = -1;
            if (!formatted) {
                diagnostics.add(new Diagnostic("ZR1003", formatStart, separator < 0 ? end : separator,
                        "当前插值前缀不支持格式化说明符", "请改用 f 前缀，例如 f\"${%03d:value}\"。"));
            } else if (separator < 0) {
                diagnostics.add(new Diagnostic("ZR1004", formatStart, end,
                        "格式化说明符后缺少分隔符 :", "使用 ${格式说明符:表达式}，例如 ${%03d:value}。"));
            } else {
                String format = text.substring(formatStart, separator);
                if (!validFormat(format)) {
                    diagnostics.add(new Diagnostic("ZR1006", formatStart, separator,
                            "无效的格式说明符 " + format, "检查格式转换符、宽度和精度，例如 %03d 或 %.2f。"));
                }
                if (text.substring(separator + 1, end).trim().isEmpty()) {
                    diagnostics.add(new Diagnostic("ZR1005", separator + 1, Math.min(text.length(), end + 1),
                            "格式化说明符后缺少表达式", "在 : 后填写表达式；空插值请使用 ${}。"));
                }
                ranges.add(new Range(FORMAT, formatStart, separator));
                ranges.add(new Range(CODE, separator + 1, end));
                return;
            }
        }
        ranges.add(new Range(CODE, start, end));
    }

    private static boolean validFormat(String format) {
        // A field may contain literal suffixes and repeated/reused conversions.
        java.util.regex.Pattern specifier = java.util.regex.Pattern.compile(
                "%(?:[1-9][0-9]*\\$)?[-#+ 0,(<]*[0-9]*(?:\\.[0-9]+)?(?:[tT][HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc]|[bBhHsScCdoxXeEfgGaA%n])");
        for (int index = 0; index < format.length(); index++) {
            if (format.charAt(index) != '%') continue;
            java.util.regex.Matcher matcher = specifier.matcher(format).region(index, format.length());
            if (!matcher.lookingAt() || !validSpecifier(matcher.group())) return false;
            index = matcher.end() - 1;
        }
        return true;
    }

    private static boolean validSpecifier(String format) {
        // Validate syntax only, without evaluating an expression or allocating
        // potentially enormous padding for non-consuming conversions.
        if (format.endsWith("n") || format.endsWith("%")) {
            int argumentIndex = format.indexOf('$');
            if (argumentIndex >= 0) {
                try {
                    Integer.parseInt(format.substring(1, argumentIndex));
                } catch (NumberFormatException invalid) {
                    return false;
                }
                format = "%" + format.substring(argumentIndex + 1);
            }
        }
        if (format.endsWith("n")) return format.equals("%n");
        if (format.endsWith("%")) {
            if (format.equals("%%")) return true;
            if (!format.matches("%-?[1-9][0-9]*%")) return false;
            try {
                Integer.parseInt(format.substring(format.charAt(1) == '-' ? 2 : 1, format.length() - 1));
                return true;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
        try (java.util.Formatter validator = new java.util.Formatter(java.util.Locale.ROOT)) {
            validator.format(format);
            return true;
        } catch (java.util.MissingFormatArgumentException expected) {
            return true;
        } catch (java.util.IllegalFormatException invalid) {
            return false;
        }
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

    static boolean isTransferredQuoteDelimiter(String text, int quoteIndex) {
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
        public final List<Diagnostic> diagnostics;

        Result(List<Range> ranges, String originalString, int endQuoteIndex) {
            this(ranges, originalString, endQuoteIndex, Collections.emptyList());
        }

        Result(List<Range> ranges, String originalString, int endQuoteIndex, List<Diagnostic> diagnostics) {
            this.ranges = Collections.unmodifiableList(new ArrayList<>(ranges));
            this.originalString = originalString;
            this.endQuoteIndex = endQuoteIndex;
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
        }
    }

    /** A source-relative diagnostic; safe for IDEs parsing incomplete input. */
    public static final class Diagnostic {
        public final String code;
        public final int start;
        public final int end;
        public final String message;
        public final String hint;

        public Diagnostic(String code, int start, int end, String message, String hint) {
            this.code = code;
            this.start = Math.max(0, start);
            this.end = Math.max(this.start, end);
            this.message = message;
            this.hint = hint;
        }

        public String displayMessage() {
            return "[" + code + "] " + message + (hint.isEmpty() ? "" : "\n建议：" + hint);
        }
    }
}
