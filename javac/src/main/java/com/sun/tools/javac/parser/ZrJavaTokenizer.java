package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Log;
import formatter.*;

import java.nio.CharBuffer;
import java.util.List;

public class ZrJavaTokenizer extends JavaTokenizer {
    public static boolean debug = "true".equalsIgnoreCase(System.getenv( "Debug" ));

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
            int bp = reader.bp;
            Tokens.Token handler = handler();
            String s1 = "";
            if (handler instanceof Tokens.StringToken){
                s1= "\""+handler.stringVal()+"\"";
            }else if (handler instanceof Tokens.NamedToken){
                s1= String.valueOf(((Tokens.NamedToken) handler).name);
            }else if (handler != null){
                s1= handler.kind.name();
            }
            String s = "["+bp+"->" + reader.bp + "]" + s1;
            wain(s);
            return handler;
        } catch (JavaCException e) {
            throw new RuntimeException( "index[" + e.errorIndex + "]发生错误: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void throwError(int post, String error) {
        throw new JavaCException(post, "index[" + post + "]发生错误: " + error);
    }

    public static class JavaCException extends RuntimeException {
        int errorIndex;

        public JavaCException(int errorIndex, String errorMsg) {
            super(errorMsg);
            this.errorIndex = errorIndex;
        }
    }

    private Tokens.Token handler() throws Exception {
        if (items == null || itemsIndex>=items.size()) {
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
                if (f.prefix().equals(usePrefix)) {
                    formatter = f;
                    break;
                }
            }
            if (formatter == null) throwError(startIndex, "没有找到符合的插值器" );
            int endIndex = startIndex + usePrefix.length();
            while (true) {
                endIndex++;
                if (endIndex >= reader.buflen) throwError(startIndex, "未找到匹配结束点" );
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
            List<GroupStringRange.StringRange> group= GroupStringRange.build(searchText, formatter);
            groupStartIndex = startIndex;
            groupEndIndex = endIndex;
            items=formatter.stringRange2Group(this, reader.buf,group,searchText,groupStartIndex);
            itemsIndex=0;
        }
        Item nowItem = items.get(itemsIndex);
        if (nowItem.token==null) {
            Tokens.Token token = super.readToken();
            if (reader.bp >= nowItem.mappingEndIndex){
                itemsIndex++;
                reIndex(groupEndIndex);
            }
            return token;
        }
        Tokens.Token token=nowItem.token;
        itemsIndex++;
        reIndex(nowItem.mappingEndIndex+groupStartIndex);
        return token;
    }



    List<Item> items;
    int itemsIndex=0;

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
        if (startIndex > endIndex) throw new RuntimeException( "截取字符串错误： " + startIndex + "~" + endIndex);
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



}
