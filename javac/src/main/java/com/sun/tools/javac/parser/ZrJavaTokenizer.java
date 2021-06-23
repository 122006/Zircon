package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

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
        if (nowChar(reader.bp) != '$' && nowChar(reader.bp) != 'f' && nowChar(reader.bp + 1) != '"') {
            throwError(reader.bp, "unknow group start with :" + subChars(reader.bp, reader.bp + 20));
        }
        Group group = new Group(reader.bp);
        group.searchEnd();
        String searchStr = subChars(group.mappingStartIndex, group.mappingEndIndex);
        log( "匹配到字符串: " + searchStr);
        int searchIndex = group.mappingStartIndex;
        List<GroupStringRange.StringRange> build = GroupStringRange.build(searchStr);
        if (searchStr.startsWith("f")){
            group.loadIdentifierToken(searchIndex, searchIndex + 1, "String");
            group.loadCommaToken(Tokens.TokenKind.DOT, searchIndex+ 1, searchIndex + 1);
            group.loadIdentifierToken(searchIndex+ 1, searchIndex + 1, "format");
            group.loadCommaToken(Tokens.TokenKind.LPAREN, searchIndex + 1, searchIndex + 1);
            group.loadStringToken(searchIndex + 1, searchIndex + 1, GroupStringRange.map2FormatString(searchStr, build));
            for (int i = 0; i < build.size(); i++) {
                GroupStringRange.StringRange a = build.get(i);
                if (a.codeStyle != 0 && a.codeStyle != 1) continue;
                if (a.codeStyle==1) {
                    group.loadCommaToken(Tokens.TokenKind.COMMA, a.endIndex + group.mappingStartIndex, a.endIndex + group.mappingStartIndex);
                    String str = subChars(a.startIndex + group.mappingStartIndex, a.endIndex + group.mappingStartIndex);
                    String toStr = str.replaceAll( "(^|[^\\\\])'([^']+?[^\\\\'])?'" , "$1\"$2\"")
                            .replaceAll( "\\\\?([a-z0-9\"']{1})" , "$1")
                            .replace( "\\\\" , "\\");
                    int replaceCount = str.length() - toStr.length();
                    if (!Objects.equals(str, toStr)) {
                        log( "替代后续文本 ${" + str + "}->${" + toStr + "}");
                        System.arraycopy(toStr.toCharArray(), 0, reader.buf, a.startIndex + group.mappingStartIndex, toStr.length());
                        char[] array = new char[replaceCount];
                        Arrays.fill(array, ' ');
                        System.arraycopy(array, 0, reader.buf, a.startIndex + group.mappingStartIndex + toStr.length(), replaceCount);
                    }
                    group.items.add(new Item(a.startIndex + group.mappingStartIndex, a.endIndex + group.mappingStartIndex, null));
                }
            }
            group.loadCommaToken(Tokens.TokenKind.RPAREN, group.mappingEndIndex, group.mappingEndIndex);
        }else {
            group.loadCommaToken(Tokens.TokenKind.LPAREN, searchIndex, searchIndex + 1);
            if (build.size()>0){
                GroupStringRange.StringRange stringRange = build.get(0);
                int startIndex = stringRange.startIndex + group.mappingStartIndex;
                int endIndex = stringRange.endIndex + group.mappingStartIndex;
                if (stringRange.codeStyle == 1) {
                    group.loadIdentifierToken(startIndex, startIndex, "String");
                    group.loadCommaToken(Tokens.TokenKind.DOT, startIndex,startIndex);
                    group.loadIdentifierToken(startIndex, startIndex, "valueOf");
                    group.loadCommaToken(Tokens.TokenKind.LPAREN, startIndex, startIndex);
                    group.items.add(new Item(startIndex, endIndex, null));
                    group.loadCommaToken(Tokens.TokenKind.RPAREN, endIndex, endIndex);
                } else if (stringRange.codeStyle == 0) {
                    group.loadStringToken(startIndex, endIndex,searchStr.substring(stringRange.startIndex, stringRange.endIndex));
                } else {
                    throwError(startIndex,"[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]");
                }
                for (int i = 1; i < build.size(); i++) {
                    stringRange = build.get(i);
                    startIndex = stringRange.startIndex + group.mappingStartIndex;
                    endIndex = stringRange.endIndex + group.mappingStartIndex;
                    group.loadCommaToken(Tokens.TokenKind.PLUS, startIndex,startIndex);
                    if (stringRange.codeStyle == 1) {
                        group.loadCommaToken(Tokens.TokenKind.LPAREN, startIndex,startIndex);
                        group.items.add(new Item(startIndex,endIndex, null));
                        group.loadCommaToken(Tokens.TokenKind.RPAREN, endIndex, endIndex);
                    } else if (stringRange.codeStyle == 0) {
                        group.loadStringToken(startIndex, endIndex
                                ,searchStr.substring(stringRange.startIndex, stringRange.endIndex));
                    }else {
                        throwError(startIndex,"[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]");
                    }
                }
            }
            group.loadCommaToken(Tokens.TokenKind.RPAREN, group.mappingEndIndex, group.mappingEndIndex);
        }


        thisGroup = group;
    }

    public class Group {
        int mappingStartIndex = -1;//  '$|f'"
        int mappingEndIndex = -1;//    '"'+1
        List<Item> items = new ArrayList<>();

        private Group(int mappingStartIndex) {
            this.mappingStartIndex = indexOf(mappingStartIndex, '"') - 1;
        }

        /**
         * 匹配结束点：'"'
         */
        public void searchEnd() {
            int find = mappingStartIndex + 1;
            while (true) {
                find++;
                if (find >= reader.buflen) throwError(mappingStartIndex, "未找到匹配结束点");
                char ch = charAt(find);
                if (ch == '\\') {
                    find++;
                    continue;
                }
                if (ch == '"') {
                    mappingEndIndex = find + 1;
                    break;
                }
            }
        }

        private int indexOf(int startIndex, char ch) {
            while (startIndex < reader.buflen && (mappingEndIndex == -1 || startIndex < mappingEndIndex + 1)) {
                if (charAt(startIndex) == ch && charAt(startIndex - 1) != '\\') {
                    return startIndex;
                }
                startIndex++;
            }
            return -1;
        }

        private void loadStringToken(int startIndex, int endIndex, String chars) {
            chars = chars.replaceAll(Matcher.quoteReplacement( "\\$"), Matcher.quoteReplacement( "$"));
            chars = toLitChar(startIndex, chars);
            com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
            Tokens.TokenKind tk = Tokens.TokenKind.STRINGLITERAL;
            Tokens.StringToken stringToken = new Tokens.StringToken(tk, startIndex, endIndex, chars, var3);
            items.add(new Item(startIndex, endIndex, stringToken));
        }

        private void loadCommaToken(Tokens.TokenKind tk, int startIndex, int endIndex) {
            com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
            Tokens.Token stringToken = new Tokens.Token(tk, startIndex, endIndex, var3);
            items.add(new Item(startIndex, endIndex, stringToken));
        }

        private void loadIdentifierToken(int startIndex, int endIndex, String identifier) {
            com.sun.tools.javac.util.List<Tokens.NamedToken> var3 = null;
            Name name = reader.names.fromString(identifier);
            Tokens.TokenKind tk = fac.tokens.lookupKind(name);
            Tokens.NamedToken stringToken = new Tokens.NamedToken(tk, startIndex, endIndex, name, null);
            items.add(new Item(startIndex, endIndex, stringToken));
        }

        public String output() {
            String str = "";
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                if (item.token == null
                        && subChars(item.mappingStartIndex, item.mappingEndIndex).trim().length() == 0)
                    continue;
                if (item.token != null) {
                    if (item.token instanceof Tokens.StringToken) {
                        str += ( "\"" + ((Tokens.StringToken) item.token).stringVal + "\"");
                    } else if (item.token instanceof Tokens.NamedToken) {
                        str += ((Tokens.NamedToken) item.token).name.toString();
                    } else {
                        str += (item.token.kind.name);
                    }
                } else {
                    str += (subChars(item.mappingStartIndex, item.mappingEndIndex));
                }
            }
            return str;
        }
    }

    public class Item {
        int mappingStartIndex = -1;
        int mappingEndIndex = -1;
        Tokens.Token token;
        boolean isParseOut = false;

        public Item(int mappingStartIndex, int mappingEndIndex, Tokens.Token token) {
            this.mappingStartIndex = mappingStartIndex;
            this.mappingEndIndex = mappingEndIndex;
            this.token = token;
        }
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
