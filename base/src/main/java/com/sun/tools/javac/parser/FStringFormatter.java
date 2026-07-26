package com.sun.tools.javac.parser;

import java.util.ArrayList;
import java.util.List;

public class FStringFormatter implements Formatter {

    @Override
    public String prefix() {
        return "f";
    }

    @Override
    public String printOut(List<StringRange> build, String text) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("String.format(\"");
        stringBuilder.append(map2FormatString(text, build));
        stringBuilder.append("\"");
        for (StringRange a : build) {
            if (a.codeStyle != 0 && a.codeStyle != 1) continue;
            if (a.codeStyle == 1) {
                stringBuilder.append(",");
                String toStr = a.stringVal;
                stringBuilder.append(toStr);
            }
        }
        stringBuilder.append(")");
        return stringBuilder.toString();
    }

    public static String map2FormatString(String text, List<StringRange> ranges) {
        StringRange formatRange = null;
        StringBuilder stringBuilder = new StringBuilder();
        for (StringRange range : ranges) {
            if (range.codeStyle == 2) formatRange = range;
            else if (range.codeStyle == 0) {
                stringBuilder.append(range.stringVal);
            } else if (range.codeStyle == 1) {
                if (formatRange == null) {
                    stringBuilder.append("%s");
                } else {
                    stringBuilder.append(text, formatRange.startIndex, formatRange.endIndex);
                    formatRange = null;
                }
            }
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

        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "String"));
        items.add(Item.loadCommaToken(Tokens.TokenKind.DOT, prefixLength, prefixLength));
        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "format"));
        items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, prefixLength, prefixLength));

        items.add(Item.loadStringToken(prefixLength, prefixLength, map2FormatString(text, build)));
        for (int i = 0; i < build.size(); i++) {
            StringRange a = build.get(i);
            int startIndex = a.startIndex;
            int endIndex = a.endIndex;
            if (a.codeStyle == 1) {
                items.add(Item.loadCommaToken(Tokens.TokenKind.COMMA, endIndex, endIndex));
                codeTransfer(buf, groupStartIndex, text, startIndex, endIndex);
                items.add(Item.loadJavacCode(startIndex, endIndex));
            }
        }
        items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, text.length(), text.length()));
        return items;
    }

    @Override
    public ZrStringModel build(String text) {
        return Formatter.buildFromSharedSplitter(this, text, TemplateStringSplitter.Syntax.FORMAT);
    }

    @Override
    public String stringTransfer(String str) {
        return str.replace("%", "%%").replace("\\$", "$");
    }

}
