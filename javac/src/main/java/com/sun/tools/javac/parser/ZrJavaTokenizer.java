package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Log;
import formatter.Formatter;
import formatter.Group;
import formatter.GroupStringRange;
import formatter.Item;

import java.nio.CharBuffer;
import java.util.List;

public class ZrJavaTokenizer extends JavaTokenizer {
    public static boolean debug = "true".equalsIgnoreCase(System.getenv( "Debug"));

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

    public Tokens.Token readToken() {
        Tokens.Token analyse = analyse();
        if (!debug) return analyse;
        if (thisGroup != null) {
            if (strPrint == null) {
                log( "模拟输出: " + thisGroup.output());
                strPrint = "";
            }
            if (analyse instanceof Tokens.NamedToken) {
                strPrint += ((Tokens.NamedToken) analyse).name.toString();
            } else if (analyse instanceof Tokens.NumericToken) {
                strPrint += ((Tokens.NumericToken) analyse).stringVal;
            } else if (analyse instanceof Tokens.StringToken) {
                strPrint += ( "\"" + ((Tokens.StringToken) analyse).stringVal + "\"");
            } else {
                strPrint += (analyse.kind.name);
            }
        } else if (strPrint != null) {
            log( "实际输出: " + strPrint);
            strPrint = null;
        }
        return analyse;
    }

    private Tokens.Token analyse() {
        if (thisGroup == null && isTargetString()) {
            try {
                formatGroup();
            } catch (Exception e) {
                if (debug)
                    e.printStackTrace();
                wain( "[" + subChars(reader.bp, reader.bp + 20) + "] error:" + e.getMessage());
            }
        }
        if (thisGroup != null) {
            int useIndex = -1;
            for (int i = 0; i < thisGroup.items.size(); i++) {
                Item item = thisGroup.items.get(i);
                if (item.token == null
                        && subChars(item.mappingStartIndex, item.mappingEndIndex).trim().length() == 0)
                    continue;
                if (item.isParseOut) continue;
                useIndex = i;
                break;
            }
            if (useIndex != -1) {
                Item item = thisGroup.items.get(useIndex);
                if (item.token != null) {
                    item.isParseOut = true;
                    if (useIndex == thisGroup.items.size() - 1) {
                        reIndex(thisGroup.mappingEndIndex);
                        thisGroup = null;
                    } else {
                        Item nextItem = thisGroup.items.get(useIndex + 1);
                        reIndex(nextItem.mappingStartIndex);
                    }
                    return item.token;
                }
            } else {
                thisGroup = null;
            }
        }
        Tokens.Token token = null;
        try {
            token = super.readToken();
            if (thisGroup != null && (tk != null && tk == Tokens.TokenKind.ERROR))
                error( "[错误]  Tokens: " + thisGroup.output());
        } catch (Throwable e) {
            if (thisGroup != null) error( "[错误]  Tokens: " + thisGroup.output());
            throw e;
        }
        if (thisGroup != null) {
            int nextCharIndex = reader.bp;
            while (isBlankChar(charAt(nextCharIndex))) {
                nextCharIndex++;
            }
            for (int i = 0; i < thisGroup.items.size(); i++) {
                Item item = thisGroup.items.get(i);
                if (!item.isParseOut && nextCharIndex >= item.mappingEndIndex) {
                    item.isParseOut = true;
                    if (i == thisGroup.items.size() - 1) {
                        reIndex(nextCharIndex);
                    } else {
                        reIndex(thisGroup.items.get(i + 1).mappingStartIndex);
                    }
                    break;
                }
            }
        }
        return token;
    }

    public boolean isBlankChar(char ch) {
        if (ch == '\t' || ch == '\f' || ch == ' ' || ch == '\n' || ch == '\r') return true;
        return false;
    }

    private boolean isTargetString() {
        int index = reader.bp;
        if (index == 0) return false;
        while (isBlankChar(charAt(index))) {
            index++;
        }
        return (charAt(index) == '$' || charAt(index) == 'f') && nextChar(index) == '"';
    }

