package com.sun.tools.javac.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class JStringFormatter implements Formatter {

    @Override
    public String prefix() {
        return "j";
    }

    @Override
    public String printOut(List<StringRange> build, String text) {
        StringBuilder stringBuilder = new StringBuilder();
        List<StringRange> stringRanges = new ArrayList<>();
        for (StringRange a : build) {
            if (stringRanges.isEmpty() || a.codeStyle == 1) {
                stringRanges.add(a.copy());
                continue;
            }
            final StringRange last = stringRanges.get(stringRanges.size() - 1);
            if (last.codeStyle != 1) {
                last.endIndex = a.endIndex;
                if (a.codeStyle == 0 && a.highlight == 1 && !a.stringVal.startsWith("\"")) {
                    last.stringVal = last.stringVal + "\\\"" + a.stringVal + "\\\"";
                } else if (a.codeStyle == 0 && a.highlight == 1 && a.stringVal.startsWith("\"")) {
                    last.stringVal = last.stringVal + a.stringVal.replace("\"", "\\\"");
                } else if (a.codeStyle == 0 && a.highlight == 2) {
                    last.stringVal = last.stringVal + a.stringVal.replace("\"", "\\\"");
                } else {
                    last.stringVal = last.stringVal + a.stringVal;
                }
                last.codeStyle = 0;
            } else {
                stringRanges.add(a.copy());
            }
        }
        build = stringRanges;
        if (build.size() > 0) {
            stringBuilder.append("(");
            for (int i = 0; i < build.size(); i++) {
                StringRange stringRange = build.get(i);
                if (stringRange.codeStyle == 1) {
                    if (i == 0) {
                        stringBuilder.append("String.valueOf");
                    } else {
                        stringBuilder.append("+");
                    }
                    stringBuilder.append("(");
                    stringBuilder.append(stringRange.stringVal);
                    stringBuilder.append(")");
                } else if (stringRange.codeStyle == 0) {
                    if (i > 0)
                        stringBuilder.append("+");
                    stringBuilder.append("\"");
                    stringBuilder.append(stringRange.stringVal);
                    stringBuilder.append("\"");
                }
            }
            stringBuilder.append(")");
        }
        return stringBuilder.toString();
    }


    @Override
    public List<Item> stringRange2Group(JavaTokenizer javaTokenizer, char[] buf, List<StringRange> build, String text, int groupStartIndex) throws Exception {
        List<Item> items = new ArrayList<>();
        if (build.isEmpty()) {
            items.add(Item.loadStringToken(0, 0, ""));
            return Item.withSourcePositions(items, groupStartIndex);
        }
        int prefixLength = prefix().length();
        items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, 0, 0));
        List<StringRange> stringRanges = new ArrayList<>();
        for (StringRange a : build) {
            if (stringRanges.isEmpty() || a.codeStyle == 1) {
                stringRanges.add(a.copy());
                continue;
            }
            final StringRange last = stringRanges.get(stringRanges.size() - 1);
            if (last.codeStyle != 1) {
                last.endIndex = a.endIndex;
                if (a.codeStyle == 0 && a.highlight == 1 && !a.stringVal.startsWith("\"")) {
                    last.stringVal = last.stringVal + "\\\"" + a.stringVal + "\\\"";
                } else if (a.codeStyle == 0 && a.highlight == 1 && a.stringVal.startsWith("\"")) {
                    last.stringVal = last.stringVal + a.stringVal.replace("\"", "\\\"");
                } else if (a.codeStyle == 0 && a.highlight == 2) {
                    last.stringVal = last.stringVal + a.stringVal.replace("\"", "\\\"");
                } else {
                    last.stringVal = last.stringVal + a.stringVal;
                }
                last.codeStyle = 0;
            } else {
                stringRanges.add(a.copy());
            }
        }
        build = stringRanges;
        if (build.size() > 0) {
            for (int i = 0; i < build.size(); i++) {
                StringRange stringRange = build.get(i);
                int startIndex = stringRange.startIndex;
                int endIndex = stringRange.endIndex;
                if (stringRange.codeStyle == 1) {
                    if (i != 0) {
                        items.add(Item.loadCommaToken(Tokens.TokenKind.PLUS, startIndex, startIndex));
                    }
                    items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "zircon"));
                    items.add(Item.loadCommaToken(Tokens.TokenKind.DOT, prefixLength, prefixLength));
                    items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "BiOp"));
                    items.add(Item.loadCommaToken(Tokens.TokenKind.DOT, prefixLength, prefixLength));
                    items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "jString"));
                    items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, startIndex - 1, startIndex - 1));
                    Formatter.CodeTransferResult transfer = transferCode(buf, groupStartIndex, text, startIndex, endIndex);
                    items.add(Item.loadJavacCode(startIndex, endIndex, transfer));
                    items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, endIndex, endIndex));
                } else if (stringRange.codeStyle == 0) {
                    if (i > 0) {
                        items.add(Item.loadCommaToken(Tokens.TokenKind.PLUS, startIndex, startIndex));
                    }
                    items.add(Item.loadStringToken(startIndex, startIndex, stringRange.stringVal));
                }
            }
        }
        items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, text.length() - 1, text.length() - 1));
        return Item.withSourcePositions(items, groupStartIndex);
    }

    @Override
    public ZrStringModel build(String text) {
        return buildJson(this, text);
    }

    private static ZrStringModel buildJson(Formatter formatter, String text) {
        ZrStringModel model = new ZrStringModel();
        model.setFormatter(formatter);
        if (text == null || text.isEmpty()) {
            model.setOriginalString("");
            return model;
        }
        int end;
        try {
            end = scanJson(formatter, text, model.getList());
        } catch (TemplateSyntaxException failure) {
            model.addDiagnostics(Collections.singletonList(failure.diagnostic));
            int quote = text.lastIndexOf('"');
            int recoveryEnd = quote > formatter.prefix().length() ? quote + 1
                    : text.indexOf(';', formatter.prefix().length() + 1);
            if (recoveryEnd < 0) recoveryEnd = text.length();
            end = recoveryEnd - 1;
        }
        model.setEndQuoteIndex(end);
        model.setOriginalString(text.substring(0, end + 1));
        return model;
    }

    @Override
    public String stringTransfer(String str) {
        return str.replace("%", "%%").replace("\\$", "$");
    }

    /** Keeps partial ranges available to IDE callers, including incomplete input. */
    public static StringRange[] parseJson(Formatter formatter, String text) {
        return buildJson(formatter, text).getList().toArray(new StringRange[0]);
    }

    private static int scanJson(Formatter formatter, String text, List<StringRange> ranges) {
        int start = formatter.prefix().length() + 1;
        int limit = text.lastIndexOf('"');
        if (limit < start) limit = text.length();
        int cursor = start;
        while (cursor < limit && Character.isWhitespace(text.charAt(cursor))) cursor++;
        if (cursor >= limit || text.charAt(cursor) != '{' && text.charAt(cursor) != '[') {
            throw jsonError(cursor, "JSON 模板必须以对象 { 或数组 [ 开始", "使用 j\"{key:value}\" 或 j\"[value]\"。");
        }
        java.util.ArrayDeque<Integer> structures = new java.util.ArrayDeque<>();
        while (cursor < limit) {
            char current = text.charAt(cursor);
            if (Character.isWhitespace(current)) {
                cursor++;
                continue;
            }
            if (current == '{' || current == '[') {
                structures.push(cursor);
                ranges.add(StringRange.of(0, formatter, text, cursor, ++cursor));
                continue;
            }
            if (current == '}' || current == ']') {
                if (structures.isEmpty()) {
                    throw jsonError(cursor, "JSON 结构出现多余的结束符 " + current, "移除多余的括号。");
                }
                char expected = closing(text.charAt(structures.peek()));
                if (current != expected) {
                    throw jsonError(cursor, "JSON 结构中的 " + current + " 与起始括号不匹配",
                            "此处应使用 " + expected + "。");
                }
                structures.pop();
                ranges.add(StringRange.of(0, formatter, text, cursor, ++cursor));
                if (structures.isEmpty()) {
                    int trailingStart = cursor;
                    while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
                    if (cursor >= text.length() || text.charAt(cursor) != '"') {
                        throw new TemplateSyntaxException(new TemplateStringSplitter.Diagnostic(
                                "ZR1001", 0, start, "插值字符串的引号未闭合", "在 JSON 结构后补上结束引号。"));
                    }
                    // Preserve the established trailing whitespace inside j strings.
                    ranges.add(StringRange.of(-1, formatter, text, trailingStart, cursor));
                    return cursor;
                }
                continue;
            }
            if (current == ',' || current == ':') {
                ranges.add(StringRange.of(0, formatter, text, cursor, ++cursor));
                continue;
            }
            boolean key = text.charAt(structures.peek()) == '{'
                    && !Objects.equals(ranges.get(ranges.size() - 1).stringVal, ":");
            int rangeStart = cursor;
            if (key) {
                if (current == '"' || current == '\\' && cursor + 1 < limit && text.charAt(cursor + 1) == '"') {
                    if (current == '\\') cursor++;
                    cursor = afterQuoted(text, cursor, limit);
                } else {
                    while (cursor < limit && !Character.isWhitespace(text.charAt(cursor))
                            && ":,{}[]".indexOf(text.charAt(cursor)) < 0) cursor++;
                }
                if (cursor == rangeStart) throw jsonError(cursor, "JSON 键名缺失", "在 : 前填写键名。");
                StringRange range = StringRange.of(0, formatter, text, rangeStart, cursor);
                range.highlight = 1;
                ranges.add(range);
            } else {
                cursor = afterValue(text, cursor, limit);
                int rangeEnd = cursor;
                while (rangeEnd > rangeStart && Character.isWhitespace(text.charAt(rangeEnd - 1))) rangeEnd--;
                if (rangeEnd == rangeStart) throw jsonError(cursor, "JSON 值缺失", "填写值或 Java 表达式。");
                StringRange range = StringRange.of(1, formatter, text, rangeStart, rangeEnd);
                range.highlight = 2;
                if (range.stringVal.trim().matches("^(?:\"(?:[^\"\\\\]|\\\\.)*\"|true|false|null|-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)$")) {
                    range.codeStyle = 0;
                }
                ranges.add(range);
                ranges.add(StringRange.of(-1, "", rangeEnd, cursor));
            }
        }
        int opening = structures.peek();
        char expected = closing(text.charAt(opening));
        throw jsonError(opening, "JSON 结构缺少结束符 " + expected, "在对象或数组末尾补上 " + expected + "。");
    }

    private static int afterValue(String text, int cursor, int limit) {
        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
        while (cursor < limit) {
            char current = text.charAt(cursor);
            if ((current == '"' || current == '\'') && TemplateStringSplitter.isTransferredQuoteDelimiter(text, cursor)) {
                cursor = afterQuoted(text, cursor, limit);
                continue;
            }
            if (current == '/' && cursor + 1 < limit) {
                if (text.charAt(cursor + 1) == '*') {
                    int close = text.indexOf("*/", cursor + 2);
                    cursor = close < 0 ? limit : Math.min(limit, close + 2);
                    continue;
                }
                if (text.charAt(cursor + 1) == '/') return limit;
            }
            if (current == '{' || current == '[' || current == '(') stack.push(current);
            else if (current == '}' || current == ']' || current == ')') {
                if (stack.isEmpty()) return cursor;
                char expected = closing(stack.pop());
                if (current != expected) {
                    throw jsonError(cursor, "JSON 值中的括号不匹配", "此处应使用 " + expected + "。");
                }
            } else if (current == ',' && stack.isEmpty()) return cursor;
            cursor++;
        }
        return cursor;
    }

    private static int afterQuoted(String text, int opening, int limit) {
        char quote = text.charAt(opening);
        for (int cursor = opening + 1; cursor < limit; cursor++) {
            if (text.charAt(cursor) == quote && TemplateStringSplitter.isTransferredQuoteDelimiter(text, cursor)) {
                return cursor + 1;
            }
        }
        throw jsonError(opening, "JSON 值或键名的引号未闭合", "补上对应的结束引号。");
    }

    private static char closing(char opening) {
        return opening == '{' ? '}' : opening == '[' ? ']' : ')';
    }

    private static TemplateSyntaxException jsonError(int position, String message, String hint) {
        return new TemplateSyntaxException(new TemplateStringSplitter.Diagnostic(
                "ZR1010", position, position + 1, message, hint));
    }
}
