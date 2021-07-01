package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Log;
import formatter.*;

import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;

public class ZrJavaTokenizer extends JavaTokenizer {
    public static boolean debug = "true".equalsIgnoreCase(System.getenv("Debug"));

    protected ZrJavaTokenizer(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        super(scannerFactory, charBuffer);
    }

    protected ZrJavaTokenizer(ScannerFactory scannerFactory, char[] chars, int i) {
        super(scannerFactory, chars, i);
    }

    protected ZrJavaTokenizer(ScannerFactory scannerFactory, UnicodeReader unicodeReader) {
        super(scannerFactory, unicodeReader);
    }

    public void log(String str) {
        if (!debug) return;
        fac.log.printRawLines(Log.WriterKind.NOTICE, str);
    }

    public void wain(String str) {
        fac.log.printRawLines(Log.WriterKind.WARNING, str);
    }

    public void error(String str) {
        fac.log.printRawLines(Log.WriterKind.ERROR, str);
    }

    boolean isTarget = false;

    String strPrint = null;

    int groupStartIndex, groupEndIndex;
    Formatter groupFormatter;
    public Tokens.Token readToken() {
        try {
            if (group == null||!groupIterator.hasNext()) {
                int startIndex = reader.bp;
                while (isBlankChar(charAt(startIndex))) {
                    startIndex++;
                }
                String usePrefix = null;
                for (String prefix : Formatter.getPrefixes()) {
                    int endIndex = startIndex + prefix.length();
                    if (charAt(endIndex) == '"' && subChars(startIndex, endIndex).equals(prefix)) {
                        usePrefix = prefix;
                    }
                }
                if (usePrefix == null) return super.readToken();
                Formatter formatter = null;
                for (Formatter f : Formatter.getAllFormatters()) {
                    if (f.prefix().equals(usePrefix))
                        formatter = f;
                }
                int endIndex = startIndex+usePrefix.length();
                while (true) {
                    endIndex++;
                    if (endIndex >= reader.buflen) throw new RuntimeException(startIndex,"未找到匹配结束点" );
                    char ch = charAt(endIndex);
                    if (ch == '\\') {
                        endIndex++;
                        continue;
                    }
                    if (ch == '"') {
                        endIndex++;
                        break;
                    }
                }
                String searchText = subChars(startIndex, endIndex);
                group = GroupStringRange.build(searchText,formatter);
                groupStartIndex=startIndex;
                groupEndIndex=endIndex;
                groupIterator=group.iterator();
            }
            if (reader.bp<groupIterator.next().endIndex){
                return super.readToken();
            }
            GroupStringRange.StringRange range = groupIterator.next();
            int rangeStartIndex = groupStartIndex + range.startIndex;
            int rangeEndIndex = groupStartIndex + range.endIndex;
            String subChars = subChars(rangeStartIndex, rangeEndIndex);
            switch (range.codeStyle){
                case 0:
                    subChars = subChars.replaceAll(Matcher.quoteReplacement( "\\$" ), Matcher.quoteReplacement( "$" ));
                    subChars = toLitChar( subChars);
                    com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
                    Tokens.TokenKind tk = Tokens.TokenKind.STRINGLITERAL;
                    Tokens.StringToken stringToken = new Tokens.StringToken(tk, rangeStartIndex, rangeEndIndex, subChars, var3);
                    return stringToken;
                case 1:
                    reIndex(rangeStartIndex);
                    return
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    List<GroupStringRange.StringRange> group;
    Iterator<GroupStringRange.StringRange> groupIterator;

    public boolean isBlankChar(char ch) {
        if (ch == '\t' || ch == '\f' || ch == ' ' || ch == '\n' || ch == '\r') return true;
        return false;
    }

    private String subChars(int startIndex, int endIndex) {
        if (endIndex > reader.buflen) {
            endIndex = reader.buflen;
        }
        int length = endIndex - startIndex;
        if (length == 0) return "";
        if (startIndex > endIndex) throw new RuntimeException("截取字符串错误： " + startIndex + "~" + endIndex);
        char[] chars = new char[length];
        System.arraycopy(reader.buf, startIndex, chars, 0, length);
        String s = new String(chars);
        return s;
    }

    /**
     * 重定向index
     */
    protected void reIndex(int index) {
        this.reader.bp = index;
        reader.ch = reader.buf[reader.bp];
    }
    private char charAt(int index) {
        return reader.buf[index];
    }

    private static String toLitChar(String textChars) throws Exception {
        StringBuilder str = new StringBuilder();
        int index = -1;
        while (++index < textChars.length()) {
            if (textChars.charAt(index) == '\\') {
                index++;
                if (index == textChars.length()) {
                    throw new RuntimeException("非法字符 in " + textChars);
                }
                if (textChars.charAt(index) == '\\') {
                    if (index + 1 != textChars.length() && textChars.charAt(index + 1) == '$') {
                        index++;
                        str.append('$');
                    } else
                        str.append('\\');
                } else {
                    switch (textChars.charAt(index)) {
                        case '"':
                            str.append('"');
                            break;
                        case '\'':
                            str.append('\'');
                            break;
                        case '0':
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                            int var3 = textChars.charAt(index) - '0';
                            if (index + 1 != textChars.length()) {
                                int v0 = textChars.charAt(index + 1) - '0';
                                if (0 <= v0 && v0 <= 7) {
                                    index++;
                                    var3 = var3 * 8 + v0;
                                    if (index + 1 != textChars.length()) {
                                        int v1 = textChars.charAt(index + 1) - '0';
                                        if (v0 < 3 && 1 <= v0 && v1 <= 7) {
                                            index++;
                                            var3 = var3 * 8 + v1;

                                        }

                                    }
                                }

                            }
                            str.append((char) var3);
                            break;
                        case 'b':
                            str.append('\b');
                            break;
                        case 'f':
                            str.append('\f');
                            break;
                        case 'n':
                            str.append('\n');
                            break;
                        case 'r':
                            str.append('\r');
                            break;
                        case 't':
                            str.append('\t');
                            break;
                        default:
                            str.append(textChars.charAt(index));
                    }
                }
            } else {
                str.append(textChars.charAt(index));
            }

        }
        return str.toString();


    }

}
