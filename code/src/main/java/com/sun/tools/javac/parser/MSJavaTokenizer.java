package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Log;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MSJavaTokenizer extends JavaTokenizer {

    protected MSJavaTokenizer(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        super(scannerFactory, charBuffer);
    }

    protected MSJavaTokenizer(ScannerFactory scannerFactory, char[] chars, int i) {
        super(scannerFactory, chars, i);
    }

    protected MSJavaTokenizer(ScannerFactory scannerFactory, UnicodeReader unicodeReader) {
        super(scannerFactory, unicodeReader);
    }


    public void log(String str) {
        fac.log.printRawLines(Log.WriterKind.NOTICE, str);
    }

    public void wain(String str) {
        fac.log.printRawLines(Log.WriterKind.WARNING, str);
    }

    public void error(String str) {
        fac.log.printRawLines(Log.WriterKind.ERROR, str);
    }

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
        return charAt(index) == '$' && nextChar(index) == '(';
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
        boolean findComma = false;
        while ((++searchIndex) < group.mappingEndIndex - 1) {
            char ch = charAt(searchIndex);
//            log("当前起始char:[" + thisItemFirstChar + "]  搜索到字符：" + ch);
            if (ch == ' ') continue;
            if (findComma) {
                if (lastChar(searchIndex) == '\\') continue;
                switch (ch) {
                    case '(':
                        switch (thisItemFirstChar){
                            case '{':
                                break;
                            case '(':
                                pCount++;
                                break;
                            case ' ':
                                pCount=1;
                                thisItemFirstChar='(';
                                break;
                            default:
                                throwError(searchIndex,"Code模式异常开始符:"+thisItemFirstChar);
                                break;
                        }
                        break;
                    case '{':
                        switch (thisItemFirstChar){
                            case '{':
                                pCount++;
                                break;
                            case '(':
                                break;
                            case ' ':
                                pCount=1;
                                thisItemFirstChar='{';
                                break;
                            default:
                                throwError(searchIndex,"Code模式异常开始符:"+thisItemFirstChar);
                                break;
                        }
                        break;
                    case ')':
                        switch (thisItemFirstChar){
                            case '(':
                                pCount--;
                                break;
                            case '{':
                                break;
                            default:
                                throwError(searchIndex,"Code模式异常开始符:"+thisItemFirstChar);
                                break;
                        }
                        break;
                    case '}':
                        switch (thisItemFirstChar){
                            case '(':
                                break;
                            case '{':
                                pCount--;
                                break;
                            default:
                                throwError(searchIndex,"Code模式异常开始符:"+thisItemFirstChar);
                                break;
                        }
                        break;
                    case ',':
                        if (pCount==0){
                            group.items.add(new Item(thisItemFirstIndex, searchIndex, null));
                            group.items.add(new Item(searchIndex, searchIndex + 1, null));
                            findComma=false;
                        }
                        break;
                    default:
                        if (searchIndex==group.mappingEndIndex - 1){
                            if (pCount!=0){
                                throwError(searchIndex,"未完整的Code模式");
                            }else {
                                group.items.add(new Item(thisItemFirstIndex, searchIndex, null));
                                findComma=false;
                            }
                        }
                        continue;
                }
            }
            if (thisItemFirstIndex == -1) {
                if (ch == ',') {
                    group.items.add(new Item(searchIndex, searchIndex + 1, null));
                } else if ((ch == '$' && charAt(searchIndex - 1) != '\\' && charAt(searchIndex + 1) == '{')
                        || ch == '"') {
                    thisItemFirstChar = ch;
                    thisItemFirstIndex = searchIndex;
                } else if (ch == '}') {
                    thisItemFirstChar = ch;
                    thisItemFirstIndex = searchIndex;
                } else {
                    findComma = true;
                    thisItemFirstChar = ' ';
                    thisItemFirstIndex = searchIndex;
//                    error(thisItemFirstIndex, "格式化字符串格式起始错误：" + ch);
                }

            } else {
                if (ch == '"' && charAt(searchIndex - 1) != '\\') {
                    if (thisItemFirstChar == '$') throwError(thisItemFirstIndex, "${}表达式还未结束");
                    //   '"'xxxxx~'"'
                    String str = subChars(thisItemFirstIndex + 1, searchIndex);
                    group.loadStringToken(thisItemFirstIndex, searchIndex, str);
                    thisItemFirstIndex = -1;
                    char nc = nextChar(searchIndex);
                    if (nc != ',' && nc != ')')
                        group.loadCommaToken(searchIndex, searchIndex + 1);
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
            int leftBracket2 = -1;
            int find = mappingStartIndex;
            while (true) {
                if (find >= reader.buflen) throwError(mappingStartIndex, "未找到匹配结束点");
                if (charAt(find) == '(' && charAt(find - 1) != '\\') {
                    leftBracket2 = (leftBracket2 == -1) ? 1 : (leftBracket2 + 1);
                }
                if (charAt(find) == ')' && charAt(find - 1) != '\\') {
                    leftBracket2--;
                }
                find++;
                if (leftBracket2 == 0) {
                    mappingEndIndex = find;
                    return;
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
