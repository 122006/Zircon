package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Position;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.CharBuffer;
import java.util.List;
import java.util.stream.Collectors;

import static com.sun.tools.javac.util.LayoutCharacters.FF;

public class ZrJavaTokenizer extends JavaTokenizer {
    public ZrJavaTokenizer(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        super(scannerFactory, charBuffer);
    }

    public ZrJavaTokenizer(ScannerFactory scannerFactory, char[] chars, int i) {
        super(scannerFactory, chars, i);
    }

    UnicodeReader unicodeReader;

    public ZrJavaTokenizer(ScannerFactory scannerFactory, UnicodeReader unicodeReader) {
        super(scannerFactory, unicodeReader.getRawCharacters(), unicodeReader.length());
    }


    /**
     * false:直接解析reader字段
     * <p>
     * true:解析其本身
     */
    public static boolean extendUnicodeReader() {
        if (assignableFrom != null) {
            return assignableFrom;
        }

        assignableFrom = UnicodeReader.class .isAssignableFrom(JavaTokenizer.class);
        return assignableFrom;
    }

    public static JavaTokenizer build(ScannerFactory scannerFactory, char[] chars, int i) {
        return new ZrJavaTokenizer(scannerFactory, chars, i);
    }

    public static JavaTokenizer build(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        return new ZrJavaTokenizer(scannerFactory, charBuffer);
    }

