package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Log;
import jdk.nashorn.internal.ir.IfNode;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZrJavaTokenizer extends JavaTokenizer {
    static boolean debug = false;

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

    public Tokens.Token readToken() {
//        System.out.println("    readToken:" + subChars(reader.bp, reader.bp + 20));
        if (thisGroup == null && isTargetString()) {
            try {
                formatGroup();
//                for (Item item : thisGroup.items) {
//                    if (item.token != null) {
//                        System.out.println("    token:" + item.token.kind);
//                    } else {
//                        System.out.println("    token:" + null);
//                    }
//                }
            } catch (Exception e) {
                wain("[" + subChars(reader.bp, reader.bp + 20) + "] " + e.getMessage());
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
        Tokens.Token token = super.readToken();
        if (thisGroup != null) {
            int nextCharIndex = reader.bp;
            while (charAt(nextCharIndex) == ' ') {
                nextCharIndex++;
            }
            for (int i = 0; i < thisGroup.items.size(); i++) {
                Item item = thisGroup.items.get(i);
                if (!item.isParseOut && nextCharIndex >= item.mappingEndIndex) {
                    item.isParseOut = true;
                    break;
                }
            }
        }

//        System.out.println("Token: name["+token.stringVal()+"]   kind["+token.kind+"]   pos["+token.pos+"]");
        return token;
    }

    private boolean isTargetString() {
        int index = reader.bp;
        switch (charAt(index)) {
            case '\t':
            case '\f':
            case ' ':
                do {
                    do {
                        index++;
                    } while (charAt(index) == ' ');
                } while (charAt(index) == '\t' || charAt(index) == '\f');
                break;
        }
        return charAt(index) == '$' && charAt(index - 1) != '\\' && nextChar(index) == '(';
    }

    public void throwError(int post, String error) {
        throw new RuntimeException("index[" + post + "]发生错误: " + error);
    }

    Group thisGroup;

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
        boolean normalCode = true;
        boolean isChar = false;
        while ((++searchIndex) < group.mappingEndIndex - 1) {
            char ch = charAt(searchIndex);
            log("当前起始char:[" + thisItemFirstChar + "]  搜索到字符：" + ch+(isChar?" isChar":"")+(normalCode?" normalCode":""));
            if (ch == ' ') continue;
            if (ch == '\'' && normalCode) {
                isChar = !isChar;
                if (isChar) log("is char");
                continue;
            }
            if (isChar) continue;
            if (thisItemFirstIndex == -1) {
                if ((ch == '$' && charAt(searchIndex + 1) == '{' || ch == '"' || ch == '}')
                        && charAt(searchIndex - 1) != '\\') {
                    normalCode = false;
                    thisItemFirstChar = ch;
                    thisItemFirstIndex = searchIndex;
                } else {
                    normalCode = true;
                    thisItemFirstChar = ' ';
                    thisItemFirstIndex = searchIndex;
//                    error(thisItemFirstIndex, "格式化字符串格式起始错误：" + ch);
                }
            } else {
                if (normalCode && ch == '"' && charAt(searchIndex - 1) != '\\') {
                    group.items.add(new Item(thisItemFirstIndex, searchIndex, null));
                    normalCode = false;
                    thisItemFirstIndex = -1;
                    searchIndex--;
                    continue;
                }
                if (ch == '"' && charAt(searchIndex - 1) != '\\') {
                    if (thisItemFirstChar == '$') throwError(thisItemFirstIndex, "${}表达式还未结束");
                    //   '"'xxxxx~'"'
                    String str = subChars(thisItemFirstIndex + 1, searchIndex);
                    group.loadStringToken(thisItemFirstIndex, searchIndex, str);
                    thisItemFirstIndex = -1;
                } else if (ch == '$' && charAt(searchIndex - 1) != '\\' && charAt(searchIndex + 1) == '{') {
                    if (thisItemFirstChar == '$') throwError(thisItemFirstIndex, "不支持${}表达式嵌套");
                    //   '"'xxxxx~'$'{
                    String str = subChars(thisItemFirstIndex + 1, searchIndex);
                    group.loadStringToken(thisItemFirstIndex, searchIndex, str);
                    thisItemFirstIndex = -1;
                    group.loadCommaToken(searchIndex, searchIndex + 1);
                    searchIndex--;
                } else if (thisItemFirstChar == '$') {
                    if (ch == '{' && charAt(searchIndex - 1) != '\\') {
                        pCount++;
                    } else if (ch == '}' && charAt(searchIndex - 1) != '\\') {
                        pCount--;
                        if (pCount == 0) {
                            //'$'{xxxxx~'}'
                            String str = subChars(thisItemFirstIndex + 2, searchIndex);
                            String toStr = str.replace("\\\"", "\"").replace("\\\\", "\\");
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

                            group.loadCommaToken(searchIndex, searchIndex + 1);
                            searchIndex--;
                            thisItemFirstIndex = -1;
                            thisItemFirstChar = ' ';
                        }
                    }
                }

            }

        }
        group.items.add(new Item(thisItemFirstIndex==-1? group.mappingEndIndex-1:thisItemFirstIndex, group.mappingEndIndex - 1, null));
        {
            group.items.add(new Item(group.mappingEndIndex - 1, group.mappingEndIndex, null));
        }
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
            int find = mappingStartIndex-1;
            boolean isChar = false;
            boolean isString = false;
            while (true) {
                find++;
                if (find >= reader.buflen) throwError(mappingStartIndex, "未找到匹配结束点");
                char ch = charAt(find);
                if (charAt(find - 1) == '\\'){
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
                if (ch == '(' ) {
                    leftBracket2++;
                }
                if (ch == ')' ) {
                    leftBracket2--;
                    if (leftBracket2 == 0) {
                        mappingEndIndex = find+1;
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
        return new String(chars);
    }

    public char nowChar(int targetBp) {
        char findChar;
        while (true) {
            if (targetBp >= reader.buflen) return ' ';
            if ((findChar = charAt(targetBp)) == ' ') {
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
            if ((findChar = charAt(targetBp)) == ' ') continue;
            return findChar;
        }
    }

    public char lastChar(int targetBp) {
        char findChar;
        while (true) {
            targetBp--;
            if (targetBp <= 0) return ' ';
            if ((findChar = charAt(targetBp)) == ' ') continue;
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


}
