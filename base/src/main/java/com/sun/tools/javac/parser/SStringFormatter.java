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
            return Item.withSourcePositions(items, groupStartIndex);
        }
        int prefixLength = prefix().length();
        // The outer expression belongs to the prefix; each embedded expression
        // gets its own opening delimiter, outside the embedded Java code.
        items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, 0, 0));
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
                    items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, startIndex - 1, startIndex - 1));
                    Formatter.CodeTransferResult transfer = transferCode(buf, groupStartIndex, text, startIndex, endIndex);
                    items.add(Item.loadJavacCode(startIndex, endIndex, transfer));
                    items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, endIndex, endIndex));
                } else if (stringRange.codeStyle == 0) {
                    if (i > 0) {
                        items.add(Item.loadCommaToken(Tokens.TokenKind.PLUS, startIndex, startIndex));
                    }
                    items.add(Item.loadStringToken(startIndex, startIndex, stringRange.stringVal));
                } else {
                    throw new TemplateSyntaxException(new TemplateStringSplitter.Diagnostic(
                            "ZR1003", startIndex, endIndex,
                            "当前插值前缀不支持格式化说明符",
                            "请改用 f 前缀，例如 f\"${%03d:value}\"。"));
                }
            }
        }
        items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, text.length() - 1, text.length() - 1));
        return Item.withSourcePositions(items, groupStartIndex);
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
                    // Keep IDE preview tolerant while the compiler reports the
                    // structured diagnostic through ZrStringModel.
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