    public Tokens.Token readToken() {
        if (appendTokens != null) {
            if (appendTokens.length > 1) {
                final Tokens.Token appendToken = appendTokens[0];
                Tokens.Token[] newTokens = new Tokens.Token[appendTokens.length - 1];
                System.arraycopy(appendTokens, 1, newTokens, 0, newTokens.length);
                appendTokens = newTokens;
                return appendToken;
            } else if (appendTokens.length == 1) {
                final Tokens.Token appendToken = appendTokens[0];
                appendTokens = null;
                return appendToken;
            }
        }
        try {
            int bp = getReaderBp();
            boolean outLog = items == null;
            Tokens.Token handler = handler();
            if (debug && items != null) {
                if (outLog) {
                    System.out.println();
                    Position.LineMap lineMap = getLineMap();
                    final int bpNow = getReaderBp();
                    System.out.print("[" + lineMap.getLineNumber(bpNow) + "," + lineMap.getColumnNumber(bpNow) + "]");
                }

                String s1 = token2String(handler);
                System.out.print(s1);
            }

            return handler;
        } catch (JavaCException e) {
            throw new RuntimeException("index[" + e.getErrorIndex() + "]发生错误: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private String token2String(Tokens.Token handler) {
        String s1 = "";
        if (handler instanceof Tokens.StringToken) {
            s1 = "\"" + handler.stringVal() + "\"";
        } else if (handler instanceof Tokens.NamedToken) {
            s1 = String.valueOf(((Tokens.NamedToken) handler).name);
        } else if (handler != null) {
            s1 = handler.kind.name;
        }
        return s1;
    }

    public void throwError(int post, String error) {
        throw new JavaCException(post, "index[" + post + "]发生错误: " + error);
    }

    public List<String> getPrefixes() throws Exception {
        if (prefixes != null) return prefixes;
        Class<?> aClass = Class.forName("com.sun.tools.javac.parser.Formatter");
        Method getPrefixes = aClass.getMethod("getPrefixes");
        List<?> invoke = (List<?>) getPrefixes.invoke(null);
        return prefixes = invoke.stream().map(String.class::cast).collect(Collectors.toList());
    }

    public List<Formatter> getAllFormatters() throws Exception {
        if (formatters != null) return formatters;
        Class<?> aClass = Class.forName("com.sun.tools.javac.parser.Formatter");
        Method getAllFormatters = aClass.getMethod("getAllFormatters");
        List<?> invoke = (List<?>) getAllFormatters.invoke(null);
        return formatters = invoke.stream().map(Formatter.class::cast).collect(Collectors.toList());
    }

    Tokens.Token[] appendTokens = null;

    private Tokens.Token handler() throws Exception {
        if (items == null || itemsIndex >= items.size()) {
            items = null;
            int startIndex = getReaderBp();
            while (startIndex < getReaderBuflen() && isBlankChar(charAt(startIndex))) {
                startIndex++;
            }

            String usePrefix = null;

            for (String prefix : getPrefixes()) {
                final int length = prefix.length();
                int endIndex = startIndex + length;
                if (startIndex >= getReaderBuflen() - length) return superReadToken();
                if (charAt(endIndex) == '"' && subChars(startIndex, endIndex).equals(prefix)) {
                    usePrefix = prefix;
                }
            }

            if (usePrefix == null) return superReadToken();
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
                if (endIndex >= getReaderBuflen()) break;
                char ch = charAt(endIndex);
                if (ch == '\n' || ch == '\r') {
                    break;
                }

            }

            String searchText = subChars(startIndex, endIndex);
            final ZrStringModel build = formatter.build(searchText);
            List<StringRange> group = build.getList();
            endIndex = startIndex + build.getEndQuoteIndex() + 1;
            searchText = subChars(startIndex, endIndex);
            groupStartIndex = startIndex;
            groupEndIndex = endIndex;

            items = formatter.stringRange2Group(this, getReaderBuf(), group, searchText, groupStartIndex);
            itemsIndex = 0;
        }

        if (items.size() == 0) {
            reIndex(groupEndIndex);
            return handler();
        }

        Item nowItem = items.get(itemsIndex);
        if (nowItem.token == null) {
            while (isBlankChar(getReaderCh())) {
                reIndex(bpAdd());
            }
            if (getReaderBp() >= nowItem.mappingEndIndex + groupStartIndex) {
                itemsIndex++;
                if (itemsIndex >= items.size()) {
                    reIndex(groupEndIndex);
                } else {
                    nowItem = items.get(itemsIndex);
                    reIndex(nowItem.mappingStartIndex + groupStartIndex);
                }

                return handler();
            } else {
                return superReadToken();
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

    private Tokens.Token superReadToken() {
        switch (getReaderCh()) {
            case ' ': // (Spec 3.6)
            case '\t': // (Spec 3.6)
            case FF: // (Spec 3.6)
                do {
                    reIndex(getReaderBp() + 1);
                } while (getReaderCh() == ' ' || getReaderCh() == '\t' || getReaderCh() == FF);
        }
        int pos = getReaderBp();
        if (getReaderCh() == '?') {
            if (charAt(pos + 1) == '.' && (charAt(pos + 2) < '0' || charAt(pos + 2) > '9')) {
                Name name = fac.names.fromString("$$NullSafe");
                Tokens.TokenKind tk = fac.tokens.lookupKind(name);
                Tokens.NamedToken stringToken = new Tokens.NamedToken(tk, pos, pos, name, null);
                appendTokens = new Tokens.Token[]{stringToken
                        , new Tokens.Token(Tokens.TokenKind.LPAREN, pos, pos, null)
                        , new Tokens.Token(Tokens.TokenKind.RPAREN, pos, pos, null)};
                reIndex(getReaderBp() + 1);
                return new Tokens.Token(Tokens.TokenKind.DOT, pos, pos, null);
            }
        }
        if (getReaderCh() == '?') {
            if (charAt(pos + 1) == ':') {

                Name name0 = fac.names.fromString("zircon");
                Tokens.TokenKind tk0 = fac.tokens.lookupKind(name0);
                Tokens.NamedToken token0 = new Tokens.NamedToken(tk0, pos, pos, name0, null);
                Name name1 = fac.names.fromString("BiOp");
                Tokens.TokenKind tk1 = fac.tokens.lookupKind(name1);
                Tokens.NamedToken token1 = new Tokens.NamedToken(tk1, pos, pos, name1, null);
                Name name2 = fac.names.fromString("$$elvisExpr");
                Tokens.TokenKind tk2 = fac.tokens.lookupKind(name1);
                Tokens.NamedToken token2 = new Tokens.NamedToken(tk2, pos, pos, name2, null);
                appendTokens = new Tokens.Token[]{
                        token0, new Tokens.Token(Tokens.TokenKind.DOT, pos, pos, null),
                        token1, new Tokens.Token(Tokens.TokenKind.DOT, pos, pos, null),
                        token2,
                        new Tokens.Token(Tokens.TokenKind.COLON, pos + 1, pos + 1, null)};
                reIndex(getReaderBp() + 2);
                return new Tokens.Token(Tokens.TokenKind.QUES, pos, pos, null);
            }
        }

        return super.readToken();
    }

    private char getReaderCh() {
        return get();
    }

    private int getReaderBuflen() {
        return length();
    }

    private int getReaderBp() {
        return position();
    }

    char[] buffer;

    private char[] getReaderBuf() {
        if (buffer != null) return buffer;
        try {
            final Field bufferField = UnicodeReader.class .getDeclaredField("buffer");
            bufferField.setAccessible(true);
            return (char[]) bufferField.get(this);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("不支持的javac版本: no field 'buffer' in UnicodeReader");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return getRawCharacters();
    }

    private int bpAdd() {
        int position = position();
        reset(++position);
        return position();
    }


    /**
     * 重定向index
     */
    protected void reIndex(int index) {
        reset(index);
    }

    public boolean isBlankChar(char ch) {
        return ch == '\t' || ch == '\f' || ch == ' ' || ch == '\r' || ch == '\n';
    }

    private String subChars(int startIndex, int endIndex) {
        if (endIndex > getReaderBuflen()) {
            endIndex = getReaderBuflen();
        }

        int length = endIndex - startIndex;
        if (length == 0) return "";
        if (startIndex > endIndex) throw new RuntimeException("截取字符串错误： " + startIndex + "~" + endIndex);
        char[] chars = new char[length];
        System.arraycopy(getReaderBuf(), startIndex, chars, 0, length);
        return new String(chars);
    }

    private char charAt(int index) {
        return getReaderBuf()[index];
    }

    public Boolean getAssignableFrom() {
        return assignableFrom;
    }

    public void setAssignableFrom(Boolean assignableFrom) {
        this.assignableFrom = assignableFrom;
    }

    public int getGroupStartIndex() {
        return groupStartIndex;
    }

    public void setGroupStartIndex(int groupStartIndex) {
        this.groupStartIndex = groupStartIndex;
    }

    public int getGroupEndIndex() {
        return groupEndIndex;
    }

    public void setGroupEndIndex(int groupEndIndex) {
        this.groupEndIndex = groupEndIndex;
    }

    public void setPrefixes(List<String> prefixes) {
        this.prefixes = prefixes;
    }

    public List<Formatter> getFormatters() {
        return formatters;
    }

    public void setFormatters(List<Formatter> formatters) {
        this.formatters = formatters;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public int getItemsIndex() {
        return itemsIndex;
    }

    public void setItemsIndex(int itemsIndex) {
        this.itemsIndex = itemsIndex;
    }

    public static boolean debug = "true".equalsIgnoreCase(System.getenv("Debug"));
    private static Boolean assignableFrom = null;
    private int groupStartIndex;
    private int groupEndIndex;
    private List<String> prefixes;
    private List<Formatter> formatters;
    private List<Item> items;
    private int itemsIndex = 0;

    public static class JavaCException extends RuntimeException {
        public JavaCException(int errorIndex, String errorMsg) {
            super(errorMsg);
            this.errorIndex = errorIndex;
        }

        public int getErrorIndex() {
            return errorIndex;
        }

        public void setErrorIndex(int errorIndex) {
            this.errorIndex = errorIndex;
        }

        private int errorIndex;
    }
}
