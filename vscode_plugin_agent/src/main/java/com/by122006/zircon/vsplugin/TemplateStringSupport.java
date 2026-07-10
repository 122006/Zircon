package com.by122006.zircon.vsplugin;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.WeakHashMap;

public final class TemplateStringSupport {
    private static final Map<Object, TemplateScannerState> TEMPLATE_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int TEMPLATE_TRANSLATION_CACHE_LIMIT = 512;
    private static final Map<String, CachedTemplateTranslation> TEMPLATE_TRANSLATION_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<String, CachedTemplateTranslation>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedTemplateTranslation> eldest) {
                    return size() > TEMPLATE_TRANSLATION_CACHE_LIMIT;
                }
            });
    private static final ThreadLocal<Integer> DISABLED_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final List<TemplateFormatter> FORMATTERS = Arrays.asList(
            new STRStringFormatter(),
            new FStringFormatter(),
            new JStringFormatter(),
            new SStringFormatter()
    );

    private TemplateStringSupport() {
    }

    private static boolean isVscodeMode() {
        return getBooleanProperty("zircon.vscode", false);
    }

    public static Object beforeGetNextToken(Object scanner) {
        if (scanner == null || isDisabled()) {
            return null;
        }
        try {
            TemplateScannerState existing = TEMPLATE_STATES.get(scanner);
            if (existing != null) {
                if (existing.hasRemainingTokens()) {
                    return existing.next(scanner);
                }
                restoreScannerPosition(scanner, existing.resumePosition);
                TEMPLATE_STATES.remove(scanner);
            }

            TemplateTranslation translation = tryCreateTranslation(scanner);
            if (translation == null) {
                return null;
            }

            TemplateScannerState state = createState(scanner, translation);
            if (state == null || !state.hasRemainingTokens()) {
                return null;
            }
            TEMPLATE_STATES.put(scanner, state);
            if (isTraceEnabled()) {
                log("[TemplateScanner] prefix=" + translation.prefix
                        + ", template=" + translation.originalStart + "-" + translation.originalEnd
                        + ", translated=" + translation.translatedExpression);
            }
            return state.next(scanner);
        } catch (Throwable error) {
            TEMPLATE_STATES.remove(scanner);
            if (isDebugEnabled()) {
                Throwable cause = error instanceof java.lang.reflect.InvocationTargetException
                        && ((java.lang.reflect.InvocationTargetException) error).getCause() != null
                        ? ((java.lang.reflect.InvocationTargetException) error).getCause()
                        : error;
                log("[TemplateScanner] inject failed: " + cause.getClass().getName() + ": " + cause.getMessage());
                log(stackTrace(cause));
            }
            return null;
        }
    }

    public static Object beforeAccessor(Object scanner, String methodName) {
        if (scanner == null || isDisabled()) {
            return null;
        }
        TemplateScannerState state = TEMPLATE_STATES.get(scanner);
        if (state == null || state.current == null) {
            return null;
        }
        switch (methodName) {
            case "getCurrentTokenSource":
            case "getCurrentIdentifierSource":
                return state.current.currentTokenSource;
            case "getCurrentTokenSourceString":
                return state.current.currentTokenSourceString != null
                        ? state.current.currentTokenSourceString
                        : state.current.currentTokenSource;
            case "getCurrentStringLiteral":
                return state.current.currentStringLiteral;
            case "getRawTokenSource":
                return state.current.rawTokenSource != null
                        ? state.current.rawTokenSource
                        : state.current.currentTokenSource;
            case "getCurrentTokenStartPosition":
                return state.current.originalStart;
            case "getCurrentTokenEndPosition":
                return state.current.originalEnd;
            default:
                return null;
        }
    }

    public static void clearState(Object scanner) {
        if (scanner != null) {
            TEMPLATE_STATES.remove(scanner);
        }
    }

    private static TemplateScannerState createState(Object scanner, TemplateTranslation translation) throws Exception {
        Object syntheticScanner = createSyntheticScanner(scanner, translation.translatedExpression.toCharArray());
        List<SyntheticToken> tokens = new ArrayList<>();
        runWithoutInterception(() -> {
            while (true) {
                Object token = invokeNoArgs(syntheticScanner, "getNextToken");
                if (isTerminalToken(token, "TokenNameEOF")) {
                    return null;
                }
                tokens.add(snapshotSyntheticToken(syntheticScanner, token, translation));
            }
        });
        return tokens.isEmpty() ? null : new TemplateScannerState(tokens, translation.resumePosition);
    }

    private static SyntheticToken snapshotSyntheticToken(Object syntheticScanner, Object token, TemplateTranslation translation) throws Exception {
        int syntheticStart = ((Integer) invokeNoArgs(syntheticScanner, "getCurrentTokenStartPosition"));
        int syntheticEnd = ((Integer) invokeNoArgs(syntheticScanner, "getCurrentTokenEndPosition"));
        int originalStart = Math.min(translation.originalEnd, translation.originalStart + Math.max(0, syntheticStart));
        int originalEnd = Math.min(translation.originalEnd, translation.originalStart + Math.max(0, syntheticEnd));
        if (originalEnd < originalStart) {
            originalEnd = originalStart;
        }
        char[] directTokenSlice = readTokenSlice(syntheticScanner);
        char[] currentTokenSource = safeCharArrayAccessor(syntheticScanner, "getCurrentTokenSource", directTokenSlice);
        char[] currentTokenSourceString = currentTokenSource;
        char[] rawTokenSource = currentTokenSource;

        return new SyntheticToken(
                token,
                currentTokenSource,
                currentTokenSourceString,
                rawTokenSource,
                readCurrentStringLiteral(syntheticScanner),
                originalStart,
                originalEnd,
                cloneArray(readField(syntheticScanner, "lookBack")),
                readField(syntheticScanner, "nextToken"),
                readField(syntheticScanner, "scanContext")
        );
    }

    private static TemplateTranslation tryCreateTranslation(Object scanner) throws Exception {
        char[] source = (char[]) readField(scanner, "source");
        Integer rawCurrentPosition = (Integer) readField(scanner, "currentPosition");
        Integer rawEofPosition = (Integer) readField(scanner, "eofPosition");
        if (source == null || rawCurrentPosition == null || rawEofPosition == null) {
            return null;
        }
        int eofPosition = Math.min(rawEofPosition, source.length);
        int cursor = Math.max(0, rawCurrentPosition);
        while (cursor < eofPosition && isWhitespace(source[cursor])) {
            cursor++;
        }
        if (cursor >= eofPosition) {
            return null;
        }

        TemplateTranslation optionalAccess = tryCreateOptionalAccessTranslation(source, cursor, eofPosition);
        if (optionalAccess != null) {
            return optionalAccess;
        }

        TemplateTranslation elvisExpression = tryCreateElvisExpressionTranslation(source, cursor, eofPosition);
        if (elvisExpression != null) {
            return elvisExpression;
        }

        TemplateFormatter formatter = matchFormatter(source, cursor, eofPosition);
        if (formatter == null) {
            return null;
        }

        int lineEnd = cursor;
        while (lineEnd < eofPosition) {
            char current = source[lineEnd];
            if (current == '\r' || current == '\n') {
                break;
            }
            lineEnd++;
        }
        String rawTemplate = new String(source, cursor, lineEnd - cursor);
        String cacheKey = (isVscodeMode() ? "vscode" : "default") + '\u0000' + formatter.prefix() + '\u0000' + rawTemplate;
        CachedTemplateTranslation cached = TEMPLATE_TRANSLATION_CACHE.get(cacheKey);
        if (cached != null) {
            return new TemplateTranslation(
                    formatter.prefix(),
                    cached.translatedExpression,
                    cursor,
                    cursor + cached.endQuoteIndex,
                    cursor + cached.endQuoteIndex + 1
            );
        }
        TemplateModel model = formatter.build(rawTemplate);
        if (model == null || model.endQuoteIndex < 0 || model.endQuoteIndex >= rawTemplate.length()) {
            return null;
        }
        if (rawTemplate.charAt(model.endQuoteIndex) != '"') {
            return null;
        }

        String trimmedTemplate = rawTemplate.substring(0, model.endQuoteIndex + 1);
        String translated = isVscodeMode()
                ? toHostStringLiteral(trimmedTemplate, formatter.prefix())
                : formatter.printOut(model.list, trimmedTemplate);
        if (translated == null || translated.isEmpty()) {
            translated = "\"\"";
        }
        TEMPLATE_TRANSLATION_CACHE.put(cacheKey, new CachedTemplateTranslation(translated, model.endQuoteIndex));
        return new TemplateTranslation(
                formatter.prefix(),
                translated,
                cursor,
                cursor + model.endQuoteIndex,
                cursor + model.endQuoteIndex + 1
        );
    }

    private static TemplateTranslation tryCreateOptionalAccessTranslation(char[] source, int cursor, int eofPosition) {
        if (source == null || cursor < 0 || cursor + 1 >= eofPosition || cursor + 1 >= source.length) {
            return null;
        }
        if (source[cursor] != '?' || source[cursor + 1] != '.') {
            return null;
        }
        return new TemplateTranslation(
                "?.",
                ".",
                cursor,
                cursor + 1,
                cursor + 2
        );
    }

    private static TemplateTranslation tryCreateElvisExpressionTranslation(char[] source, int cursor, int eofPosition) {
        if (source == null || cursor < 0 || cursor + 1 >= eofPosition || cursor + 1 >= source.length) {
            return null;
        }
        if (source[cursor] != '?' || source[cursor + 1] != ':') {
            return null;
        }
        return new TemplateTranslation(
                "?:",
                "? zircon.BiOp.$$elvisExpr :",
                cursor,
                cursor + 1,
                cursor + 2
        );
    }

    private static TemplateFormatter matchFormatter(char[] source, int start, int eofPosition) {
        for (TemplateFormatter formatter : FORMATTERS) {
            String prefix = formatter.prefix();
            int quoteIndex = start + prefix.length();
            if (quoteIndex >= eofPosition || quoteIndex >= source.length) {
                continue;
            }
            if (source[quoteIndex] != '"') {
                continue;
            }
            if (startsWith(source, start, prefix)) {
                return formatter;
            }
        }
        return null;
    }

    private static Object createSyntheticScanner(Object scanner, char[] translatedSource) throws Exception {
        Class<?> scannerClass = Class.forName("org.eclipse.jdt.internal.compiler.parser.Scanner", false, scanner.getClass().getClassLoader());
        Constructor<?> constructor = scannerClass.getDeclaredConstructor(
                boolean.class,
                boolean.class,
                boolean.class,
                long.class,
                long.class,
                char[][].class,
                char[][].class,
                boolean.class,
                boolean.class
        );
        constructor.setAccessible(true);
        Object syntheticScanner = constructor.newInstance(
                readBooleanField(scanner, "tokenizeComments"),
                readBooleanField(scanner, "tokenizeWhiteSpace"),
                readBooleanField(scanner, "checkNonExternalizedStringLiterals"),
                readLongField(scanner, "sourceLevel"),
                readLongField(scanner, "complianceLevel"),
                readCharArrayArrayField(scanner, "taskTags"),
                readCharArrayArrayField(scanner, "taskPriorities"),
                readBooleanField(scanner, "isTaskCaseSensitive"),
                readBooleanField(scanner, "previewEnabled")
        );
        invokeSingleArg(syntheticScanner, "setSource", char[].class, translatedSource);
        return syntheticScanner;
    }

    private static void restoreScannerPosition(Object scanner, int position) throws Exception {
        writeField(scanner, "startPosition", position);
        writeField(scanner, "currentPosition", position);
        char[] source = (char[]) readField(scanner, "source");
        int eofPosition = readIntField(scanner, "eofPosition");
        char currentCharacter = position >= 0 && position < eofPosition && source != null && position < source.length
                ? source[position]
                : '\0';
        writeField(scanner, "currentCharacter", currentCharacter);
    }

    private static void copyScannerState(Object scanner, SyntheticToken token) throws Exception {
        writeField(scanner, "startPosition", token.originalStart);
        writeField(scanner, "currentPosition", token.originalEnd + 1);
        char[] source = (char[]) readField(scanner, "source");
        int eofPosition = readIntField(scanner, "eofPosition");
        char currentCharacter = token.originalEnd + 1 >= 0
                && token.originalEnd + 1 < eofPosition
                && source != null
                && token.originalEnd + 1 < source.length
                ? source[token.originalEnd + 1]
                : '\0';
        writeField(scanner, "currentCharacter", currentCharacter);
        if (token.lookBack != null) {
            writeField(scanner, "lookBack", token.lookBack);
        }
        if (token.nextToken != null) {
            writeField(scanner, "nextToken", token.nextToken);
        }
        if (token.scanContext != null) {
            writeField(scanner, "scanContext", token.scanContext);
        }
    }

    private static <T> T runWithoutInterception(TemplateCallable<T> callable) throws Exception {
        DISABLED_DEPTH.set(DISABLED_DEPTH.get() + 1);
        try {
            return callable.call();
        } finally {
            int depth = DISABLED_DEPTH.get() - 1;
            if (depth <= 0) {
                DISABLED_DEPTH.remove();
            } else {
                DISABLED_DEPTH.set(depth);
            }
        }
    }

    private static boolean isDisabled() {
        return DISABLED_DEPTH.get() > 0;
    }

    private static boolean startsWith(char[] source, int start, String prefix) {
        if (start < 0 || start + prefix.length() > source.length) {
            return false;
        }
        for (int index = 0; index < prefix.length(); index++) {
            if (source[start + index] != prefix.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWhitespace(char value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n' || value == '\f';
    }

    private static boolean isTerminalToken(Object token, String enumName) {
        return token instanceof Enum && enumName.equals(((Enum<?>) token).name());
    }

    private static String readCurrentStringLiteral(Object scanner) {
        try {
            Object literal = invokeNoArgs(scanner, "getCurrentStringLiteral");
            return literal instanceof String ? (String) literal : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static char[] safeCharArrayAccessor(Object scanner, String methodName, char[] fallback) {
        try {
            char[] value = asCharArray(invokeNoArgs(scanner, methodName));
            return value != null ? value : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static char[] readTokenSlice(Object scanner) {
        try {
            char[] source = (char[]) readField(scanner, "source");
            Integer start = (Integer) readField(scanner, "startPosition");
            Integer current = (Integer) readField(scanner, "currentPosition");
            if (source == null || start == null || current == null) {
                return null;
            }
            int begin = Math.max(0, start);
            int end = Math.max(begin, Math.min(source.length, current));
            if (end <= begin) {
                return null;
            }
            return Arrays.copyOfRange(source, begin, end);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static char[] asCharArray(Object value) {
        if (value instanceof char[]) {
            return Arrays.copyOf((char[]) value, ((char[]) value).length);
        }
        return null;
    }

    private static Object cloneArray(Object value) {
        if (!(value instanceof Object[])) {
            return value;
        }
        Object[] array = (Object[]) value;
        Object clone = Array.newInstance(value.getClass().getComponentType(), array.length);
        System.arraycopy(array, 0, clone, 0, array.length);
        return clone;
    }

    private static String toJavaStringLiteral(String templateText) {
        return escapeJavaString(materializeStringToken(templateText));
    }

    private static String toHostStringLiteral(String templateText, String prefix) {
        if (templateText == null || prefix == null) {
            return "\"\"";
        }
        int contentStart = prefix.length() + 1;
        int contentEnd = Math.max(contentStart, templateText.length() - 1);
        if (contentStart >= contentEnd) {
            return "\"\"";
        }
        return "\"" + escapeJavaString(templateText.substring(contentStart, contentEnd)) + "\"";
    }

    private static String materializeStringToken(String templateText) {
        if (templateText == null || templateText.isEmpty()) {
            return "";
        }
        return decodeTemplateLiteral(templateText.replace("\\$", "$"));
    }

    private static String decodeTemplateLiteral(String textChars) {
        StringBuilder builder = new StringBuilder();
        int index = -1;
        while (++index < textChars.length()) {
            char current = textChars.charAt(index);
            if (current != '\\') {
                builder.append(current);
                continue;
            }
            index++;
            if (index == textChars.length()) {
                throw new RuntimeException("Illegal template escape in " + textChars);
            }
            char escaped = textChars.charAt(index);
            if (escaped == '\\') {
                if (index + 1 != textChars.length() && textChars.charAt(index + 1) == '$') {
                    index++;
                    builder.append('$');
                } else {
                    builder.append('\\');
                }
                continue;
            }
            switch (escaped) {
                case '"':
                    builder.append('"');
                    break;
                case '\'':
                    builder.append('\'');
                    break;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                    int value = escaped - '0';
                    if (index + 1 != textChars.length()) {
                        int next = textChars.charAt(index + 1) - '0';
                        if (0 <= next && next <= 7) {
                            index++;
                            value = value * 8 + next;
                            if (index + 1 != textChars.length()) {
                                int next2 = textChars.charAt(index + 1) - '0';
                                if (next < 3 && 1 <= next && next2 <= 7) {
                                    index++;
                                    value = value * 8 + next2;
                                }
                            }
                        }
                    }
                    builder.append((char) value);
                    break;
                case 'b':
                    builder.append('\b');
                    break;
                case 'f':
                    builder.append('\f');
                    break;
                case 'n':
                    builder.append('\n');
                    break;
                case 'r':
                    builder.append('\r');
                    break;
                case 't':
                    builder.append('\t');
                    break;
                default:
                    builder.append(escaped);
                    break;
            }
        }
        return builder.toString();
    }

    private static String escapeJavaString(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                    break;
            }
        }
        return builder.toString();
    }

    private static boolean readBooleanField(Object target, String fieldName) throws Exception {
        Object value = readField(target, fieldName);
        return value instanceof Boolean && (Boolean) value;
    }

    private static int readIntField(Object target, String fieldName) throws Exception {
        Object value = readField(target, fieldName);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static long readLongField(Object target, String fieldName) throws Exception {
        Object value = readField(target, fieldName);
        return value instanceof Long ? (Long) value : 0L;
    }

    private static char[][] readCharArrayArrayField(Object target, String fieldName) throws Exception {
        Object value = readField(target, fieldName);
        return value instanceof char[][] ? (char[][]) value : null;
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        field.setAccessible(true);
        return field.get(target);
    }

    private static void writeField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return;
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object invokeNoArgs(Object target, String methodName) throws Exception {
        Method method = findMethod(target.getClass(), methodName);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
        }
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invokeSingleArg(Object target, String methodName, Class<?> parameterType, Object argument) throws Exception {
        Method method = findMethod(target.getClass(), methodName, parameterType);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
        }
        method.setAccessible(true);
        return method.invoke(target, argument);
    }

    private static Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private interface TemplateCallable<T> {
        T call() throws Exception;
    }

    private static final class TemplateScannerState {
        private final List<SyntheticToken> tokens;
        private final int resumePosition;
        private int index;
        private SyntheticToken current;

        private TemplateScannerState(List<SyntheticToken> tokens, int resumePosition) {
            this.tokens = tokens;
            this.resumePosition = resumePosition;
        }

        private boolean hasRemainingTokens() {
            return index < tokens.size();
        }

        private Object next(Object scanner) throws Exception {
            current = tokens.get(index++);
            copyScannerState(scanner, current);
            return current.token;
        }
    }

    private static final class SyntheticToken {
        private final Object token;
        private final char[] currentTokenSource;
        private final char[] currentTokenSourceString;
        private final char[] rawTokenSource;
        private final String currentStringLiteral;
        private final int originalStart;
        private final int originalEnd;
        private final Object lookBack;
        private final Object nextToken;
        private final Object scanContext;

        private SyntheticToken(
                Object token,
                char[] currentTokenSource,
                char[] currentTokenSourceString,
                char[] rawTokenSource,
                String currentStringLiteral,
                int originalStart,
                int originalEnd,
                Object lookBack,
                Object nextToken,
                Object scanContext
        ) {
            this.token = token;
            this.currentTokenSource = currentTokenSource;
            this.currentTokenSourceString = currentTokenSourceString;
            this.rawTokenSource = rawTokenSource;
            this.currentStringLiteral = currentStringLiteral;
            this.originalStart = originalStart;
            this.originalEnd = originalEnd;
            this.lookBack = lookBack;
            this.nextToken = nextToken;
            this.scanContext = scanContext;
        }
    }

    private static final class TemplateTranslation {
        private final String prefix;
        private final String translatedExpression;
        private final int originalStart;
        private final int originalEnd;
        private final int resumePosition;

        private TemplateTranslation(String prefix, String translatedExpression, int originalStart, int originalEnd, int resumePosition) {
            this.prefix = prefix;
            this.translatedExpression = translatedExpression;
            this.originalStart = originalStart;
            this.originalEnd = originalEnd;
            this.resumePosition = resumePosition;
        }
    }

    private static final class CachedTemplateTranslation {
        private final String translatedExpression;
        private final int endQuoteIndex;

        private CachedTemplateTranslation(String translatedExpression, int endQuoteIndex) {
            this.translatedExpression = translatedExpression;
            this.endQuoteIndex = endQuoteIndex;
        }
    }

    private static final class TemplateModel {
        private final List<StringRange> list = new ArrayList<>();
        private int endQuoteIndex = -1;
    }

    private static final class StringRange {
        private int codeStyle = -1;
        private int startIndex;
        private int endIndex;
        private int highlight;
        private String stringVal;

        private static StringRange of(int codeStyle, int startIndex, int endIndex) {
            StringRange range = new StringRange();
            range.codeStyle = codeStyle;
            range.startIndex = startIndex;
            range.endIndex = endIndex;
            return range;
        }

        private static StringRange of(int codeStyle, TemplateFormatter formatter, String text, int startIndex, int endIndex) {
            StringRange range = new StringRange();
            range.codeStyle = codeStyle;
            range.startIndex = startIndex;
            range.endIndex = endIndex;
            range.stringVal = formatter.codeTransfer(text.substring(startIndex, endIndex));
            return range;
        }

        private static StringRange code(TemplateFormatter formatter, String text, int startIndex, int endIndex) {
            StringRange range = new StringRange();
            range.codeStyle = 1;
            range.startIndex = startIndex;
            range.endIndex = endIndex;
            range.stringVal = formatter.codeTransfer(text.substring(startIndex, endIndex));
            return range;
        }

        private static StringRange string(TemplateFormatter formatter, String text, int startIndex, int endIndex) {
            StringRange range = new StringRange();
            range.codeStyle = 0;
            range.startIndex = startIndex;
            range.endIndex = endIndex;
            range.stringVal = formatter.stringTransfer(text.substring(startIndex, endIndex));
            return range;
        }

        private StringRange copy() {
            StringRange copy = new StringRange();
            copy.codeStyle = codeStyle;
            copy.startIndex = startIndex;
            copy.endIndex = endIndex;
            copy.highlight = highlight;
            copy.stringVal = stringVal;
            return copy;
        }
    }

    private interface TemplateFormatter {
        String prefix();

        String printOut(List<StringRange> build, String text);

        TemplateModel build(String text);

        String stringTransfer(String text);

        default String codeTransfer(String text) {
            return text.replaceAll("\\\\?([a-z0-9\"']{1})", "$1").replace("\\\\", "\\");
        }
    }

    private static class SStringFormatter implements TemplateFormatter {

        @Override
        public String prefix() {
            return "$";
        }

        @Override
        public String printOut(List<StringRange> build, String text) {
            if (build.isEmpty()) {
                return "\"\"";
            }
            StringBuilder builder = new StringBuilder("(");
            for (int index = 0; index < build.size(); index++) {
                StringRange range = build.get(index);
                if (range.codeStyle == 1) {
                    if (index == 0) {
                        builder.append("String.valueOf");
                    } else {
                        builder.append("+");
                    }
                    builder.append("(").append(range.stringVal).append(")");
                } else if (range.codeStyle == 0) {
                    if (index > 0) {
                        builder.append("+");
                    }
                    builder.append("\"").append(toJavaStringLiteral(range.stringVal)).append("\"");
                }
            }
            builder.append(")");
            return builder.toString();
        }

        @Override
        public TemplateModel build(String text) {
            TemplateModel model = new TemplateModel();
            List<StringRange> list = model.list;
            int startIndex = prefix().length() + 1;
            int selectMode = -1;
            int parenthesisCount = 0;
            for (int cursor = startIndex; cursor < text.length() - 1; cursor++) {
                char current = text.charAt(cursor);
                if (text.charAt(cursor - 1) == '\\' && text.charAt(cursor - 2) != '\\') {
                    continue;
                }
                if (selectMode == 2) {
                    if (current == '{') {
                        parenthesisCount++;
                    }
                    if (current == '}') {
                        parenthesisCount--;
                        if (parenthesisCount == 0) {
                            if (cursor - startIndex > 0) {
                                list.add(StringRange.code(this, text, startIndex, cursor));
                            }
                            startIndex = cursor + 1;
                            selectMode = -1;
                        }
                    }
                    continue;
                }
                if (selectMode == 1) {
                    if (String.valueOf(current).matches(parenthesisCount > 0 ? "[^)]{1}" : "[A-Za-z0-9_\\u4e00-\\u9fa5.$]{1}")) {
                        continue;
                    }
                    if (current == '(') {
                        if (text.substring(cursor).matches("^\\([^)]*\\).*")) {
                            parenthesisCount++;
                            continue;
                        }
                    } else if (current == ')') {
                        if (parenthesisCount > 0) {
                            if (text.substring(cursor).matches("^\\)\\.[A-Za-z_\\u4e00-\\u9fa5$]+.*")) {
                                parenthesisCount--;
                                continue;
                            }
                            list.add(StringRange.code(this, text, startIndex, cursor + 1));
                            startIndex = cursor + 1;
                            selectMode = -1;
                            continue;
                        }
                    }
                    list.add(StringRange.code(this, text, startIndex, cursor));
                    selectMode = -1;
                    startIndex = cursor;
                    cursor--;
                    continue;
                }
                if (current == '$'
                        && !(text.charAt(cursor - 1) == '\\' && text.charAt(cursor - 2) == '\\')
                        && String.valueOf(text.charAt(cursor + 1)).matches("[A-Za-z_\\u4e00-\\u9fa5{$]{1}")) {
                    if (cursor - startIndex != 0) {
                        list.add(StringRange.string(this, text, startIndex, cursor));
                    }
                    if (text.charAt(cursor + 1) == '{') {
                        startIndex = cursor + 2;
                        selectMode = 2;
                        parenthesisCount = 0;
                    } else {
                        startIndex = cursor + 1;
                        selectMode = 1;
                        parenthesisCount = 0;
                    }
                }
                if (selectMode == -1 && current == '"') {
                    if (cursor > startIndex) {
                        list.add(StringRange.string(this, text, startIndex, cursor));
                    }
                    model.endQuoteIndex = cursor;
                    return model;
                }
            }
            if (text.length() - 1 > startIndex) {
                if (selectMode > 0) {
                    list.add(StringRange.code(this, text, startIndex, text.length() - 1));
                } else {
                    list.add(StringRange.string(this, text, startIndex, text.length() - 1));
                }
            }
            model.endQuoteIndex = text.length() - 1;
            return model;
        }

        @Override
        public String stringTransfer(String str) {
            return str.replace("\\$", "$");
        }
    }

    private static final class STRStringFormatter extends SStringFormatter {

        @Override
        public String prefix() {
            return "STR.";
        }

        @Override
        public TemplateModel build(String text) {
            TemplateModel model = new TemplateModel();
            List<StringRange> list = model.list;
            int startIndex = prefix().length() + 1;
            int selectMode = -1;
            int parenthesisCount = 0;
            for (int cursor = startIndex; cursor < text.length() - 1; cursor++) {
                char current = text.charAt(cursor);
                if (text.charAt(cursor) != '{' && text.charAt(cursor - 1) == '\\' && text.charAt(cursor - 2) != '\\') {
                    continue;
                }
                if (selectMode == 2) {
                    if (current == '{') {
                        parenthesisCount++;
                    }
                    if (current == '}') {
                        parenthesisCount--;
                        if (parenthesisCount == 0) {
                            if (cursor - startIndex > 0) {
                                list.add(StringRange.code(this, text, startIndex, cursor));
                            }
                            startIndex = cursor + 1;
                            selectMode = -1;
                        }
                    }
                    continue;
                }
                if (current == '\\'
                        && !(text.charAt(cursor - 1) == '\\' && text.charAt(cursor - 2) == '\\')
                        && String.valueOf(text.charAt(cursor + 1)).equals("{")) {
                    if (cursor - startIndex != 0) {
                        list.add(StringRange.string(this, text, startIndex, cursor));
                    }
                    startIndex = cursor + 2;
                    selectMode = 2;
                    parenthesisCount = 0;
                }
                if (selectMode == -1 && current == '"') {
                    if (cursor > startIndex) {
                        list.add(StringRange.string(this, text, startIndex, cursor));
                    }
                    model.endQuoteIndex = cursor;
                    return model;
                }
            }
            if (text.length() - 1 > startIndex) {
                if (selectMode > 0) {
                    list.add(StringRange.code(this, text, startIndex, text.length() - 1));
                } else {
                    list.add(StringRange.string(this, text, startIndex, text.length() - 1));
                }
            }
            model.endQuoteIndex = text.length() - 1;
            return model;
        }

        @Override
        public String stringTransfer(String str) {
            return str;
        }
    }

    private static final class FStringFormatter implements TemplateFormatter {

        @Override
        public String prefix() {
            return "f";
        }

        @Override
        public String printOut(List<StringRange> build, String text) {
            StringBuilder builder = new StringBuilder();
            builder.append("String.format(\"");
            builder.append(toJavaStringLiteral(mapToFormatString(text, build)));
            builder.append("\"");
            for (StringRange range : build) {
                if (range.codeStyle == 1) {
                    builder.append(",").append(range.stringVal);
                }
            }
            builder.append(")");
            return builder.toString();
        }

        private static String mapToFormatString(String text, List<StringRange> ranges) {
            StringRange formatRange = null;
            StringBuilder builder = new StringBuilder();
            for (StringRange range : ranges) {
                if (range.codeStyle == 2) {
                    formatRange = range;
                } else if (range.codeStyle == 0) {
                    builder.append(range.stringVal);
                } else if (range.codeStyle == 1) {
                    if (formatRange == null) {
                        builder.append("%s");
                    } else {
                        builder.append(text, formatRange.startIndex, formatRange.endIndex);
                        formatRange = null;
                    }
                }
            }
            return builder.toString();
        }

        @Override
        public TemplateModel build(String text) {
            TemplateModel model = new TemplateModel();
            List<StringRange> list = model.list;
            int startIndex = prefix().length() + 1;
            int selectMode = -1;
            int parenthesisCount = 0;
            for (int cursor = startIndex; cursor < text.length() - 1; cursor++) {
                char current = text.charAt(cursor);
                if (text.charAt(cursor - 1) == '\\' && text.charAt(cursor - 2) != '\\') {
                    continue;
                }
                if (selectMode == 2) {
                    if (current == '{') {
                        parenthesisCount++;
                    }
                    if (current == '}') {
                        parenthesisCount--;
                        if (parenthesisCount == 0) {
                            if (cursor - startIndex > 0) {
                                String substring = text.substring(startIndex, cursor);
                                if (substring.startsWith("%")) {
                                    int splitChar = substring.indexOf(":");
                                    if (splitChar == -1) {
                                        list.add(StringRange.code(this, text, startIndex, cursor));
                                    } else {
                                        list.add(StringRange.of(2, startIndex, startIndex + splitChar));
                                        list.add(StringRange.code(this, text, startIndex + splitChar + 1, cursor));
                                    }
                                } else {
                                    list.add(StringRange.code(this, text, startIndex, cursor));
                                }
                            }
                            startIndex = cursor + 1;
                            selectMode = -1;
                        }
                    }
                    continue;
                }
                if (selectMode == 1) {
                    if (String.valueOf(current).matches(parenthesisCount > 0 ? "[^)]{1}" : "[A-Za-z0-9_\\u4e00-\\u9fa5.$]{1}")) {
                        continue;
                    }
                    if (current == '(') {
                        if (text.substring(cursor).matches("^\\([^)]*\\).*")) {
                            parenthesisCount++;
                            continue;
                        }
                    } else if (current == ')') {
                        if (parenthesisCount > 0) {
                            if (text.substring(cursor).matches("^\\)\\.[A-Za-z_\\u4e00-\\u9fa5$]+.*")) {
                                parenthesisCount--;
                                continue;
                            }
                            list.add(StringRange.code(this, text, startIndex, cursor + 1));
                            startIndex = cursor + 1;
                            selectMode = -1;
                            continue;
                        }
                    }
                    list.add(StringRange.code(this, text, startIndex, cursor));
                    selectMode = -1;
                    startIndex = cursor;
                    cursor--;
                    continue;
                }
                if (current == '$'
                        && !(text.charAt(cursor - 1) == '\\' && text.charAt(cursor - 2) == '\\')
                        && String.valueOf(text.charAt(cursor + 1)).matches("[A-Za-z_\\u4e00-\\u9fa5{$]{1}")) {
                    if (cursor - startIndex != 0) {
                        list.add(StringRange.string(this, text, startIndex, cursor));
                    }
                    if (text.charAt(cursor + 1) == '{') {
                        startIndex = cursor + 2;
                        selectMode = 2;
                        parenthesisCount = 0;
                    } else {
                        startIndex = cursor + 1;
                        selectMode = 1;
                        parenthesisCount = 0;
                    }
                }
                if (selectMode == -1 && current == '"') {
                    if (cursor > startIndex) {
                        list.add(StringRange.string(this, text, startIndex, cursor));
                    }
                    model.endQuoteIndex = cursor;
                    return model;
                }
            }
            if (text.length() - 1 > startIndex) {
                if (selectMode > 0) {
                    list.add(StringRange.code(this, text, startIndex, text.length() - 1));
                } else {
                    list.add(StringRange.string(this, text, startIndex, text.length() - 1));
                }
            }
            model.endQuoteIndex = text.length() - 1;
            return model;
        }

        @Override
        public String stringTransfer(String str) {
            return str.replace("%", "%%").replace("\\$", "$");
        }
    }

    private static final class JStringFormatter implements TemplateFormatter {

        @Override
        public String prefix() {
            return "j";
        }

        @Override
        public String printOut(List<StringRange> build, String text) {
            List<StringRange> stringRanges = new ArrayList<>();
            for (StringRange range : build) {
                if (stringRanges.isEmpty() || range.codeStyle == 1) {
                    stringRanges.add(range.copy());
                    continue;
                }
                StringRange last = stringRanges.get(stringRanges.size() - 1);
                if (last.codeStyle != 1) {
                    last.endIndex = range.endIndex;
                    if (range.codeStyle == 0 && range.highlight == 1 && !range.stringVal.startsWith("\"")) {
                        last.stringVal = last.stringVal + "\\\"" + range.stringVal + "\\\"";
                    } else if (range.codeStyle == 0 && range.highlight == 1 && range.stringVal.startsWith("\"")) {
                        last.stringVal = last.stringVal + range.stringVal.replace("\"", "\\\"");
                    } else if (range.codeStyle == 0 && range.highlight == 2) {
                        last.stringVal = last.stringVal + range.stringVal.replace("\"", "\\\"");
                    } else {
                        last.stringVal = last.stringVal + range.stringVal;
                    }
                    last.codeStyle = 0;
                } else {
                    stringRanges.add(range.copy());
                }
            }
            if (stringRanges.isEmpty()) {
                return "\"\"";
            }

            StringBuilder builder = new StringBuilder("(");
            for (int index = 0; index < stringRanges.size(); index++) {
                StringRange range = stringRanges.get(index);
                if (range.codeStyle == 1) {
                    if (index == 0) {
                        builder.append("String.valueOf");
                    } else {
                        builder.append("+");
                    }
                    builder.append("(zircon.BiOp.jString(").append(range.stringVal).append("))");
                } else if (range.codeStyle == 0) {
                    if (index > 0) {
                        builder.append("+");
                    }
                    builder.append("\"").append(toJavaStringLiteral(range.stringVal)).append("\"");
                }
            }
            builder.append(")");
            return builder.toString();
        }

        @Override
        public TemplateModel build(String text) {
            TemplateModel model = new TemplateModel();
            if (getBooleanProperty("zircon.vscode", false)) {
                int endQuoteIndex = text.lastIndexOf('"');
                if (endQuoteIndex > 1) {
                    model.endQuoteIndex = endQuoteIndex;
                    model.list.add(StringRange.of(0, this, text, 2, endQuoteIndex));
                }
                return model;
            }
            Collections.addAll(model.list, parseJson(this, text));
            if (!model.list.isEmpty()) {
                model.endQuoteIndex = model.list.get(model.list.size() - 1).endIndex;
            }
            return model;
        }

        @Override
        public String stringTransfer(String str) {
            return str.replace("%", "%%").replace("\\$", "$");
        }

        private static StringRange[] parseJson(TemplateFormatter formatter, String jsonStr) {
            if (jsonStr == null || jsonStr.isEmpty()) {
                return new StringRange[0];
            }

            List<StringRange> ranges = new ArrayList<>();
            int len = jsonStr.lastIndexOf("\"");
            int cursor = 2;
            char[] chars = jsonStr.toCharArray();
            Stack<Character> structureStack = new Stack<>();

            outer:
            while (cursor < len) {
                char current = chars[cursor];
                switch (current) {
                    case ' ':
                    case '\t':
                    case '\n':
                    case '\r':
                    case '\f':
                        break;
                    case '{':
                    case '[':
                        ranges.add(StringRange.of(0, formatter, jsonStr, cursor, cursor + 1));
                        structureStack.push(current);
                        break;
                    case ']':
                    case '}':
                        ranges.add(StringRange.of(0, formatter, jsonStr, cursor, cursor + 1));
                        structureStack.pop();
                        if (structureStack.isEmpty()) {
                            int start = cursor;
                            while (cursor < len && chars[cursor] != '"') {
                                cursor++;
                            }
                            ranges.add(StringRange.of(-1, start + 1, cursor));
                            break outer;
                        }
                        break;
                    case ',':
                    case ':':
                        ranges.add(StringRange.of(0, formatter, jsonStr, cursor, cursor + 1));
                        break;
                    default:
                        int start = cursor;
                        boolean scanCode = false;
                        if (structureStack.peek() == '{') {
                            if (Objects.equals(ranges.get(ranges.size() - 1).stringVal, ":")) {
                                scanCode = true;
                            } else {
                                while (cursor < len) {
                                    switch (chars[cursor]) {
                                        case ' ':
                                        case '\t':
                                        case '\n':
                                        case '\r':
                                        case '\f':
                                        case ':':
                                        case ',':
                                            StringRange element = StringRange.of(0, formatter, jsonStr, start, cursor);
                                            element.highlight = 1;
                                            ranges.add(element);
                                            cursor--;
                                            continue outer;
                                        default:
                                            cursor++;
                                            break;
                                    }
                                }
                                StringRange element = StringRange.of(0, formatter, jsonStr, start, cursor);
                                element.highlight = 1;
                                ranges.add(element);
                                cursor--;
                                break;
                            }
                        } else {
                            scanCode = true;
                        }
                        if (scanCode) {
                            Stack<Character> valueStack = new Stack<>();
                            while (cursor < len) {
                                char value = chars[cursor];
                                switch (value) {
                                    case '\'':
                                        cursor = skipQuoted(chars, cursor, len, '\'');
                                        break;
                                    case '"':
                                        cursor = skipQuoted(chars, cursor, len, '"');
                                        break;
                                    case '{':
                                    case '[':
                                    case '(':
                                        valueStack.push(value);
                                        break;
                                    case '}':
                                        if (!valueStack.isEmpty() && valueStack.peek() == '{') {
                                            valueStack.pop();
                                            break;
                                        }
                                        if (valueStack.isEmpty()) {
                                            StringRange code = StringRange.code(formatter, jsonStr, start, cursor);
                                            code.highlight = 2;
                                            ranges.add(code);
                                            cursor--;
                                            break outer;
                                        }
                                        break;
                                    case ']':
                                        if (!valueStack.isEmpty() && valueStack.peek() == '[') {
                                            valueStack.pop();
                                        }
                                        break;
                                    case ')':
                                        if (!valueStack.isEmpty() && valueStack.peek() == '(') {
                                            valueStack.pop();
                                        }
                                        break;
                                    case ',':
                                        if (valueStack.isEmpty()) {
                                            StringRange code = StringRange.code(formatter, jsonStr, start, cursor);
                                            code.highlight = 2;
                                            ranges.add(code);
                                            cursor--;
                                            continue outer;
                                        }
                                        break;
                                    default:
                                        break;
                                }
                                cursor++;
                            }
                            StringRange code = StringRange.code(formatter, jsonStr, start, cursor);
                            code.highlight = 2;
                            ranges.add(code);
                            cursor--;
                        }
                        break;
                }
                cursor++;
            }
            return ranges.toArray(new StringRange[0]);
        }

        private static int skipQuoted(char[] chars, int start, int limit, char quote) {
            int cursor = start + 1;
            while (cursor < limit) {
                char current = chars[cursor];
                if (current == '\\') {
                    cursor += 2;
                    continue;
                }
                if (current == quote) {
                    return cursor;
                }
                cursor++;
            }
            return limit;
        }
    }

    private static final java.nio.file.Path LOG_PATH = resolveLogPath();

    public static void log(String msg) {
        try {
            java.nio.file.Files.write(
                    LOG_PATH,
                    (msg + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {
        }
    }

    public static boolean isDebugEnabled() {
        return getBooleanProperty("zircon.debug", false);
    }

    public static boolean isTraceEnabled() {
        return getBooleanProperty("zircon.trace", false);
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getProperty(key, Boolean.toString(defaultValue)));
    }

    public static String getProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        if (key != null && key.startsWith("zircon.")) {
            value = System.getProperty("Z" + key.substring(1));
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return defaultValue;
    }

    public static String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        java.io.StringWriter buffer = new java.io.StringWriter();
        java.io.PrintWriter writer = new java.io.PrintWriter(buffer);
        throwable.printStackTrace(writer);
        writer.flush();
        return buffer.toString();
    }

    public static java.nio.file.Path resolveLogPath() {
        String explicit = getProperty("zircon.log.path", "").trim();
        if (!explicit.isEmpty()) {
            return java.nio.file.Paths.get(explicit);
        }
        String tempDir = System.getProperty("java.io.tmpdir", ".");
        return java.nio.file.Paths.get(tempDir, "zircon_vscode_agent.log");
    }
}
