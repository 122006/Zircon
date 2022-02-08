package com.sun.tools.javac.parser;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public interface Formatter {
//    Logger logger = Logger.getLogger(Formatter.class.getSimpleName());
    List<Formatter> FORMATTERS = new ArrayList<>();
    List<String> PREFIXES = new ArrayList<>();

    static List<Formatter> getAllFormatters() {
        if (!FORMATTERS.isEmpty()) {
            return FORMATTERS;
        }
        List<Class<? extends Formatter>> classes = Arrays.asList(SStringFormatter.class, FStringFormatter.class);
        List<Formatter> collect = classes.stream().map(a -> {
            try {
                return (Formatter)a.getConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
        FORMATTERS.addAll(collect);
        return collect;
    }

    static List<String> getPrefixes() {
        if (!PREFIXES.isEmpty()) {
            return PREFIXES;
        }
        return getAllFormatters().stream().map(Formatter::prefix).collect(Collectors.toList());
    }

    public String prefix();


    public String printOut(List<StringRange> build, String text);

    public List<Item> stringRange2Group(JavaTokenizer javaTokenizer, char[] buf, List<StringRange> build, String text, int groupStartIndex) throws Exception;

    List<StringRange> build(String text);

    String stringTransfer(String text);

    default String codeTransfer(String text) {
        String toStr = text.replaceAll( "(^|[^\\\\])'([^']*?)'", "$1\"$2\"" )
                .replaceAll( "\\\\?([a-z0-9\"']{1})", "$1" )
                .replace( "\\\\", "\\" );
        return toStr;
    }

    default String codeTransfer(char[] buf, int groupStartIndex, String text, int startIndex, int endIndex) {
        String str = text.substring(startIndex, endIndex);
        String toStr = codeTransfer(str);
        int replaceCount = str.length() - toStr.length();
        if (!Objects.equals(str, toStr)) {
//            logger.info( "替代后续文本 ${" + str + "}->${" + toStr + "}" );
            System.arraycopy(toStr.toCharArray(), 0, buf, groupStartIndex + startIndex, toStr.length());
            char[] array = new char[replaceCount];
            Arrays.fill(array, ' ');
            System.arraycopy(array, 0, buf, groupStartIndex + startIndex + toStr.length(), replaceCount);
        }
        return toStr;
    }
}
