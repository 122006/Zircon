package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Log;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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

    public Tokens.Token readToken() {
        Tokens.Token analyse = analyse();
        if (!debug) return analyse;
        if (thisGroup != null) {
            if (strPrint == null) {
                log("模拟输出: " + thisGroup.output());
                strPrint = "";
            }
            if (analyse instanceof Tokens.NamedToken) {
                strPrint += ((Tokens.NamedToken) analyse).name;
            } else if (analyse instanceof Tokens.NumericToken) {
                strPrint += ((Tokens.NumericToken) analyse).stringVal;
            } else if (analyse instanceof Tokens.StringToken) {
                strPrint += ("\"" + ((Tokens.StringToken) analyse).stringVal + "\"");
            } else {
                strPrint += (analyse.kind.name);
            }
        } else if (strPrint != null) {
            log("实际输出: " + strPrint);
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
                wain("[" + subChars(reader.bp, reader.bp + 20) + "] error:" + e.getMessage());
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
                error("[错误]  Tokens: " + thisGroup.output());
        } catch (Throwable e) {
            if (thisGroup != null) error("[错误]  Tokens: " + thisGroup.output());
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
        while (isBlankChar(charAt(index))) {
            index++;
        }
        return charAt(index) == '$' && charAt(index - 1) != '\\' && nextChar(index) == '(';
    }

    public void throwError(int post, String error) {
        throw new RuntimeException("index[" + post + "]发生错误: " + error);
    }

    Group thisGroup;

    public static void main(String[] args) {
        System.out.println("test (${String.format(\"str:[%s]\",\"format\")})".replace("\\\\", "\\").replaceAll("\\\\([a-z]{1})", "$1"));
    }


    private void formatGroup() {
        if (nowChar(reader.bp) != '$') throwError(reader.bp, "错误");
        Group group = new Group(reader.bp);
        group.searchEnd();
        log("匹配到字符串: " + subChars(group.mappingStartIndex, group.mappingEndIndex));
        int searchIndex = group.mappingStartIndex;
        {
            group.items.add(new Item(searchIndex, searchIndex + 1, null));
        }
        {
            searchIndex = group.indexOf(reader.bp, '(');
            group.items.add(new Item(searchIndex, searchIndex + 1, null));
        }
        char thisItemFirstChar = ' ';
        int thisItemFirstIndex = -1;
        int pCount = 0;
        boolean normalCode = true;//格式代码
        boolean isChar = false;
        boolean isUnicode = false;
        while ((++searchIndex) < group.mappingEndIndex - 1) {
            char ch = charAt(searchIndex);
            log("当前起始char:[" + thisItemFirstChar + "]  搜索到字符：" + ch + " (" + ((int) ch) + ") " + (isChar ? " isChar" : "") + (normalCode ? " normalCode" : ""));
            if (isBlankChar(ch) && thisItemFirstChar != '$') continue;
            if (ch == '\'' && normalCode) {
                isChar = !isChar;
                if (isChar) log("is char");
                continue;
            }
            if (isChar) continue;
            if (ch == '\\' && !normalCode && !isUnicode) {
                isUnicode = true;
                continue;
            }
            if (isUnicode) {
                if (ch == '\\' && charAt(searchIndex + 1) == '$') searchIndex++;
                isUnicode = false;
                continue;
            }
            if (thisItemFirstIndex == -1) {
                if ((ch == '$' || ch == '"')
                        && charAt(searchIndex - 1) != '\\') {
                    normalCode = ch == '$';
                    thisItemFirstChar = ch;
                    thisItemFirstIndex = searchIndex;
                } else {
                    normalCode = true;
                    thisItemFirstChar = ' ';
                    thisItemFirstIndex = searchIndex;
                }
            } else {
                if (thisItemFirstChar == '$' && charAt(thisItemFirstIndex + 1) != '{') {
                    if (ch == '"') {
                        group.items.add(new Item(thisItemFirstIndex + 1, searchIndex, null));
                        thisItemFirstIndex = searchIndex + 1;
                        thisItemFirstChar = ' ';
                        normalCode = true;
                        continue;
                    }
//                    if (ch == '$') {
//                        group.items.add(new Item(thisItemFirstIndex + 1, searchIndex, null));
//                        group.loadCommaToken(searchIndex, searchIndex + 1);
//                        thisItemFirstIndex = searchIndex;
//                        thisItemFirstChar = '$';
//                        normalCode = true;
//                        continue;
//                    }
                    if (!String.valueOf(ch).matches("[A-Za-z0-9_\\u4e00-\\u9fa5.$]+")) {
                        //'$'xxxxx~' '
                        group.items.add(new Item(thisItemFirstIndex + 1, searchIndex, null));
                        group.loadCommaToken(searchIndex, searchIndex + 1);
                        thisItemFirstIndex = searchIndex - 1;
                        thisItemFirstChar = '}';
                        searchIndex--;
                        normalCode = false;
                        continue;
                    }
                } else if (thisItemFirstChar == '$' && charAt(thisItemFirstIndex + 1) == '{') {
                    if (ch == '{' && charAt(searchIndex - 1) != '\\') {
                        pCount++;
                    } else if (ch == '}' && charAt(searchIndex - 1) != '\\') {
                        pCount--;
                        if (pCount == 0) {
                            //'$'{xxxxx~'}'
                            String str = subChars(thisItemFirstIndex + 2, searchIndex);
                            String toStr = str.replaceAll("\\\\{0,1}([a-z0-9\"]{1})", "$1").replace("\\\\", "\\");
                            int replaceCount = str.length() - toStr.length();
                            if (replaceCount != 0) {
                                log("替代后续文本 ${" + str + "}->${" + toStr + "}");
                                System.arraycopy(toStr.toCharArray(), 0, reader.buf, thisItemFirstIndex + 2, toStr.length());
                                char[] array = new char[replaceCount];
                                Arrays.fill(array, ' ');
                                System.arraycopy(array, 0, reader.buf, thisItemFirstIndex + 2 + toStr.length(), replaceCount);

                            }
                            if (searchIndex - (thisItemFirstIndex + 2) == 0) {
                                group.loadStringToken(searchIndex, searchIndex, "");
                            } else {
                                group.items.add(new Item(thisItemFirstIndex + 2, searchIndex, null));
                            }
                            ch = charAt(searchIndex + 1);
                            if (ch == '$') {
                                group.loadCommaToken(searchIndex, searchIndex + 1);
                                thisItemFirstIndex = searchIndex + 1;
                                thisItemFirstChar = '$';
                                normalCode = true;
                                searchIndex++;
                                continue;
                            } else if (ch == '"') {
                                thisItemFirstIndex = searchIndex + 2;
                                thisItemFirstChar = ' ';
                                normalCode = true;
                                searchIndex++;
                                continue;
                            } else {
                                group.loadCommaToken(searchIndex, searchIndex);
                                thisItemFirstIndex = searchIndex;
                                thisItemFirstChar = '}';
                                normalCode = false;
                                continue;
                            }


                        }
                    }
                } else if (thisItemFirstChar == '"') {
                    if (ch == '$') {
                        if (searchIndex - (thisItemFirstChar + 1) > 0) {
                            String str = subChars(thisItemFirstIndex + 1, searchIndex);
                            group.loadStringToken(thisItemFirstIndex, searchIndex, str);
                            group.loadCommaToken(searchIndex, searchIndex + 1);
                        }
                        thisItemFirstIndex = searchIndex;
                        thisItemFirstChar = '$';
                        normalCode = true;
                        continue;
                    } else if (ch == '"') {
                        if (searchIndex - (thisItemFirstChar + 1) > 0) {
                            String str = subChars(thisItemFirstIndex + 1, searchIndex);
                            group.loadStringToken(thisItemFirstIndex, searchIndex, str);
                        }
                        thisItemFirstIndex = searchIndex + 1;
                        thisItemFirstChar = ' ';
                        normalCode = true;
                        continue;
                    }
                } else if (thisItemFirstChar == '}') {
                    if (ch == '$') {
                        if (searchIndex - (thisItemFirstChar + 1) > 0) {
                            String str = subChars(thisItemFirstIndex + 1, searchIndex);
                            group.loadStringToken(thisItemFirstIndex + 1, searchIndex, str);
                            group.loadCommaToken(searchIndex, searchIndex + 1);
                        }
                        thisItemFirstIndex = searchIndex;
                        thisItemFirstChar = '$';
                        normalCode = true;
                        continue;
                    } else if (ch == '"') {
                        if (searchIndex - (thisItemFirstChar + 1) > 0) {
                            String str = subChars(thisItemFirstIndex + 1, searchIndex);
                            group.loadStringToken(thisItemFirstIndex + 1, searchIndex, str);
                        }
                        thisItemFirstIndex = searchIndex + 1;
                        thisItemFirstChar = ' ';
                        normalCode = true;
                        continue;
                    }
                } else if (thisItemFirstChar == ' ' && normalCode) {
                    if (ch == '"' && charAt(thisItemFirstIndex - 1) != '\\') {
                        if (searchIndex - thisItemFirstChar > 0) {
                            group.items.add(new Item(thisItemFirstIndex, searchIndex, null));
                        }
                        if (charAt(searchIndex + 1) == '$') {
                            thisItemFirstIndex = searchIndex + 1;
                            thisItemFirstChar = '$';
                            normalCode = true;
                            searchIndex++;
                            continue;
                        } else {
                            thisItemFirstIndex = searchIndex;
                            thisItemFirstChar = '"';
                            normalCode = false;
                            continue;
                        }

                    }
                }
            }

        }
        if (group.items.size() > 0 && group.items.get(group.items.size() - 1).token != null && group.items.get(group.items.size() - 1).token.kind == Tokens.TokenKind.COMMA) {
            group.items.remove(group.items.size() - 1);
        }
        group.items.add(new Item(thisItemFirstIndex == -1 ? group.mappingEndIndex - 1 : thisItemFirstIndex, group.mappingEndIndex, null));
        thisGroup = group;
    }


    public class Group {
        int mappingStartIndex = -1;//  '$'(
        int mappingEndIndex = -1;//    ')'+1
        List<Item> items = new ArrayList<>();

        private Group(int mappingStartIndex) {
            this.mappingStartIndex = indexOf(mappingStartIndex, '$');
        }

        /**
         * 匹配结束点：')'
         */
        public void searchEnd() {
            int leftBracket2 = 0;
            int find = mappingStartIndex - 1;
            boolean isChar = false;
            boolean isString = false;
            while (true) {
                find++;
                if (find >= reader.buflen) throwError(mappingStartIndex, "未找到匹配结束点");
                char ch = charAt(find);
                if (ch == '\\') {
                    find++;
                    continue;
                }
                if (ch == '\'') {
                    isChar = !isChar;
                    continue;
                }
                if (ch == '\"') {
                    isString = !isString;
                    continue;
                }
                if (isChar || isString) continue;

                if (ch == '(') {
                    leftBracket2++;
                }
                if (ch == ')') {
                    leftBracket2--;
                    if (leftBracket2 == 0) {
                        mappingEndIndex = find + 1;
                        return;
                    }
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
            chars = chars.replaceAll(Matcher.quoteReplacement("\\$"), Matcher.quoteReplacement("$"));
            chars = toLitChar(startIndex, chars);
            com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
            Tokens.TokenKind tk = Tokens.TokenKind.STRINGLITERAL;
            Tokens.StringToken stringToken = new Tokens.StringToken(tk, startIndex, endIndex, chars, var3);
            items.add(new Item(startIndex, endIndex, stringToken));
        }

        private void loadCommaToken(int startIndex, int endIndex) {
            com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
            Tokens.TokenKind tk = Tokens.TokenKind.COMMA;
            Tokens.Token stringToken = new Tokens.Token(tk, startIndex, endIndex, var3);
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
                        str += ("\"" + ((Tokens.StringToken) item.token).stringVal + "\"");
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
        if (startIndex > endIndex) throw new RuntimeException("截取字符串错误： " + startIndex + "~" + endIndex);
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
                    throwError(startIndex, "非法字符");
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
