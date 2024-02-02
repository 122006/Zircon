package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Position;

import java.lang.reflect.Method;
import java.nio.CharBuffer;
import java.util.List;
import java.util.stream.Collectors;

public class ZrJavaTokenizer extends JavaTokenizer {
    public static boolean debug = "true".equalsIgnoreCase(System.getenv("Debug"));

    public ZrJavaTokenizer(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        super(scannerFactory, charBuffer);
    }

    public ZrJavaTokenizer(ScannerFactory scannerFactory, char[] chars, int i) {
        super(scannerFactory, chars, i);
    }

    public ZrJavaTokenizer(ScannerFactory scannerFactory, UnicodeReader unicodeReader) {
        super(scannerFactory, unicodeReader);
    }

    int groupStartIndex, groupEndIndex;

    public Tokens.Token readToken() {
        try {
            int bp = reader.bp;
            boolean outLog = items == null;
            Tokens.Token handler = handler();
            if (debug && items != null) {
                if (outLog) {
                    System.out.println();
                    Position.LineMap lineMap = getLineMap();
                    System.out.print("[" + lineMap.getLineNumber(reader.bp) + "," + lineMap.getColumnNumber(reader.bp) + "]");
                }
                String s1 = "";
                if (handler instanceof Tokens.StringToken) {
                    s1 = "\"" + handler.stringVal() + "\"";
                } else if (handler instanceof Tokens.NamedToken) {
                    s1 = String.valueOf(((Tokens.NamedToken) handler).name);
                } else if (handler != null) {
                    s1 = handler.kind.name;
                }
                System.out.print(s1);
            }
            return handler;
        } catch (JavaCException e) {
            throw new RuntimeException("index[" + e.errorIndex + "]发生错误: " + e.getMessage());
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

    List<String> prefixes;

    public List<String> getPrefixes() throws Exception {
        if (prefixes != null) return prefixes;
        Class<?> aClass = Class.forName("com.sun.tools.javac.parser.Formatter");
        Method getPrefixes = aClass.getMethod("getPrefixes");
        List<?> invoke = (List<?>) getPrefixes.invoke(null);
        return prefixes = invoke.stream().map(String.class::cast).collect(Collectors.toList());
    }

    List<Formatter> formatters;

    public List<Formatter> getAllFormatters() throws Exception {
        if (formatters != null) return formatters;
        Class<?> aClass = Class.forName("com.sun.tools.javac.parser.Formatter");
        Method getAllFormatters = aClass.getMethod("getAllFormatters");
        List<?> invoke = (List<?>) getAllFormatters.invoke(null);
        return formatters = invoke.stream().map(Formatter.class::cast).collect(Collectors.toList());
    }

    private Tokens.Token handler() throws Exception {
        if (items == null || itemsIndex >= items.size()) {
            items = null;
            int startIndex = reader.bp;
            while (isBlankChar(charAt(startIndex)) && startIndex < reader.buflen) {
                startIndex++;
            }
            String usePrefix = null;

            for (String prefix : getPrefixes()) {
                final int length = prefix.length();
                int endIndex = startIndex + length;
                if (startIndex >= reader.buflen - length) return super.readToken();
                if (charAt(endIndex) == '"' && subChars(startIndex, endIndex).equals(prefix)) {
                    usePrefix = prefix;
                }
            }
            if (usePrefix == null) return super.readToken();
            Formatter formatter = null;
            for (Formatter f : getAllFormatters()) {
                if (f.prefix().equals(usePrefix)) {
                    formatter = f;
                    break;
                }
            }
            if (formatter == null) throwError(startIndex, "没有找到符合的插值器");
            assert formatter != null;
            int endIndex = startIndex + usePrefix.length();
            while (true) {
                endIndex++;
                if (endIndex >= reader.buflen) break;
                char ch = charAt(endIndex);
                if (ch == '\n' || ch == '\r') {
                    break;
                }
            }
            String searchText = subChars(startIndex, endIndex);
            final ZrStringModel build = formatter.build(searchText);
            List<StringRange> group = build.getList();
            endIndex =startIndex+ + build.getEndQuoteIndex()+1;
            searchText = subChars(startIndex, endIndex);
            groupStartIndex = startIndex;
            groupEndIndex = endIndex;
            items = formatter.stringRange2Group(this, reader.buf, group, searchText, groupStartIndex);
            itemsIndex = 0;
        }
        if (items.size() == 0) {
            reIndex(groupEndIndex);
            return handler();
        }
        Item nowItem = items.get(itemsIndex);
        if (nowItem.token == null) {
            while (isBlankChar(reader.ch)) {
                reIndex(++reader.bp);
            }
            if (reader.bp >= nowItem.mappingEndIndex + groupStartIndex) {
                itemsIndex++;
                if (itemsIndex >= items.size()) {
                    reIndex(groupEndIndex);
                } else {
                    nowItem = items.get(itemsIndex);
                    reIndex(nowItem.mappingStartIndex + groupStartIndex);
                }
                return handler();
            } else {
                return super.readToken();
            }
        }
        Tokens.Token token = nowItem.token;
        itemsIndex++;
        if (itemsIndex >= items.size()) {
            reIndex(groupEndIndex);
        } else {
            nowItem = items.get(itemsIndex);
            reIndex(nowItem.mappingStartIndex + groupStartIndex);
        }
        return token;
    }


    List<Item> items;
    int itemsIndex = 0;

    public boolean isBlankChar(char ch) {
        return ch == '\t' || ch == '\f' || ch == ' ' || ch == '\r' || ch == '\n';
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
