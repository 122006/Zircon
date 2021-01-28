package com.sun.tools.javac.parser;

import javax.xml.bind.Element;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class OOJavaTokenizer extends JavaTokenizer {

    protected OOJavaTokenizer(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        super(scannerFactory, charBuffer);
        System.out.println("OOJavaTokenizer1");
    }

    protected OOJavaTokenizer(ScannerFactory scannerFactory, char[] chars, int i) {
        super(scannerFactory, chars, i);
        System.out.println("OOJavaTokenizer2");
    }

    protected OOJavaTokenizer(ScannerFactory scannerFactory, UnicodeReader unicodeReader) {
        super(scannerFactory, unicodeReader);
        System.out.println("OOJavaTokenizer3");
    }

    boolean aroundString = false;

    List<Tokens.Token> wList = new ArrayList<Tokens.Token>();

    public Tokens.Token readToken() {
//        if (!aroundString&& OOProcessor.get(this.reader,"ch").equals('"')){
//            aroundString=true;
//            System.out.println("Start");
//        }
        System.out.println("    readToken:" + subChars(reader.bp, reader.bp + 20));
        if (wList.size() > 0) {
            Tokens.Token remove = wList.remove(0);
            System.out.println("add '" + (remove instanceof Tokens.StringToken ? ((Tokens.StringToken) remove).stringVal : remove) + "'    index: " + remove.pos + "~" + remove.endPos);
            return remove;
        }
        Tokens.Token stringToken = stringHandler();
        if (stringToken != null) {
            System.out.println("add '" + (stringToken instanceof Tokens.StringToken ? ((Tokens.StringToken) stringToken).stringVal : stringToken) + "'    index: " + stringToken.pos + "~" + stringToken.endPos);
            return stringToken;
        }

//        replaceReaderChar();


        Tokens.Token token = super.readToken();
//        System.out.println("Token: name["+token.stringVal()+"]   kind["+token.kind+"]   pos["+token.pos+"]");
        return token;
    }


    public void replaceReaderChar() {
        int oStartIndex = reader.bp;
        switch (charAt(oStartIndex)) {
            case '\t':
            case '\f':
            case ' ':
                do {
                    do {
                        oStartIndex++;
                    } while (charAt(oStartIndex) == ' ');
                } while (charAt(oStartIndex) == '\t' || charAt(oStartIndex) == '\f');
                break;
        }
        if (charAt(oStartIndex) == '$' && charAt(oStartIndex + 1) == '(' && charAt(oStartIndex - 1) == '"') {
            int leftBracket2 = 1;
            int find = oStartIndex + 1;
            int endIndex = -1;
            while (true) {
                if (find >= reader.buflen) throw new RuntimeException(">= buflen");
                if (charAt(find) == '(' && charAt(find - 1) != '\\') {
                    leftBracket2++;
                }
                if (charAt(find) == ')' && charAt(find - 1) != '\\') {
                    leftBracket2--;
                }
                if (leftBracket2 == 0) {
                    endIndex = find + 1;
                    break;
                }
                find++;
            }
            String preCode = subChars(oStartIndex, endIndex);
            String toCode = "";
            int preLength = reader.buf.length;
            int toLength = preLength - preCode.length() + preCode.length();
            char[] chars = new char[toLength];
            System.arraycopy(reader.buf, 0, chars, 0, oStartIndex);
            System.arraycopy(reader.buf, oStartIndex, chars, oStartIndex, toCode.length());
            System.arraycopy(reader.buf, oStartIndex + preCode.length(), chars, oStartIndex + toCode.length(), toLength - (oStartIndex + toCode.length()));
            reader.buf = chars;
//            reader.buflen=toLength;

        }
    }

    public void latterLoadToken(Tokens.Token token) {
        wList.add(token);
    }

    //$"xxxxxxx${new String(\"dsadd\")}fadasdad"
    //"xxxxxxx+new String("dsadd")+fadasdad"

    private Tokens.Token stringHandler() {
        int oStartIndex = reader.bp;
        if (mStringGroup != null && oStartIndex > mStringGroup.endIndex) {
            throw new RuntimeException("搜索结果异常，StringGroup未正确结束");
        }
        switch (charAt(oStartIndex)) {
            case '\t':
            case '\f':
            case ' ':
                do {
                    do {
                        oStartIndex++;
                    } while (charAt(oStartIndex) == ' ');
                } while (charAt(oStartIndex) == '\t' || charAt(oStartIndex) == '\f');
                break;
        }
        if (charAt(oStartIndex) == '"' && charAt(oStartIndex - 2) == '$' && charAt(oStartIndex - 1) == '(') {
            StringGroup stringGroup = createStringGroup(oStartIndex);
            boolean b = stringGroup.searchEnd();
            if (!b) {
                System.out.println("忽略内容: " + subChars(stringGroup.startIndex, stringGroup.endIndex));
                stringGroup.remove();
                return null;
            }
            System.out.println("发现动态字符串: " + subChars(stringGroup.startIndex, stringGroup.endIndex));
            int find = stringGroup.startIndex + 1;
            while (true) {
                if (find >= this.reader.buflen) throw new RuntimeException(">= buflen");
                if (find >= stringGroup.endIndex) throw new RuntimeException(">= string end");
                find = indexOf(oStartIndex, '$', true);
                if (find == -1) {
                    //记录 '"'~'"'的字符串
                    String chars = subChars(oStartIndex + 1, stringGroup.endIndex - 1);
                    reIndex(stringGroup.endIndex);
                    mStringGroup.remove();
                    return loadStringToken(oStartIndex, stringGroup.endIndex + 1, chars);
                } else {
                    if (charAt(find - 1) == '\\' || charAt(find + 1) != '{') continue;
                    //记录 '"'~'${'的字符串
                    stringGroup.createDynamicCode(find);
                    stringGroup.leftBracket++;
                    String chars = subChars(oStartIndex + 1, find);
                    findAndReplaceWhiteBetweenCode(stringGroup, find);
                    reIndex(find + 2);
                    latterLoadToken(loadCommaToken(find, find + 2));
                    return loadStringToken(oStartIndex + 1, find, chars);
                }

            }
        }
        if (mStringGroup != null && !mStringGroup.isEnd()) {
            if (charAt(oStartIndex) == '{' && charAt(oStartIndex - 1) != '\\') {
                mStringGroup.leftBracket++;
                return null;
            }
            if (charAt(oStartIndex) == '}' && charAt(oStartIndex - 1) != '\\') {
                mStringGroup.leftBracket--;
                if (mStringGroup.leftBracket == 0) {
                    //遇到了'}'
                    mStringGroup.getLastDynamicCode().endIndex = oStartIndex;
                    int find = oStartIndex;
                    while (true) {
                        if (find >= this.reader.buflen) throw new RuntimeException(">= buflen");
                        if (find >= mStringGroup.endIndex) throw new RuntimeException(">= string end");
                        find = indexOf(find + 1, '$', true);
                        if (find == -1) {
                            int end = mStringGroup.endIndex;
                            mStringGroup.remove();
                            if (end == oStartIndex + 2) {
                                //'}'后立刻跟一个'"'
                                //跳过'"'
                                reIndex(end);
                                return null;
                            } else {
                                //记录 '}'~'"'的字符串
                                String chars = subChars(oStartIndex + 1, end - 1);
                                reIndex(end);
                                latterLoadToken(loadStringToken(oStartIndex, end, chars));
                                return loadCommaToken(oStartIndex, oStartIndex + 1);
                            }
                        } else {
                            if (charAt(find + 1) != '{') continue;
                            //记录  '}'~'${'
                            String chars = subChars(oStartIndex + 1, find);
                            mStringGroup.createDynamicCode(find);
                            mStringGroup.leftBracket++;
                            findAndReplaceWhiteBetweenCode(mStringGroup, find);
                            reIndex(find + 2);
                            latterLoadToken(loadStringToken(oStartIndex, mStringGroup.getLastDynamicCode().endIndex, chars));
                            latterLoadToken(loadCommaToken(find, find + 2));
                            return loadCommaToken(oStartIndex, oStartIndex + 1);
                        }

                    }
                }
            }
        }
        return null;
    }

    private void findAndReplaceWhiteBetweenCode(StringGroup stringGroup, int find) {
        int searchBracket = find + 2;
        int bracket = 1;
        int bcNum = 0;
        while (true) {
            if (find >= reader.buflen) throw new RuntimeException(">= buflen");
            if (find >= stringGroup.endIndex) throw new RuntimeException(">= string end");
            if (charAt(searchBracket) == '{' && charAt(searchBracket - 1) != '\\') {
                bracket++;
            }
            if (charAt(searchBracket) == '}' && charAt(searchBracket - 1) != '\\') {
                bracket--;
            }
            if (charAt(searchBracket) == '\\' && charAt(searchBracket + 1) == '"') {
                if (charAt(searchBracket-1)=='\\') {
                    if (charAt(searchBracket-2)=='\\') throw new RuntimeException("\"多次转义错误");
                    reader.buf[searchBracket-1] = '"';
                    reader.buf[searchBracket] = ' ';
                    reader.buf[searchBracket + 1] = ' ';
                } else {

                    bcNum++;
                    if (bcNum % 2 == 1) {
                        reader.buf[searchBracket] = ' ';
                        reader.buf[searchBracket + 1] = '"';
                    } else {
                        reader.buf[searchBracket] = '"';
                        reader.buf[searchBracket + 1] = ' ';
                    }
                }

            }
            if (bracket == 0) {
                break;
            }
            searchBracket++;
        }
    }

    private String subChars(int startIndex, int endIndex) {
        if (endIndex > reader.buflen) {
            endIndex = reader.buflen;
        }
        int length = endIndex - startIndex;
        if (length == 0) return "";
        if (length < 0) throw new RuntimeException("截取字符串错误： " + startIndex + "~" + endIndex);
        char[] chars = new char[length];
        System.arraycopy(reader.buf, startIndex, chars, 0, length);
        return new String(chars);
    }

    StringGroup mStringGroup = null;

    protected class StringGroup {
        int startIndex = -1;//'"'
        int endIndex = -1;//'"'+1(end)
        List<DynamicCode> dynamicCodes = new ArrayList<>();

        /**
         * 左大括号数
         */
        int leftBracket = 0;

        /**
         * 左小括号数
         */
        int leftBracket2 = 0;

        private StringGroup() {
        }

        protected class DynamicCode {
            int startIndex = -1;//startWith '$'{
            int endIndex = -1;//  '}'+1

            private DynamicCode() {
            }

            public DynamicCode setEndIndex(int endIndex) {
                this.endIndex = endIndex;
                return this;
            }

            public boolean isEnd() {
                return endIndex != -1;
            }
        }

        public DynamicCode createDynamicCode(int startIndex) {
            DynamicCode e = new DynamicCode();
            e.startIndex = startIndex;
            dynamicCodes.add(e);
            return e;
        }

        public void remove() {
//            this.endIndex = endIndex;
            mStringGroup = null;
        }

        public boolean isEnd() {
            if (dynamicCodes.size() == 0) return true;
            return getLastDynamicCode().isEnd();
        }

        public DynamicCode getLastDynamicCode() {
            return dynamicCodes.get(dynamicCodes.size() - 1);
        }

        public boolean searchEnd() {
            leftBracket2 = 1;
            int find = startIndex + 1;
            int mul = 0;
            while (true) {
                if (find >= reader.buflen) throw new RuntimeException(">= buflen");
                if (charAt(find) == '(' && charAt(find - 1) != '\\') {
                    leftBracket2++;
                }
                if (charAt(find) == ')' && charAt(find - 1) != '\\') {
                    leftBracket2--;
                }

                if (leftBracket2 == 0) {
                    if ((mul == 1) && charAt(find - 1) == '"') {
                        endIndex = find;
                        return true;
                    } else {
                        endIndex = find;
                        return false;
                    }
                }
                if (charAt(find) == '"' && charAt(find - 1) != '\\') {
                    mul++;
                }
                find++;
            }
        }

    }

    public StringGroup createStringGroup(int startIndex) {
        StringGroup group = new StringGroup();
        group.startIndex = startIndex;
        mStringGroup = group;
        return group;
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

    private Tokens.Token loadStringToken(int startIndex, int endIndex, String chars) {
        com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
        this.tk = Tokens.TokenKind.STRINGLITERAL;
        return new Tokens.StringToken(this.tk, startIndex, endIndex, chars, var3);
    }

    private Tokens.Token loadCommaToken(int startIndex, int endIndex) {
        com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
        this.tk = Tokens.TokenKind.COMMA;
        return new Tokens.Token(this.tk, startIndex, endIndex, var3);
    }

    private int indexOf(int startIndex, char ch, boolean must) {
        while (startIndex < this.reader.buflen && startIndex < mStringGroup.endIndex + 1) {
            if (charAt(startIndex) == ch && (must || charAt(startIndex - 1) != '\\')) {
                return startIndex;
            }
            startIndex++;
        }
        return -1;
    }


}
