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

    static List<String> getAllFormattersClazz() {
        List<String> clazzList = new ArrayList<>();
        clazzList.add("com.sun.tools.javac.parser.SStringFormatter");
        clazzList.add("com.sun.tools.javac.parser.FStringFormatter");
        if (!javaVersionUpper(21))
            clazzList.add("com.sun.tools.javac.parser.STRStringFormatter");
        return clazzList;
    }

    public static boolean javaVersionUpper(int versionCode) {
        final String version = System.getProperty("java.version");
        return Integer.parseInt(version.split("\\.")[0]) >= versionCode;
    }

    @SuppressWarnings("unchecked")
    static List<Formatter> getAllFormatters() {
        if (!FORMATTERS.isEmpty()) {
            return FORMATTERS;
        }
        List<Class<? extends Formatter>> classes = getAllFormattersClazz()
                .stream()
                .map(a -> {
                    try {
                        return (Class<? extends Formatter>) Class.forName(a);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Formatter> collect = classes.stream().map(a -> {
            try {
                return (Formatter) a.getConstructor().newInstance();
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

    ZrStringModel build(String text);

    String stringTransfer(String text);

    default String codeTransfer(String text) {
        // todo :这是一个测试，或许可以让字符串代码段格式完全按照非字符串内容格式？
//        if (text.matches( "^\".*|.*[^'\\\\]{1}\".*")) {
//            return text;
//        } else {
//            String toStr = text.replaceAll( "\\\\?([a-z0-9\"']{1})" , "$1")
//                    .replace( "\\\\" , "\\");
//            return toStr;
//        }
        String toStr = text.replaceAll("\\\\?([a-z0-9\"']{1})", "$1")
                           .replace("\\\\", "\\");
        return toStr;
    }

    default String codeTransfer(char[] buf, int groupStartIndex, String text, int startIndex, int endIndex) {
        String str = text.substring(startIndex, endIndex);
        String toStr = codeTransfer(str);
        int replaceCount = str.length() - toStr.length();
        if (!Objects.equals(str, toStr)) {
//            System.err.println( "替代后续文本 ${" + str + "}->${" + toStr + "}");
            System.arraycopy(toStr.toCharArray(), 0, buf, groupStartIndex + startIndex, toStr.length());
            char[] array = new char[replaceCount];
            Arrays.fill(array, ' ');
            System.arraycopy(array, 0, buf, groupStartIndex + startIndex + toStr.length(), replaceCount);
        }
        return toStr;
    }
}
