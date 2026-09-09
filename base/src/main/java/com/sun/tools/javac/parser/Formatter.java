package com.sun.tools.javac.parser;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface Formatter {
    //    Logger logger = Logger.getLogger(Formatter.class.getSimpleName());
    List<Formatter> FORMATTERS = new ArrayList<>();
    List<String> PREFIXES = new ArrayList<>();

    static List<String> getAllFormattersClazz() {
        List<String> clazzList = new ArrayList<>();
        clazzList.add("com.sun.tools.javac.parser.SStringFormatter");
        clazzList.add("com.sun.tools.javac.parser.FStringFormatter");
        clazzList.add("com.sun.tools.javac.parser.JStringFormatter");
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
        List<Formatter> collect = new ArrayList<>();
        for (String className : getAllFormattersClazz()) {
            try {
                Class<?> clazz = Class.forName(className);
                Object value = clazz.getConstructor().newInstance();
                if (!(value instanceof Formatter)) {
                    throw new LinkageError("not a Formatter");
                }
                collect.add((Formatter) value);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("[ZR9002] 无法加载字符串适配器 " + className
                        + "，JDK=" + System.getProperty("java.version"), failure);
            }
        }
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

    static ZrStringModel buildFromSharedSplitter(
            Formatter formatter,
            String text,
            TemplateStringSplitter.Syntax syntax
    ) {
        TemplateStringSplitter.Result split = TemplateStringSplitter.split(text, formatter.prefix(), syntax);
        ZrStringModel model = new ZrStringModel();
        model.setFormatter(formatter);
        model.addDiagnostics(split.diagnostics);
        for (TemplateStringSplitter.Range range : split.ranges) {
            if (range.style == TemplateStringSplitter.CODE) {
                // Empty interpolation is an established Zircon no-op
                // (`$"${}"` == ""). Keep it in the lexical split result so
                // IDEs can preserve structure, but do not emit an empty Java
                // expression into javac's rewritten token stream.
                if (range.startIndex == range.endIndex) {
                    continue;
                }
                model.getList().add(StringRange.code(formatter, text, range.startIndex, range.endIndex));
            } else if (range.style == TemplateStringSplitter.STRING) {
                model.getList().add(StringRange.string(formatter, text, range.startIndex, range.endIndex));
            } else {
                model.getList().add(StringRange.of(range.style, range.startIndex, range.endIndex));
            }
        }
        model.setOriginalString(split.originalString);
        model.setEndQuoteIndex(split.endQuoteIndex);
        return model;
    }

    String stringTransfer(String text);

    default String codeTransfer(String text) {
        return codeTransferWithOffsets(text).getText();
    }

    /**
     * Applies the same transfer used by javac while retaining a mapping back to
     * the original template range. IDEA uses this to place diagnostics on raw
     * escaped source instead of on the shorter transferred expression.
     */
    default CodeTransferResult codeTransferWithOffsets(String text) {
        if (text == null || text.isEmpty()) {
            return new CodeTransferResult("", text == null ? 0 : text.length(),
                    new int[0], new int[0]);
        }

        List<MappedCharacter> firstPass = new ArrayList<>();
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\\'
                    && index + 1 < text.length()
                    && isCodeTransferCharacter(text.charAt(index + 1))) {
                firstPass.add(new MappedCharacter(
                        text.charAt(index + 1), index, index + 2));
                index++;
            } else {
                firstPass.add(new MappedCharacter(value, index, index + 1));
            }
        }

        List<MappedCharacter> secondPass = new ArrayList<>();
        for (int index = 0; index < firstPass.size(); index++) {
            MappedCharacter current = firstPass.get(index);
            if (current.value == '\\'
                    && index + 1 < firstPass.size()
                    && firstPass.get(index + 1).value == '\\') {
                MappedCharacter next = firstPass.get(++index);
                secondPass.add(new MappedCharacter(
                        '\\', current.rawStart, next.rawEnd));
            } else {
                secondPass.add(current);
            }
        }

        StringBuilder transferred = new StringBuilder(secondPass.size());
        int[] rawStarts = new int[secondPass.size()];
        int[] rawEnds = new int[secondPass.size()];
        for (int index = 0; index < secondPass.size(); index++) {
            MappedCharacter character = secondPass.get(index);
            transferred.append(character.value);
            rawStarts[index] = character.rawStart;
            rawEnds[index] = character.rawEnd;
        }
        return new CodeTransferResult(
                transferred.toString(), text.length(), rawStarts, rawEnds);
    }

    static boolean isCodeTransferCharacter(char value) {
        return value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '"'
                || value == '\'';
    }

    default String codeTransfer(char[] buf, int groupStartIndex, String text, int startIndex, int endIndex) {
        return transferCode(buf, groupStartIndex, text, startIndex, endIndex).getText();
    }

    default CodeTransferResult transferCode(char[] buf, int groupStartIndex, String text,
                                             int startIndex, int endIndex) {
        CodeTransferResult transfer = codeTransferWithOffsets(text.substring(startIndex, endIndex));
        String source = text.substring(startIndex, endIndex);
        if (!source.equals(transfer.getText())) {
            System.arraycopy(transfer.getText().toCharArray(), 0, buf,
                    groupStartIndex + startIndex, transfer.getText().length());
            char[] padding = new char[source.length() - transfer.getText().length()];
            Arrays.fill(padding, ' ');
            System.arraycopy(padding, 0, buf,
                    groupStartIndex + startIndex + transfer.getText().length(), padding.length);
        }
        return transfer;
    }

    final class MappedCharacter {
        final char value;
        final int rawStart;
        final int rawEnd;

        MappedCharacter(char value, int rawStart, int rawEnd) {
            this.value = value;
            this.rawStart = rawStart;
            this.rawEnd = rawEnd;
        }
    }

    final class CodeTransferResult {
        private final String text;
        private final int rawLength;
        private final int[] rawStarts;
        private final int[] rawEnds;

        CodeTransferResult(String text, int rawLength, int[] rawStarts, int[] rawEnds) {
            this.text = text;
            this.rawLength = rawLength;
            this.rawStarts = rawStarts;
            this.rawEnds = rawEnds;
        }

        public String getText() {
            return text;
        }

        public int rawStartOffset(int transferredOffset) {
            int offset = Math.max(0, Math.min(text.length(), transferredOffset));
            if (offset == text.length()) {
                return rawLength;
            }
            return rawStarts[offset];
        }

        public int rawEndOffset(int transferredOffset) {
            int offset = Math.max(0, Math.min(text.length(), transferredOffset));
            if (offset == 0) {
                return 0;
            }
            return rawEnds[offset - 1];
        }

        public int rawBoundaryOffset(int transferredOffset) {
            int offset = Math.max(0, Math.min(text.length(), transferredOffset));
            return offset == 0 ? 0 : rawEnds[offset - 1];
        }
    }
}
