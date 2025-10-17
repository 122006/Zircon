package com.by122006.zircon.ijplugin252;

import com.intellij.java.syntax.element.JavaSyntaxTokenType;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.lexer.LexerPosition;
import com.intellij.pom.java.LanguageLevel;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrStringModel;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@Log4j
public class ZrJavaLexer252 implements Lexer {

    @Setter
    @NotNull JavaLexer javaLexer252 = null;
    private static final Logger LOG = Logger.getInstance(ZrJavaLexer252.class.getName());

    public ZrJavaLexer252(@NotNull LanguageLevel level) {
        javaLexer252 = new JavaLexer(level);
    }

    public ZrJavaLexer252(JavaLexer javaLexer) {
        this.javaLexer252 = javaLexer;
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        javaLexer252.start(buffer, startOffset, endOffset, initialState);
    }

    @Override
    public int getState() {
        return javaLexer252.getState();
    }

    @Override
    public SyntaxElementType getTokenType() {
        _locateToken();
        return ReflectionUtil.getDeclaredField(javaLexer252, JavaLexer.class, "myTokenType");
    }

    @Override
    public int getTokenStart() {
        _locateToken();
        return ReflectionUtil.getDeclaredField(javaLexer252, JavaLexer.class, "myBufferIndex");
    }

    @Override
    public int getTokenEnd() {
        _locateToken();
        return ReflectionUtil.getDeclaredField(javaLexer252, JavaLexer.class, "myTokenEndOffset");
    }

    @Override
    public void advance() {
        _locateToken();
        ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myTokenType", null);
    }

    ElementTypesInfo[] infos = null;

    private void _locateToken() {
        SyntaxElementType myTokenType = ReflectionUtil.getDeclaredField(javaLexer252, JavaLexer.class, "myTokenType");
        int myTokenEndOffset = ReflectionUtil.getDeclaredField(javaLexer252, JavaLexer.class, "myTokenEndOffset");
        int myBufferEndOffset = ReflectionUtil.getDeclaredField(javaLexer252, JavaLexer.class, "myBufferEndOffset");
        int myBufferIndex = ReflectionUtil.getDeclaredField(javaLexer252, JavaLexer.class, "myBufferIndex");

        if (myTokenType != null) return;

        if (infos != null) {
            if (infos.length != 0) {
                final ElementTypesInfo info = infos[0];
                infos = Arrays.copyOfRange(infos, 1, infos.length);
                ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myBufferIndex", info.endTokenEndOffset);
                ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myTokenType", info.tokenType);
                return;
            } else {
                infos = null;
            }
        }

        if (myTokenEndOffset == myBufferEndOffset) {
            ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myBufferIndex", myBufferEndOffset);
            return;
        }
        ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myBufferIndex", myTokenEndOffset);
        CharSequence myBuffer = ReflectionUtil.getDeclaredField(javaLexer252, JavaLexer.class, "myBuffer");
        char c = ReflectionUtil.invokeMethod(javaLexer252, JavaLexer.class, "locateCharAt", myTokenEndOffset);

        final int currentIndex = myTokenEndOffset;
        switch (c) {
            case '\u001a':
            case ' ':
            case '\t':
            case '\n':
            case '\r':
            case '\u000C':
            case '{':
            case '}':
            case '/':
            case '#':
            case '"':
            case '\'':
                ReflectionUtil.invokeMethod(javaLexer252, JavaLexer.class, "locateToken");
                break;
            case '?':
                if (charAt(currentIndex + 1) == '.' && currentIndex + 1 < myBufferEndOffset && ((charAt(currentIndex + 2) < '0') || (charAt(currentIndex + 2) > '9'))) {
                    ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myTokenType", JavaSyntaxTokenType.DOT);
                    ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myTokenEndOffset", currentIndex + 2);
                } else if (charAt(currentIndex + 1) == ':') {
                    infos = new ElementTypesInfo[]{
                            new ElementTypesInfo(JavaSyntaxTokenType.NULL_KEYWORD, currentIndex + 2),
                            new ElementTypesInfo(JavaSyntaxTokenType.COLON, currentIndex + 2)};
                    ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myTokenType", JavaSyntaxTokenType.QUEST);
                    ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myTokenEndOffset", currentIndex + 2);
                } else {
                    ReflectionUtil.invokeMethod(javaLexer252, JavaLexer.class, "flexLocateToken");
                }
                break;
            default: {
                Formatter formatter = Formatter.getAllFormatters().stream().filter((Formatter a) -> {
                    String prefix = a.prefix();
                    int length = prefix.length();
                    if (currentIndex + length >= myBuffer.length()) return false;
                    if (charAt(currentIndex + length) != '"') return false;
                    for (int i = 0; i < length; i++) {
                        if (charAt(currentIndex + i) != prefix.charAt(i)) return false;
                    }
                    return true;
                }).findFirst().orElse(null);
                if (formatter != null) {
                    ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myTokenType", JavaSyntaxTokenType.STRING_LITERAL);
                    final int closingQuote = getClosingQuote(formatter, currentIndex, '"', myBufferEndOffset, myBuffer);
                    ReflectionUtil.setDeclaredField(javaLexer252, JavaLexer.class, "myTokenEndOffset", closingQuote);
                } else {
                    ReflectionUtil.invokeMethod(javaLexer252, JavaLexer.class, "locateToken");
                }
                ReflectionUtil.invokeMethod(javaLexer252, JavaLexer.class, "locateToken");

            }
        }

    }

    private int getClosingQuote(Formatter formatter, int offset, char quoteChar, int myBufferEndOffset, CharSequence myBuffer) {
        int startIndex = offset;
        while (true) {
            offset++;
            if (offset >= myBufferEndOffset) break;
            char ch = charAt(offset);
            if (ch == '\n' || ch == '\r') {
                break;
            }
        }
        final String s = myBuffer.subSequence(startIndex, offset).toString();
        final ZrStringModel build = formatter.build(s);
        LOG.info("Read ZrString：" + build.getOriginalString());
        return build.getEndQuoteIndex() + startIndex + 1;
    }

    private char charAt(int i) {
        return ReflectionUtil.invokeMethod(javaLexer252, JavaLexer.class, "locateCharAt", i);
    }


    @Override
    public void start(@NotNull CharSequence charSequence, int i, int i1) {
        javaLexer252.start(charSequence, i, i1);
    }

    @Override
    public void start(@NotNull CharSequence charSequence) {
        javaLexer252.start(charSequence);
    }

    @Override
    public @NotNull LexerPosition getCurrentPosition() {
        return javaLexer252.getCurrentPosition();
    }

    @Override
    public void restore(@NotNull LexerPosition lexerPosition) {
        javaLexer252.restore(lexerPosition);
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return javaLexer252.getBufferSequence();
    }

    @Override
    public int getBufferEnd() {
        return javaLexer252.getBufferEnd();
    }

    public static class ElementTypesInfo {
        SyntaxElementType tokenType = null;
        int endTokenEndOffset = -1;

        public ElementTypesInfo(SyntaxElementType tokenType, int endTokenEndOffset) {
            this.tokenType = tokenType;
            this.endTokenEndOffset = endTokenEndOffset;
        }
    }
}
