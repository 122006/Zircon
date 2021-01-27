package com.sun.tools.javac.parser;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;

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

        if (wList.size() > 0) {
            return wList.remove(0);
        }
        Tokens.Token stringToken = stringHandler();
        if (stringToken != null) {
            return stringToken;
        }


        Tokens.Token token = super.readToken();
//        System.out.println("Token: name["+token.stringVal()+"]   kind["+token.kind+"]   pos["+token.pos+"]");
        return token;
    }

    //$"xxxxxxx${new String(\"dsadd\")}fadasdad"
    //"xxxxxxx+new String("dsadd")+fadasdad"

    private Tokens.Token stringHandler() {
        int oStartIndex = reader.bp;
        if (charAt(oStartIndex) == '$' && charAt(oStartIndex+1) == '"') {
            StringGroup stringGroup = createStringGroup(oStartIndex+1);
            int si=stringGroup.startIndex+1;
            while (true){
                if (si >= this.reader.buflen) throw new RuntimeException(">= buflen");
                si=indexOf(oStartIndex+1,'$');
                if (si==-1){
                    int endIndex=indexOf(oStartIndex+2,'"');
                    if (endIndex!=-1){
                        //记录 '"'~'"'的字符串
                        stringGroup.end(endIndex);
                        String chars = subChars(oStartIndex +2,stringGroup.endIndex);
                        reIndex(endIndex+1);
                        return loadStringToken(oStartIndex,stringGroup.endIndex, chars);
                    }else {
                        throw new RuntimeException("string no end");
                    }
                }else {
                    if (charAt(si-1)=='\\'|| charAt(si+1)!='{')continue;
                    //记录 '"'~'${'的字符串
                    stringGroup.createDynamicCode(si);
                    stringGroup.leftBracket++;
                    String chars = subChars(oStartIndex +2,stringGroup.endIndex);
                    reIndex(si+2);
                    return loadStringToken(oStartIndex,stringGroup.endIndex, chars);
                }

            }
        }
        if (mStringGroup!=null&&!mStringGroup.isEnd()) {
            if (charAt(oStartIndex)=='{'&&charAt(oStartIndex-1)!='\\'){
                mStringGroup.leftBracket++;
            }
            if (charAt(oStartIndex)=='}'&&charAt(oStartIndex-1)!='\\'){
                mStringGroup.leftBracket--;
                if (mStringGroup.leftBracket==0){
                    mStringGroup.getLastDynamicCode().endIndex=oStartIndex;
                    int si=oStartIndex+1;
                    while (true){
                        if (si >= this.reader.buflen) throw new RuntimeException(">= buflen");
                        si=indexOf(oStartIndex+1,'$');
                        if (si==-1){
                            int endIndex=indexOf(oStartIndex+2,'"');
                            if (endIndex!=-1){
                                mStringGroup.end(endIndex);
                                if (endIndex==oStartIndex+1){
                                    //}后立刻跟一个"
                                    //todo 怎么跳过？？？
                                }else {
                                    //记录 '}'~'"'的字符串
                                    String chars = subChars(oStartIndex +1,mStringGroup.endIndex);
                                    reIndex(endIndex+2);
                                    return loadStringToken(oStartIndex,mStringGroup.endIndex, chars);
                                }
                            }else {
                                throw new RuntimeException("string no end");
                            }
                        }else {
                            if (charAt(si-1)=='\\'|| charAt(si+1)!='{')continue;
                            //记录  '}'~'${'
                            String chars = subChars(mStringGroup.getLastDynamicCode().endIndex,si);
                            mStringGroup.createDynamicCode(si);
                            mStringGroup.leftBracket++;
                            reIndex(si+2);
                            return loadStringToken(oStartIndex,mStringGroup.getLastDynamicCode().endIndex, chars);
                        }

                    }
                }
            }
        }
        return null;
    }

    private String subChars(int startIndex,int endIndex) {
        int length=endIndex-startIndex;
        char[] chars=new char[length];
        System.arraycopy(reader.buf, startIndex, chars, 0, length);
        return new String(chars);
    }

    StringGroup mStringGroup =null;

    protected  class StringGroup {
        int startIndex = -1;//'"'
        int endIndex = -1;//'"'(end)
        List<DynamicCode> dynamicCodes = new ArrayList<>();

        int leftBracket=0;

        private StringGroup() {
        }

        protected class DynamicCode {
            int startIndex = -1;//startWith '$'{
            int endIndex = -1;//  '}'+1

            private DynamicCode() {
            }

            public DynamicCode setEndIndex(int endIndex){
                this.endIndex=endIndex;
                return this;
            }
            public boolean isEnd(){
                return endIndex!=-1;
            }
        }
        public DynamicCode createDynamicCode(int startIndex) {
            DynamicCode e = new DynamicCode();
            e.startIndex = startIndex;
            dynamicCodes.add(e);
            return e;
        }
        public void end(int endIndex){
            this.endIndex=endIndex;
            mStringGroup=null;
        }
        public boolean isEnd(){
            if (dynamicCodes.size()==0)return true;
            return getLastDynamicCode().isEnd();
        }

        public DynamicCode getLastDynamicCode(){
            return dynamicCodes.get(dynamicCodes.size()-1);
        }

    }
    public  StringGroup createStringGroup(int startIndex) {
        StringGroup group = new StringGroup();
        group.startIndex = startIndex;
        mStringGroup=group;
        return group;
    }
    /**
     * 重定向index
     */
    protected void reIndex(int index) {
        this.reader.bp = index;
        this.reader.scanChar();
    }

    private char charAt(int index) {
        return reader.buf[index];
    }

    private Tokens.Token loadStringToken(int startIndex, int endIndex, String chars) {
        com.sun.tools.javac.util.List<Tokens.Comment> var3 = null;
        this.tk = Tokens.TokenKind.STRINGLITERAL;
        return new Tokens.StringToken(this.tk, startIndex, endIndex, chars, var3);
    }


    private int indexOf(int startIndex,char ch) {
        int index = reader.bp;
        while (index < this.reader.buflen) {


            return;
        }
        this.lexError(this.reader.bp, "illegal.esc.char");
        return
    }


}
