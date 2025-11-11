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
            return items;
        }
        int prefixLength = prefix().length();
        items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, prefixLength, prefixLength));
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
                    if (i == 0) {
                        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "String"));
                        items.add(Item.loadCommaToken(Tokens.TokenKind.DOT, prefixLength, prefixLength));
                        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "valueOf"));
                    } else {
                        items.add(Item.loadCommaToken(Tokens.TokenKind.PLUS, startIndex, startIndex));
                    }
                    items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, prefixLength, prefixLength));
                    codeTransfer(buf, groupStartIndex, text, startIndex, endIndex);
                    items.add(Item.loadJavacCode(startIndex, endIndex));
                    items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, prefixLength, prefixLength));
                } else if (stringRange.codeStyle == 0) {
                    if (i > 0) {
                        items.add(Item.loadCommaToken(Tokens.TokenKind.PLUS, startIndex, startIndex));
                    }
                    items.add(Item.loadStringToken(startIndex, startIndex, stringRange.stringVal));
                }
            }
        }
        items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, text.length(), text.length()));
        return items;
    }

    @Override
    public ZrStringModel build(String text) {
        ZrStringModel model = new ZrStringModel();
        model.setFormatter(this);
        final StringRange[] elements = parseJson(this, text);
        Collections.addAll(model.getList(), elements);
        model.setOriginalString(text);
        model.setEndQuoteIndex(elements[elements.length - 1].endIndex);
        return model;
    }


    @Override
    public String stringTransfer(String str) {
        return str.replace("%", "%%").replace("\\$", "$");
    }


    /**
     * 解析 JSON 字符串为 StringRange 数组
     *
     * @param jsonStr 输入的 JSON 字符串
     * @return StringRange 数组，表示 JSON 的各个 token
     */
    public static StringRange[] parseJson(Formatter formatter, String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return new StringRange[0];
        }

        List<StringRange> ranges = new ArrayList<>();
        int len = jsonStr.lastIndexOf("\"");
        int i = 2;
        char[] chars = jsonStr.toCharArray();
        java.util.Stack<Character> structureStack = new java.util.Stack<>();
        outer:
        while (i < len) {
            char c = chars[i];
            switch (c) {
                case ' ':
                case '\t':
                case '\n':
                case '\r':
                case '\f':
                    break;
                case '{':
                case '[':
                    ranges.add(StringRange.of(0, formatter, jsonStr, i, i + 1));
                    structureStack.push(c);
                    break;
                case ']':
                case '}':
                    ranges.add(StringRange.of(0, formatter, jsonStr, i, i + 1));
                    structureStack.pop();
                    if (structureStack.isEmpty()) {
                        int start = i;
                        while (i < len) {
                            if (chars[i] == '"') break;
                            i++;
                        }
                        ranges.add(StringRange.of(-1, formatter, jsonStr, start + 1, i));
                        break outer;
                    }
                    break;
                case ',':
                    ranges.add(StringRange.of(0, formatter, jsonStr, i, i + 1));
                    break;
                case ':':
                    ranges.add(StringRange.of(0, formatter, jsonStr, i, i + 1));
                    break;
                default:
                    int start = i;
                    boolean scanCode = false;
                    if (structureStack.peek() == '{') {
                        if (Objects.equals(ranges.get(ranges.size() - 1).stringVal, ":")) {
                            scanCode = true;
                        } else {
                            in:
                            while (i < len) {
                                switch (chars[i]) {
                                    case ' ':
                                    case '\t':
                                    case '\n':
                                    case '\r':
                                    case '\f':
                                    case ':':
                                    case ',':
                                        break in;
                                }
                                i++;
                            }
                            final StringRange e = StringRange.of(0, formatter, jsonStr, start, i);
                            e.highlight = 1;
                            ranges.add(e);
                            i--;
                            break;
                        }
                    } else {
                        scanCode = true;
                    }
                    if (scanCode) {
                        java.util.Stack<Character> valueStack = new java.util.Stack<>();

                        ScanValue:
                        while (i < len) {
                            char _c = chars[i];
                            switch (_c) {
                                case '\'':
                                    while (i < len) {
                                        char _c2 = chars[i];
                                        if (_c2 == '\'' && chars[i - 1] != '\\') {
                                            break;
                                        }
                                        i++;
                                    }
                                    break;
                                case '"':
                                    i++;
                                    while (i < len) {
                                        char _c2 = chars[i];
                                        if (_c2 == '"' && chars[i - 1] != '\\') {
                                            break;
                                        }
                                        i++;
                                    }
                                    break;
                                case '{':
                                case '[':
                                case '(':
                                    valueStack.push(_c);
                                    break;
                                case ']':
                                case '}':
                                case ')':
                                    if (valueStack.isEmpty()) {
                                        break ScanValue;
                                    }
                                    valueStack.pop();
                                    break;
                                case ',': {
                                    if (valueStack.isEmpty()) {
                                        break ScanValue;
                                    }
                                }
                            }
                            i++;
                        }
                        int iSpace = i - 1;
                        in2:
                        while (iSpace > 0) {
                            switch (chars[iSpace]) {
                                case ' ':
                                case '\t':
                                case '\n':
                                case '\r':
                                case '\f':
                                case ':':
                                case ',':
                                    iSpace--;
                                default:
                                    break in2;
                            }
                        }
                        final StringRange e = StringRange.of(1, formatter, jsonStr, start, iSpace + 1);
                        e.highlight = 2;
                        ranges.add(e);
                        ranges.add(StringRange.of(-1, "", iSpace + 1, i));
                        i--;
                    }
                    break;
            }

            i++;
        }
        for (StringRange stringRange : ranges) {
            if (stringRange.codeStyle == 1) {
                if (stringRange.stringVal.trim().matches("^(?:\"(?:[^\"\\\\]|\\\\.)*\"|true|false|null|-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)$")) {
                    stringRange.codeStyle = 0;
                }
            }
        }
        return ranges.toArray(new StringRange[0]);
    }
}
