package com.sun.tools.javac.parser;

import java.util.ArrayList;
import java.util.List;

public class SStringFormatter implements Formatter {

    @Override
    public String prefix() {
        return "$";
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
                    throw new Error("\"[error(使用了"+prefix()+"字符串语法不支持格式化字符串功能，请使用f前缀字符串)]\\n原始字符串：\" + text");
                }
            }
        }
        items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, text.length(), text.length()));
        return items;
    }

    @Override
    public String printOut(List<StringRange> build, String text) {
        StringBuilder stringBuilder = new StringBuilder();
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
                } else {
                    System.err.println("[error(使用了"+prefix()+"字符串语法不支持格式化字符串功能，请使用f前缀字符串)]");
                }
            }
            stringBuilder.append(")");
        }
        return stringBuilder.toString();
    }

    @Override
    public ZrStringModel build(String text) {
        return Formatter.buildFromSharedSplitter(this, text, TemplateStringSplitter.Syntax.DOLLAR);
    }

    @Override
    public String stringTransfer(String str) {
        return str.replace("\\$", "$");
    }
}
