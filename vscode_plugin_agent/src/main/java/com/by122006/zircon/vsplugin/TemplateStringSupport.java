package com.by122006.zircon.vsplugin;

import com.sun.tools.javac.parser.TemplateStringSplitter;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> FIELD_MISSES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> METHOD_MISSES = new ConcurrentHashMap<>();
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
        Object syntheticScanner = createSyntheticScanner(scanner, translation);
        List<SyntheticToken> tokens = new ArrayList<>();
        boolean[] completionCaptured = {false};
        runWithoutInterception(() -> {
            int previousPosition = -1;
            int stalledCount = 0;
            int tokenLimit = Math.max(64, translation.translatedExpression.length() * 4);
            while (true) {
                Object token = invokeNoArgs(syntheticScanner, "getNextToken");
                if (isTerminalToken(token, "TokenNameEOF")) {
                    return null;
                }
                SyntheticToken snapshot = snapshotSyntheticToken(
                        syntheticScanner,
                        token,
                        translation,
                        !completionCaptured[0]
                );
                tokens.add(snapshot);
                if (snapshot.completionIdentifier != null) {
                    completionCaptured[0] = true;
                }
                int currentPosition = readIntField(syntheticScanner, "currentPosition");
                stalledCount = currentPosition <= previousPosition ? stalledCount + 1 : 0;
                previousPosition = currentPosition;
                if (stalledCount >= 2 || tokens.size() >= tokenLimit) {
                    return null;
                }
            }
        });
        if (isTraceEnabled() && scanner.getClass().getName().endsWith("CompletionScanner")) {
            StringBuilder trace = new StringBuilder("[TemplateScanner] completionTokens");
            for (SyntheticToken token : tokens) {
                trace.append(' ').append(token.token)
                        .append('[').append(token.originalStart).append('-').append(token.originalEnd).append(']')
                        .append('=').append(token.currentTokenSource == null ? "" : new String(token.currentTokenSource));
                if (token.completionIdentifier != null) {
                    trace.append("{assist=").append(new String(token.completionIdentifier))
                            .append('@').append(token.completedIdentifierStart).append('-').append(token.completedIdentifierEnd)
                            .append('}');
                }
            }
            log(trace.toString());
        }
        return tokens.isEmpty() ? null : new TemplateScannerState(tokens, translation.resumePosition);
    }

    private static SyntheticToken snapshotSyntheticToken(
            Object syntheticScanner,
            Object token,
            TemplateTranslation translation,
            boolean allowCompletion
    ) throws Exception {
        int syntheticStart = ((Integer) invokeNoArgs(syntheticScanner, "getCurrentTokenStartPosition"));
        int syntheticEnd = ((Integer) invokeNoArgs(syntheticScanner, "getCurrentTokenEndPosition"));
        int originalStart = translation.toOriginalPosition(syntheticStart);
        int originalEnd = translation.toOriginalPosition(syntheticEnd);
        if (originalEnd < originalStart) {
            originalEnd = originalStart;
        }
        char[] directTokenSlice = readTokenSlice(syntheticScanner);
        char[] currentTokenSource = safeCharArrayAccessor(syntheticScanner, "getCurrentTokenSource", directTokenSlice);
        if (allowCompletion && isTerminalToken(token, "TokenNameIdentifier")) {
            currentTokenSource = safeCharArrayAccessor(syntheticScanner, "getCurrentIdentifierSource", currentTokenSource);
        }
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
                readField(syntheticScanner, "scanContext"),
                allowCompletion ? cloneCharArray(readOptionalField(syntheticScanner, "completionIdentifier")) : null,
                allowCompletion ? mapOptionalPosition(syntheticScanner, "completedIdentifierStart", translation) : null,
                allowCompletion ? mapOptionalPosition(syntheticScanner, "completedIdentifierEnd", translation) : null,
                allowCompletion ? mapOptionalPosition(syntheticScanner, "endOfEmptyToken", translation) : null,
                cloneCharArray(readOptionalField(syntheticScanner, "selectionIdentifier"))
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
        TemplateModel model = formatter.build(rawTemplate);
        if (model == null || model.endQuoteIndex < 0 || model.endQuoteIndex >= rawTemplate.length()) {
            return null;
        }
        if (rawTemplate.charAt(model.endQuoteIndex) != '"') {
            return null;
        }

        int originalEnd = cursor + model.endQuoteIndex;
        boolean activeCompletionTemplate = isActiveCompletionTemplate(scanner, cursor, originalEnd);
        boolean stabilizeIncompleteAccess = !activeCompletionTemplate;
        String cacheKey = (isVscodeMode() ? "vscode" : "default")
                + '\u0000' + formatter.prefix()
                + '\u0000' + (stabilizeIncompleteAccess ? "stable" : "completion")
                + '\u0000' + rawTemplate;
        CachedTemplateTranslation cached = TEMPLATE_TRANSLATION_CACHE.get(cacheKey);
        if (cached != null) {
            return new TemplateTranslation(
                    formatter.prefix(),
                    cached.translatedExpression,
                    cached.generatedToTemplateOffset,
                    cursor,
                    cursor + cached.endQuoteIndex,
                    cursor + cached.endQuoteIndex + 1
            );
        }

        String trimmedTemplate = rawTemplate.substring(0, model.endQuoteIndex + 1);
        MappedExpression mappedExpression = formatter.translate(model.list, trimmedTemplate);
        if (stabilizeIncompleteAccess) {
            mappedExpression = stabilizeIncompleteMemberAccesses(mappedExpression);
        } else {
            mappedExpression = completeTerminalMemberAccess(mappedExpression);
        }
        String translated = mappedExpression == null ? null : mappedExpression.text;
        if (translated == null || translated.isEmpty()) {
            mappedExpression = MappedExpression.synthetic("\"\"", 0, Math.max(0, trimmedTemplate.length() - 1));
            translated = mappedExpression.text;
        }
        TEMPLATE_TRANSLATION_CACHE.put(cacheKey, new CachedTemplateTranslation(
                translated,
                mappedExpression.generatedToTemplateOffset,
                model.endQuoteIndex
        ));
        return new TemplateTranslation(
                formatter.prefix(),
                translated,
                mappedExpression.generatedToTemplateOffset,
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
                new int[]{0},
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
                createLinearOffsetMap("? zircon.BiOp.$$elvisExpr :".length(), 0, 1),
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

    private static Object createSyntheticScanner(Object scanner, TemplateTranslation translation) throws Exception {
        char[] translatedSource = translation.translatedExpression.toCharArray();
        Class<?> scannerClass = scanner.getClass();
        String scannerClassName = scannerClass.getName();
        Object syntheticScanner;
        if (scannerClassName.endsWith("CompletionScanner")) {
            int cursorLocation = readIntField(scanner, "cursorLocation");
            boolean activeCompletion = translation.containsOriginalPosition(cursorLocation);
            int syntheticCursorLocation = -1;
            if (!activeCompletion) {
                // A CompletionScanner injects an assist token even at cursor -1.
                // Non-active templates must therefore use a plain Scanner or an
                // earlier incomplete template will hijack the whole request.
                syntheticScanner = createBaseSyntheticScanner(scanner, translatedSource);
            } else {
                Constructor<?> constructor = scannerClass.getDeclaredConstructor(long.class, boolean.class);
                constructor.setAccessible(true);
                syntheticScanner = constructor.newInstance(
                        readLongField(scanner, "sourceLevel"),
                        readBooleanField(scanner, "previewEnabled")
                );
                copyFieldIfPresent(scanner, syntheticScanner, "completionIdentifier");
                syntheticCursorLocation = translation.toSyntheticPosition(cursorLocation);
                writeField(syntheticScanner, "cursorLocation", syntheticCursorLocation);
            }
            if (isTraceEnabled()) {
                log("[TemplateScanner] completionCursor original=" + cursorLocation
                        + ", synthetic=" + syntheticCursorLocation
                        + ", template=" + translation.originalStart + "-" + translation.originalEnd);
            }
        } else if (scannerClassName.endsWith("SelectionScanner")) {
            int selectionStart = readIntField(scanner, "selectionStart");
            int selectionEnd = readIntField(scanner, "selectionEnd");
            if (!translation.intersectsOriginalRange(selectionStart, selectionEnd)) {
                syntheticScanner = createBaseSyntheticScanner(scanner, translatedSource);
            } else {
                Constructor<?> constructor = scannerClass.getDeclaredConstructor(long.class, boolean.class);
                constructor.setAccessible(true);
                syntheticScanner = constructor.newInstance(
                        readLongField(scanner, "sourceLevel"),
                        readBooleanField(scanner, "previewEnabled")
                );
                copyFieldIfPresent(scanner, syntheticScanner, "selectionIdentifier");
                writeField(syntheticScanner, "selectionStart", translation.toSyntheticPosition(selectionStart));
                writeField(syntheticScanner, "selectionEnd", translation.toSyntheticPosition(selectionEnd));
            }
        } else {
            syntheticScanner = createBaseSyntheticScanner(scanner, translatedSource);
        }
        invokeSingleArg(syntheticScanner, "setSource", char[].class, translatedSource);
        return syntheticScanner;
    }

    private static Object createBaseSyntheticScanner(Object scanner, char[] translatedSource) throws Exception {
        Class<?> baseScannerClass = Class.forName("org.eclipse.jdt.internal.compiler.parser.Scanner", false, scanner.getClass().getClassLoader());
        Constructor<?> constructor = baseScannerClass.getDeclaredConstructor(
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
        if (token.completionIdentifier != null) {
            writeField(scanner, "completionIdentifier", token.completionIdentifier);
        }
        if (token.completedIdentifierStart != null) {
            writeField(scanner, "completedIdentifierStart", token.completedIdentifierStart);
        }
        if (token.completedIdentifierEnd != null) {
            writeField(scanner, "completedIdentifierEnd", token.completedIdentifierEnd);
        }
        if (token.endOfEmptyToken != null) {
            writeField(scanner, "endOfEmptyToken", token.endOfEmptyToken);
        }
        if (token.selectionIdentifier != null) {
            writeField(scanner, "selectionIdentifier", token.selectionIdentifier);
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

    private static int[] createLinearOffsetMap(int length, int startOffset, int endOffset) {
        if (length <= 0) {
            return new int[0];
        }
        int safeStart = Math.max(0, startOffset);
        int safeEnd = Math.max(safeStart, endOffset);
        int[] mapping = new int[length];
        if (length == 1) {
            mapping[0] = safeStart;
            return mapping;
        }
        for (int index = 0; index < length; index++) {
            mapping[index] = safeStart + (int) (((long) (safeEnd - safeStart) * index) / (length - 1));
        }
        return mapping;
    }

    /**
     * Builds the smallest Java expression JDT needs for type analysis. Literal text is
     * represented by one empty String and every embedded Java expression is retained.
     * Besides avoiding irrelevant formatter overloads during completion, this lets the
     * generated punctuation occupy the template's own prefix, ${ and } characters so
     * token source positions remain strictly ordered.
     */
    private static MappedExpression translateAsStringConcatenation(
            List<StringRange> ranges,
            String text,
            int prefixLength
    ) {
        MappedTextBuilder builder = new MappedTextBuilder();
        builder.appendSynthetic("(", 0, 0);
        builder.appendSynthetic("\"\"", prefixLength, prefixLength);
        for (StringRange range : ranges) {
            if (range.codeStyle != 1) {
                continue;
            }
            int plusOffset = Math.max(prefixLength + 1, range.startIndex - 2);
            int openOffset = Math.max(plusOffset, range.startIndex - 1);
            builder.appendSynthetic("+", plusOffset, plusOffset);
            builder.appendSynthetic("(", openOffset, openOffset);
            builder.appendOriginal(range.stringVal, range.startIndex, range.endIndex);
            builder.appendSynthetic(")", range.endIndex, range.endIndex);
        }
        int endOffset = Math.max(0, text.length() - 1);
        builder.appendSynthetic(")", endOffset, endOffset);
        return builder.build();
    }

    private static boolean isActiveCompletionTemplate(Object scanner, int originalStart, int originalEnd) {
        if (scanner == null || !scanner.getClass().getName().endsWith("CompletionScanner")) {
            return false;
        }
        try {
            int cursorLocation = readIntField(scanner, "cursorLocation");
            return cursorLocation >= originalStart && cursorLocation <= originalEnd;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Keeps an unfinished member access local to its template during ordinary
     * parsing. The active completion template is intentionally left untouched
     * so JDT can still complete directly after the dot.
     */
    private static MappedExpression stabilizeIncompleteMemberAccesses(MappedExpression expression) {
        if (expression == null || expression.text == null || expression.text.isEmpty()) {
            return expression;
        }
        String text = expression.text;
        String placeholder = "__zirconIncompleteMember";
        StringBuilder stabilized = new StringBuilder(text.length() + placeholder.length());
        List<Integer> offsets = new ArrayList<>();
        int state = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
            int offset = expression.generatedToTemplateOffset.length == 0
                    ? 0
                    : expression.generatedToTemplateOffset[Math.min(index, expression.generatedToTemplateOffset.length - 1)];
            stabilized.append(current);
            offsets.add(offset);

            if (state == 1) {
                if (current == '\\' && index + 1 < text.length()) {
                    index++;
                    stabilized.append(text.charAt(index));
                    offsets.add(expression.generatedToTemplateOffset[Math.min(index, expression.generatedToTemplateOffset.length - 1)]);
                } else if (current == '\'') {
                    state = 0;
                }
                continue;
            }
            if (state == 2) {
                if (current == '\\' && index + 1 < text.length()) {
                    index++;
                    stabilized.append(text.charAt(index));
                    offsets.add(expression.generatedToTemplateOffset[Math.min(index, expression.generatedToTemplateOffset.length - 1)]);
                } else if (current == '"') {
                    state = 0;
                }
                continue;
            }
            if (state == 3) {
                if (current == '\r' || current == '\n') {
                    state = 0;
                }
                continue;
            }
            if (state == 4) {
                if (current == '*' && next == '/') {
                    index++;
                    stabilized.append('/');
                    offsets.add(expression.generatedToTemplateOffset[Math.min(index, expression.generatedToTemplateOffset.length - 1)]);
                    state = 0;
                }
                continue;
            }
            if (current == '\'') {
                state = 1;
            } else if (current == '"') {
                state = 2;
            } else if (current == '/' && next == '/') {
                state = 3;
            } else if (current == '/' && next == '*') {
                state = 4;
            } else if (current == '.' && isFollowedByGeneratedClosingParenthesis(text, index + 1)) {
                for (int placeholderIndex = 0; placeholderIndex < placeholder.length(); placeholderIndex++) {
                    stabilized.append(placeholder.charAt(placeholderIndex));
                    offsets.add(offset);
                }
            }
        }
        int[] mapping = new int[offsets.size()];
        for (int index = 0; index < offsets.size(); index++) {
            mapping[index] = offsets.get(index);
        }
        return new MappedExpression(stabilized.toString(), mapping);
    }

    /**
     * CompletionParser does not dispatch a terminal {@code receiver.prefix}
     * nested in the synthetic string-concatenation parentheses as a member
     * completion when no Java field has that name. Supplying an empty argument
     * list makes it a CompletionOnMessageSendName, which is JDT's native method
     * completion path. The inserted characters map to the original identifier
     * end and never reach the source document.
     */
    private static MappedExpression completeTerminalMemberAccess(MappedExpression expression) {
        if (expression == null || expression.text == null || expression.text.isEmpty()) {
            return expression;
        }
        String text = expression.text;
        int closingStart = text.length();
        while (closingStart > 0) {
            char current = text.charAt(closingStart - 1);
            if (Character.isWhitespace(current) || current == ')') {
                closingStart--;
                continue;
            }
            break;
        }
        int identifierEnd = closingStart;
        int identifierStart = identifierEnd;
        while (identifierStart > 0 && Character.isJavaIdentifierPart(text.charAt(identifierStart - 1))) {
            identifierStart--;
        }
        if (identifierStart == identifierEnd || identifierStart <= 0 || text.charAt(identifierStart - 1) != '.') {
            return expression;
        }

        int mappedOffset = expression.generatedToTemplateOffset.length == 0
                ? 0
                : expression.generatedToTemplateOffset[Math.min(
                        identifierEnd - 1,
                        expression.generatedToTemplateOffset.length - 1
                )];
        String completed = text.substring(0, identifierEnd) + "()" + text.substring(identifierEnd);
        int[] mapping = new int[expression.generatedToTemplateOffset.length + 2];
        System.arraycopy(expression.generatedToTemplateOffset, 0, mapping, 0, identifierEnd);
        // Keep the synthetic cursor on the identifier. Mapping the generated
        // parentheses to the next source offset prevents toSyntheticPosition
        // from advancing the assist location past the requested prefix.
        mapping[identifierEnd] = mappedOffset + 1;
        mapping[identifierEnd + 1] = mappedOffset + 1;
        System.arraycopy(
                expression.generatedToTemplateOffset,
                identifierEnd,
                mapping,
                identifierEnd + 2,
                expression.generatedToTemplateOffset.length - identifierEnd
        );
        return new MappedExpression(completed, mapping);
    }

    private static boolean isFollowedByGeneratedClosingParenthesis(String text, int start) {
        int cursor = start;
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor < text.length() && text.charAt(cursor) == ')';
    }

    private static TemplateModel buildFromSharedSplitter(
            TemplateFormatter formatter,
            String text,
            TemplateStringSplitter.Syntax syntax
    ) {
        TemplateStringSplitter.Result split = TemplateStringSplitter.split(text, formatter.prefix(), syntax);
        TemplateModel model = new TemplateModel();
        for (TemplateStringSplitter.Range range : split.ranges) {
            if (range.style == TemplateStringSplitter.CODE) {
                model.list.add(StringRange.code(formatter, text, range.startIndex, range.endIndex));
            } else if (range.style == TemplateStringSplitter.STRING) {
                model.list.add(StringRange.string(formatter, text, range.startIndex, range.endIndex));
            } else {
                model.list.add(StringRange.of(range.style, range.startIndex, range.endIndex));
            }
        }
        model.endQuoteIndex = split.endQuoteIndex;
        return model;
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

    private static char[] cloneCharArray(Object value) {
        return value instanceof char[] ? Arrays.copyOf((char[]) value, ((char[]) value).length) : null;
    }

    private static Object readOptionalField(Object target, String fieldName) throws Exception {
        Field field = target == null ? null : findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        field.setAccessible(true);
        return field.get(target);
    }

    private static Integer mapOptionalPosition(Object target, String fieldName, TemplateTranslation translation) throws Exception {
        Object value = readOptionalField(target, fieldName);
        return value instanceof Integer && (Integer) value >= 0
                ? translation.toOriginalPosition((Integer) value)
                : null;
    }

    private static String toJavaStringLiteral(String templateText) {
        return escapeJavaString(materializeStringToken(templateText));
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

    private static void copyFieldIfPresent(Object source, Object target, String fieldName) throws Exception {
        Field sourceField = source == null ? null : findField(source.getClass(), fieldName);
        Field targetField = target == null ? null : findField(target.getClass(), fieldName);
        if (sourceField == null || targetField == null) {
            return;
        }
        sourceField.setAccessible(true);
        targetField.setAccessible(true);
        Object value = sourceField.get(source);
        if (value instanceof char[]) {
            value = Arrays.copyOf((char[]) value, ((char[]) value).length);
        }
        targetField.set(target, value);
    }

    private static Field findField(Class<?> type, String fieldName) {
        Map<String, Field> fields = FIELD_CACHE.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
        Field cached = fields.get(fieldName);
        if (cached != null) {
            return cached;
        }
        Set<String> misses = FIELD_MISSES.computeIfAbsent(type, ignored -> ConcurrentHashMap.newKeySet());
        if (misses.contains(fieldName)) {
            return null;
        }
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                fields.put(fieldName, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        misses.add(fieldName);
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
        String key = methodName + Arrays.toString(parameterTypes);
        Map<String, Method> methods = METHOD_CACHE.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
        Method cached = methods.get(key);
        if (cached != null) {
            return cached;
        }
        Set<String> misses = METHOD_MISSES.computeIfAbsent(type, ignored -> ConcurrentHashMap.newKeySet());
        if (misses.contains(key)) {
            return null;
        }
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                methods.put(key, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        misses.add(key);
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
        private final char[] completionIdentifier;
        private final Integer completedIdentifierStart;
        private final Integer completedIdentifierEnd;
        private final Integer endOfEmptyToken;
        private final char[] selectionIdentifier;

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
                Object scanContext,
                char[] completionIdentifier,
                Integer completedIdentifierStart,
                Integer completedIdentifierEnd,
                Integer endOfEmptyToken,
                char[] selectionIdentifier
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
            this.completionIdentifier = completionIdentifier;
            this.completedIdentifierStart = completedIdentifierStart;
            this.completedIdentifierEnd = completedIdentifierEnd;
            this.endOfEmptyToken = endOfEmptyToken;
            this.selectionIdentifier = selectionIdentifier;
        }
    }

    private static final class TemplateTranslation {
        private final String prefix;
        private final String translatedExpression;
        private final int[] generatedToTemplateOffset;
        private final int originalStart;
        private final int originalEnd;
        private final int resumePosition;

        private TemplateTranslation(
                String prefix,
                String translatedExpression,
                int[] generatedToTemplateOffset,
                int originalStart,
                int originalEnd,
                int resumePosition
        ) {
            this.prefix = prefix;
            this.translatedExpression = translatedExpression;
            this.generatedToTemplateOffset = generatedToTemplateOffset != null
                    ? generatedToTemplateOffset
                    : createLinearOffsetMap(translatedExpression == null ? 0 : translatedExpression.length(), 0, Math.max(0, originalEnd - originalStart));
            this.originalStart = originalStart;
            this.originalEnd = originalEnd;
            this.resumePosition = resumePosition;
        }

        private int toOriginalPosition(int generatedPosition) {
            if (generatedToTemplateOffset.length == 0) {
                return originalStart;
            }
            int safePosition = Math.max(0, Math.min(generatedToTemplateOffset.length - 1, generatedPosition));
            return Math.max(originalStart, Math.min(originalEnd, originalStart + generatedToTemplateOffset[safePosition]));
        }

        private boolean containsOriginalPosition(int originalPosition) {
            return originalPosition >= originalStart && originalPosition <= originalEnd;
        }

        private boolean intersectsOriginalRange(int rangeStart, int rangeEnd) {
            int safeStart = Math.min(rangeStart, rangeEnd);
            int safeEnd = Math.max(rangeStart, rangeEnd);
            return safeEnd >= originalStart && safeStart <= originalEnd;
        }

        private int toSyntheticPosition(int originalPosition) {
            if (generatedToTemplateOffset.length == 0 || originalPosition <= originalStart) {
                return 0;
            }
            int relative = Math.min(originalEnd - originalStart, originalPosition - originalStart);
            int best = 0;
            for (int index = 0; index < generatedToTemplateOffset.length; index++) {
                if (generatedToTemplateOffset[index] > relative) {
                    break;
                }
                best = index;
            }
            return best;
        }
    }

    private static final class CachedTemplateTranslation {
        private final String translatedExpression;
        private final int[] generatedToTemplateOffset;
        private final int endQuoteIndex;

        private CachedTemplateTranslation(String translatedExpression, int[] generatedToTemplateOffset, int endQuoteIndex) {
            this.translatedExpression = translatedExpression;
            this.generatedToTemplateOffset = generatedToTemplateOffset;
            this.endQuoteIndex = endQuoteIndex;
        }
    }

    private static final class MappedExpression {
        private final String text;
        private final int[] generatedToTemplateOffset;

        private MappedExpression(String text, int[] generatedToTemplateOffset) {
            this.text = text;
            this.generatedToTemplateOffset = generatedToTemplateOffset;
        }

        private static MappedExpression synthetic(String text, int startOffset, int endOffset) {
            return new MappedExpression(text, createLinearOffsetMap(text == null ? 0 : text.length(), startOffset, endOffset));
        }
    }

    private static final class MappedTextBuilder {
        private final StringBuilder text = new StringBuilder();
        private final List<Integer> offsets = new ArrayList<>();

        private void appendSynthetic(String value, int startOffset, int endOffset) {
            if (value == null || value.isEmpty()) {
                return;
            }
            int[] mapping = createLinearOffsetMap(value.length(), startOffset, endOffset);
            text.append(value);
            for (int offset : mapping) {
                offsets.add(offset);
            }
        }

        private void appendOriginal(String value, int originalStart, int originalEnd) {
            appendSynthetic(value, originalStart, Math.max(originalStart, originalEnd - 1));
        }

        private MappedExpression build() {
            int[] mapping = new int[offsets.size()];
            for (int index = 0; index < offsets.size(); index++) {
                mapping[index] = offsets.get(index);
            }
            return new MappedExpression(text.toString(), mapping);
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

        default MappedExpression translate(List<StringRange> build, String text) {
            String translated = printOut(build, text);
            return MappedExpression.synthetic(translated, 0, Math.max(0, text.length() - 1));
        }

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
        public MappedExpression translate(List<StringRange> build, String text) {
            return translateAsStringConcatenation(build, text, prefix().length());
        }

        @Override
        public TemplateModel build(String text) {
            return buildFromSharedSplitter(this, text, TemplateStringSplitter.Syntax.DOLLAR);
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
            return buildFromSharedSplitter(this, text, TemplateStringSplitter.Syntax.STR);
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

        @Override
        public MappedExpression translate(List<StringRange> build, String text) {
            return translateAsStringConcatenation(build, text, prefix().length());
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
            return buildFromSharedSplitter(this, text, TemplateStringSplitter.Syntax.FORMAT);
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
