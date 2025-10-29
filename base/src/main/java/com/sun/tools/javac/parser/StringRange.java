package com.sun.tools.javac.parser;

public class StringRange {
    public static StringRange of(int codeStyle, int startIndex, int endIndex) {
        StringRange stringRange = new StringRange();
        stringRange.endIndex = endIndex;
        stringRange.codeStyle = codeStyle;
        stringRange.startIndex = startIndex;
        return stringRange;
    }

    public static StringRange of(int codeStyle, String stringVal, int startIndex, int endIndex) {
        StringRange stringRange = new StringRange();
        stringRange.endIndex = endIndex;
        stringRange.codeStyle = codeStyle;
        stringRange.stringVal = stringVal;
        stringRange.startIndex = startIndex;
        return stringRange;
    }

    public static StringRange of(int codeStyle, Formatter formatter, String text, int startIndex, int endIndex) {
        StringRange stringRange = new StringRange();
        stringRange.endIndex = endIndex;
        stringRange.codeStyle = codeStyle;
        stringRange.stringVal = formatter.codeTransfer(text.substring(startIndex, endIndex));
        stringRange.startIndex = startIndex;
        return stringRange;
    }

    public static StringRange code(Formatter formatter, String text, int startIndex, int endIndex) {
        StringRange stringRange = new StringRange();
        stringRange.endIndex = endIndex;
        stringRange.codeStyle = 1;
        stringRange.stringVal = formatter.codeTransfer(text.substring(startIndex, endIndex));
        stringRange.startIndex = startIndex;
        return stringRange;
    }

    public static StringRange string(Formatter formatter, String text, int startIndex, int endIndex) {
        StringRange stringRange = new StringRange();
        stringRange.endIndex = endIndex;
        stringRange.codeStyle = 0;
        stringRange.stringVal = formatter.stringTransfer(text.substring(startIndex, endIndex));
        stringRange.startIndex = startIndex;
        return stringRange;
    }

    /**
     * -1 ignore space <br>
     * 0 hard String  <br>
     * 1 java code<br>
     * 2 format info
     */
    public int codeStyle = -1;
    public int startIndex;
    public int endIndex;
    public int highlight = 0;

    public String stringVal = null;


    public StringRange copy() {
        StringRange stringRange = new StringRange();
        stringRange.endIndex = endIndex;
        stringRange.codeStyle = codeStyle;
        stringRange.stringVal = stringVal;
        stringRange.startIndex = startIndex;
        stringRange.highlight = highlight;
        return stringRange;
    }


}
