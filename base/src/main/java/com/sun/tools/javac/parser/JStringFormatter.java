package com.sun.tools.javac.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JStringFormatter implements Formatter {

    @Override
    public String prefix() {
        return "j";
    }

    @Override
    public String printOut(List<StringRange> build, String text) {
        StringBuilder stringBuilder = new StringBuilder();
        if (build.size() > 0) {
            stringBuilder.append( "(");
            for (int i = 0; i < build.size(); i++) {
                StringRange stringRange = build.get(i);
                if (stringRange.codeStyle == 1) {
                    if (i == 0) {
                        stringBuilder.append( "String.valueOf");
                    } else {
                        stringBuilder.append( "+");
                    }
                    stringBuilder.append( "(");
                    stringBuilder.append(stringRange.stringVal);
                    stringBuilder.append( ")");
                } else if (stringRange.codeStyle == 0) {
                    if (i > 0)
                        stringBuilder.append( "+");
                    stringBuilder.append( "\"");
                    stringBuilder.append(stringRange.stringVal);
                    stringBuilder.append( "\"");
                } else {
//                    if (i > 0)
//                        stringBuilder.append( "+");
//                    stringBuilder.append( "\"");
//                    stringBuilder.append(stringRange.stringVal);
//                    stringBuilder.append( "\"");
                }
            }
            stringBuilder.append( ")");
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
                stringRanges.add(a);
                continue;
            }
            final StringRange last = stringRanges.get(stringRanges.size() - 1);
            if (last.codeStyle != 1) {
                last.codeStyle = 0;
                last.endIndex = a.endIndex;
                last.stringVal = text.substring(last.startIndex, last.endIndex);
            } else {
                stringRanges.add(a);
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
                } else {
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
        return str.replace( "%" , "%%").replace( "\\$" , "$");
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
        int len = jsonStr.lastIndexOf( "\"");
        int i = 2;
        char[] chars = jsonStr.toCharArray();
        java.util.Stack<Character> structureStack = new java.util.Stack<>();
        outer:
        while (i < len) {
            char c = chars[i];
            switch (c) {
                case '{':
                case '[':
                    ranges.add(StringRange.of(2, i, i + 1));
                    structureStack.push(c);
                    break;
                case ']':
                case '}':
                    ranges.add(StringRange.of(2, i, i + 1));
                    structureStack.pop();
                    if (structureStack.isEmpty()) {
                        int start = i;
                        while (i < len) {
                            if (chars[i] == '"') break;
                            i++;
                        }
                        ranges.add(StringRange.of(0, start, i));
                        break outer;
                    }
                    break;
                case ',':
                    ranges.add(StringRange.of(2, i, i + 1));
                    break;
                case ':':
                    ranges.add(StringRange.of(2, i, i + 1));
                    break;
                default:
                    int start = i;
                    boolean scanCode = false;
                    if (structureStack.peek() == '{') {
                        if (chars[i - 1] == ':') {
                            scanCode = true;
                        } else {
                            while (i < len) {
                                if (chars[i] == ':' || chars[i] == ',') break;
                                i++;
                            }
                            ranges.add(StringRange.string(formatter, jsonStr, start, i));
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
                        ranges.add(StringRange.code(formatter, jsonStr, start, i));
                        i--;
                    }
                    break;
            }

            i++;
        }
        for (StringRange stringRange : ranges) {
            if (stringRange.stringVal == null) {
                stringRange.stringVal = jsonStr.substring(stringRange.startIndex, stringRange.endIndex);
            }
            if (stringRange.codeStyle == 2) stringRange.codeStyle = 0;
            else if (stringRange.codeStyle == 0) stringRange.codeStyle = 2;
            else if (stringRange.codeStyle == 1) {
                if (stringRange.stringVal.matches( "^(?:\"(?:[^\"\\\\]|\\\\.)*\"|true|false|null|-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)$")) {
                    stringRange.codeStyle = 0;
                }
            }
        }
        return ranges.toArray(new StringRange[0]);
    }
}