    public void throwError(int post, String error) {
        throw new RuntimeException( "index[" + post + "]发生错误: " + error);
    }

    Group thisGroup;

    public static void main(String[] args) {
        System.out.println( "test (${String.format(\"str:[%s]\",\"format\")})".replace( "\\\\" , "\\").replaceAll( "\\\\([a-z]{1})" , "$1"));
    }


    private void formatGroup() {
        Group group = new Group(this, reader.bp);
        group.searchEnd();
        String searchStr = subChars(group.mappingStartIndex, group.mappingEndIndex);
        log( "匹配到字符串: " + searchStr);
        int searchIndex = group.mappingStartIndex;
        if (searchStr.startsWith( "\"" )) return;
        int endIndex = searchStr.indexOf( "\"" );
        if (endIndex == -1) {
            log( "字符串前缀无法识别" );
            return;
        }
        String prefix = searchStr.substring(0, endIndex);
        List<Formatter> allFormatters=Formatter.getAllFormatters();
        Formatter formatter = allFormatters.stream()
                .filter(a -> a.prefix().test(prefix)).findFirst().orElse(null);
        if (formatter == null) {
            log( "未识别的字符串前缀" );
            return;
        }
        List<GroupStringRange.StringRange> build = GroupStringRange.build(searchStr);
        formatter.code2Tokens(this,group, searchStr);
        thisGroup = group;
    }


    private String subChars(int startIndex, int endIndex) {
        if (endIndex > reader.buflen) {
            endIndex = reader.buflen;
        }
        int length = endIndex - startIndex;
        if (length == 0) return "";
        if (startIndex > endIndex) throw new RuntimeException( "截取字符串错误： " + startIndex + "~" + endIndex);
        char[] chars = new char[length];
        System.arraycopy(reader.buf, startIndex, chars, 0, length);
        String s = new String(chars);
        return s;
    }

    public char nowChar(int targetBp) {
        char findChar;
        while (true) {
            if (targetBp >= reader.buflen) return ' ';
            if (isBlankChar(findChar = charAt(targetBp))) {
                targetBp++;
                continue;
            }
            return findChar;
        }
    }

    public char nextChar(int targetBp) {
        char findChar;
        while (true) {
            targetBp++;
            if (targetBp >= reader.buflen) return ' ';
            if (isBlankChar(findChar = charAt(targetBp))) continue;
            return findChar;
        }
    }

    public char lastChar(int targetBp) {
        char findChar;
        while (true) {
            targetBp--;
            if (targetBp <= 0) return ' ';
            if (isBlankChar(findChar = charAt(targetBp))) continue;
            return findChar;
        }
    }

    public boolean isNext(int targetBp, char nextChar) {
        return nextChar(targetBp) == nextChar;
    }

    public boolean isLast(int targetBp, char nextChar) {
        return lastChar(targetBp) == nextChar;
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

    private String toLitChar(int startIndex, String textChars) {
        String str = "";
        int index = -1;
        while (++index < textChars.length()) {
            if (textChars.charAt(index) == '\\') {
                index++;
                if (index == textChars.length()) {
                    throwError(startIndex, "非法字符 in " + textChars);
                    break;
                }
                ;
                if (textChars.charAt(index) == '\\') {
                    if (index + 1 != textChars.length() && textChars.charAt(index + 1) == '$') {
                        index++;
                        str += ('$');
                    } else
                        str += ('\\');
                } else {
                    switch (textChars.charAt(index)) {
                        case '"':
                            str += ('"');
                            break;
                        case '\'':
                            str += ('\'');
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
                            str += (char) var3;
                            break;
                        case 'b':
                            str += ('\b');
                            break;
                        case 'f':
                            str += ('\f');
                            break;
                        case 'n':
                            str += ('\n');
                            break;
                        case 'r':
                            str += ('\r');
                            break;
                        case 't':
                            str += ('\t');
                            break;
                        default:
                            str += textChars.charAt(index);
                    }
                }
            } else {
                str += textChars.charAt(index);
            }

        }
        return str;


    }

}
