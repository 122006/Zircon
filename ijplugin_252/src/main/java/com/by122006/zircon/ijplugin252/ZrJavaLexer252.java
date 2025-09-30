package com.by122006.zircon.ijplugin252;

import com.intellij.java.syntax.element.JavaDocSyntaxElementType;
import com.intellij.java.syntax.element.JavaSyntaxTokenType;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.element.SyntaxTokenTypes;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.lexer.LexerPosition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.text.CharArrayUtil;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

public class ZrJavaLexer252 implements Lexer {
//    static {
//        try {
//            //强制设置)和.之间不含空格
//            Map<Pair<JavaSyntaxTokenType, JavaSyntaxTokenType>, Boolean> ourTokenStickingMatrix = JavaSpacePropertyProcessor.class.getStaticFieldValue("ourTokenStickingMatrix");
//            ourTokenStickingMatrix.put(Pair.pair(JavaSyntaxTokenType.RPARENTH, JavaSyntaxTokenType.DOT), true);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private static final Logger LOG = Logger.getInstance(ZrJavaLexer252.class.getName());


    private Object myFlexLexer;
    private CharSequence myBuffer;
    private @Nullable
    char[] myBufferArray;
    private int myBufferIndex;
    private int myBufferEndOffset;
    private int myTokenEndOffset;  // positioned after the last symbol of the current token
    private SyntaxElementType myTokenType;

    public ZrJavaLexer252(@NotNull LanguageLevel level) {

        try {
            Constructor<?> constructor = getFlexClazz().getConstructor(LanguageLevel.class);
            constructor.setAccessible(true);
            myFlexLexer = constructor.newInstance(level);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private Class getFlexClazz() {
        try {
            return Class.forName("com.intellij.java.syntax.lexer._JavaLexer");
        } catch (ClassNotFoundException e) {
            LOG.error(e);
            throw new RuntimeException();
        }
    }

    public ZrJavaLexer252(com.intellij.lang.java.lexer.JavaLexer lexer) {
        super();
        myFlexLexer = com.sun.tools.javac.parser.ReflectionUtil.getDeclaredField(lexer, JavaLexer.class, "myFlexLexer");
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
//        LOG.info("ZrJavaLexer start size:"+buffer.length());
//        if (buffer.length()<100){
//            LOG.info(buffer.toString());
//        }
        myBuffer = buffer;
        myBufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);
        myBufferIndex = startOffset;
        myBufferEndOffset = endOffset;
        myTokenType = null;
        myTokenEndOffset = startOffset;
        try {
            Method reset = getFlexClazz().getMethod("reset", CharSequence.class, int.class, int.class, int.class);
            reset.setAccessible(true);
            reset.invoke(myFlexLexer, myBuffer, startOffset, endOffset, 0);
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public SyntaxElementType getTokenType() {
        locateToken();
        return myTokenType;
    }

    @Override
    public int getTokenStart() {
        return myBufferIndex;
    }

    @Override
    public int getTokenEnd() {
        locateToken();
        return myTokenEndOffset;
    }

    @Override
    public void advance() {
        locateToken();
        myTokenType = null;
    }

    ElementTypesInfo[] infos = null;


    public static class ElementTypesInfo {
        SyntaxElementType iElementType = null;
        int endTokenEndOffset = -1;

        public ElementTypesInfo(SyntaxElementType iElementType, int endTokenEndOffset) {
            this.iElementType = iElementType;
            this.endTokenEndOffset = endTokenEndOffset;
        }
    }

    static SyntaxElementType WHITE_SPACE = ReflectionUtil.getDeclaredField(null, SyntaxTokenTypes.class, "WHITE_SPACE");

    private void locateToken() {
        if (myTokenType != null) return;
        if (infos != null) {
            if (infos.length != 0) {
                final ElementTypesInfo info = infos[0];
                infos = Arrays.copyOfRange(infos, 1, infos.length);
                myBufferIndex = info.endTokenEndOffset;
                myTokenType = info.iElementType;
                return;
            } else {
                infos = null;
            }
        }


        if (myTokenEndOffset == myBufferEndOffset) {
            myBufferIndex = myBufferEndOffset;
            return;
        }

        myBufferIndex = myTokenEndOffset;

        char c = charAt(myBufferIndex);
        switch (c) {
            case ' ':
            case '\t':
            case '\n':
            case '\r':
            case '\f':
                myTokenType = WHITE_SPACE;
                myTokenEndOffset = getWhitespaces(myBufferIndex + 1);
                break;

            case '/':
                if (myBufferIndex + 1 >= myBufferEndOffset) {
                    myTokenType = JavaSyntaxTokenType.DIV;
                    myTokenEndOffset = myBufferEndOffset;
                } else {
                    char nextChar = charAt(myBufferIndex + 1);
                    if (nextChar == '/') {
                        myTokenType = JavaSyntaxTokenType.END_OF_LINE_COMMENT;
                        myTokenEndOffset = getLineTerminator(myBufferIndex + 2);
                    } else if (nextChar == '*') {
                        if (myBufferIndex + 2 >= myBufferEndOffset ||
                                (charAt(myBufferIndex + 2)) != '*' ||
                                (myBufferIndex + 3 < myBufferEndOffset &&
                                        (charAt(myBufferIndex + 3)) == '/')) {
                            myTokenType = JavaSyntaxTokenType.C_STYLE_COMMENT;
                            myTokenEndOffset = getClosingComment(myBufferIndex + 2);
                        } else {
                            myTokenType = JavaDocSyntaxElementType.DOC_COMMENT;
                            myTokenEndOffset = getClosingComment(myBufferIndex + 3);
                        }
                    } else {
                        flexLocateToken();
                    }
                }
                break;

            case '#':
                if (myBufferIndex == 0 && myBufferIndex + 1 < myBufferEndOffset && charAt(myBufferIndex + 1) == '!') {
                    myTokenType = JavaSyntaxTokenType.END_OF_LINE_COMMENT;
                    myTokenEndOffset = getLineTerminator(myBufferIndex + 2);
                } else {
                    flexLocateToken();
                }
                break;
            case '?':
                if (charAt(myBufferIndex + 1) == '.' && myBufferIndex + 1 < myBufferEndOffset && ((charAt(myBufferIndex + 2) < '0') || (charAt(myBufferIndex + 2) > '9'))) {
                    myTokenType = JavaSyntaxTokenType.DOT;
                    myTokenEndOffset = myBufferIndex + 2;
//                    flexLocateToken();
                } else if (charAt(myBufferIndex + 1) == ':') {
//                    infos = new ElementTypesInfo[]{new ElementTypesInfo(JavaSyntaxTokenType.EQEQ, myTokenEndOffset),
//                            new ElementTypesInfo(JavaSyntaxTokenType.EQEQ, myTokenEndOffset),
//                            new ElementTypesInfo(JavaSyntaxTokenType.NULL_KEYWORD, myTokenEndOffset),
//                            new ElementTypesInfo(JavaSyntaxTokenType.COLON, myTokenEndOffset + 1)};
//                    myTokenType = JavaSyntaxTokenType.QUEST;
//                    myTokenEndOffset = myBufferIndex;
                    myTokenType = ZrJavaSyntaxTokenType.ELVIS;
                    myTokenEndOffset = myBufferIndex + 2;
//                    flexLocateToken();
                } else {
                    flexLocateToken();
                }
                break;
            case '\'':
                myTokenType = JavaSyntaxTokenType.CHARACTER_LITERAL;
                myTokenEndOffset = getClosingQuote0(myBufferIndex + 1, c);
                break;

            case '"':
                if (myBufferIndex + 2 < myBufferEndOffset && charAt(myBufferIndex + 2) == '"' && charAt(myBufferIndex + 1) == '"') {
                    myTokenType = JavaSyntaxTokenType.TEXT_BLOCK_LITERAL;
                    myTokenEndOffset = getTextBlockEnd(myBufferIndex + 2);
                } else {
                    myTokenType = JavaSyntaxTokenType.STRING_LITERAL;
                    myTokenEndOffset = getClosingQuote0(myBufferIndex + 1, c);
                }
                break;
            default: {

                Formatter formatter = Formatter.getAllFormatters().stream().filter((Formatter a) -> {
                    String prefix = a.prefix();
                    int length = prefix.length();
                    if (myBufferIndex + length >= myBuffer.length()) return false;
                    if (charAt(myBufferIndex + length) != '"') return false;
                    for (int i = 0; i < length; i++) {
                        if (charAt(myBufferIndex + i) != prefix.charAt(i)) return false;
                    }
                    return true;
                }).findFirst().orElse(null);
                if (formatter != null) {
//                    LOG.info("myBufferEndOffset: " + myBufferEndOffset);
                    myTokenType = JavaSyntaxTokenType.STRING_LITERAL;
                    myTokenEndOffset = getClosingQuote(formatter, myBufferIndex, '"');
                } else {
                    flexLocateToken();
                }
            }
        }

        if (myTokenEndOffset > myBufferEndOffset) {
            myTokenEndOffset = myBufferEndOffset;
        }
    }

    private int getWhitespaces(int offset) {
        if (offset >= myBufferEndOffset) {
            return myBufferEndOffset;
        }

        int pos = offset;
        char c = charAt(pos);

        while (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
            pos++;
            if (pos == myBufferEndOffset) return pos;
            c = charAt(pos);
        }

        return pos;
    }

    private static Method goTo = null;
    private static Method advance = null;
    private static Method getTokenEnd = null;

    private void flexLocateToken() {
        try {
            if (goTo == null) {
                goTo = getFlexClazz().getMethod("goTo", int.class);
                goTo.setAccessible(true);
            }
            goTo.invoke(myFlexLexer, myBufferIndex);
            if (advance == null) {
                advance = getFlexClazz().getMethod("advance");
                advance.setAccessible(true);
            }
            myTokenType = (SyntaxElementType) advance.invoke(myFlexLexer);
            if (getTokenEnd == null) {
                getTokenEnd = getFlexClazz().getMethod("getTokenEnd");
                getTokenEnd.setAccessible(true);
            }
            myTokenEndOffset = (int) getTokenEnd.invoke(myFlexLexer);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn(e);
        }
    }

    private int getClosingQuote0(int offset, char quoteChar) {
        if (offset >= myBufferEndOffset) {
            return myBufferEndOffset;
        }

        int pos = offset;
        char c = charAt(pos);

        while (true) {
            while (c != quoteChar && c != '\n' && c != '\r' && c != '\\') {
                pos++;
                if (pos >= myBufferEndOffset) return myBufferEndOffset;
                c = charAt(pos);
            }

            if (c == '\\') {
                pos++;
                if (pos >= myBufferEndOffset) return myBufferEndOffset;
                c = charAt(pos);
                if (c == '\n' || c == '\r') continue;
                pos++;
                if (pos >= myBufferEndOffset) return myBufferEndOffset;
                c = charAt(pos);
            } else if (c == quoteChar) {
                break;
            } else {
                pos--;
                break;
            }
        }

        return pos + 1;
    }

    private int getClosingQuote(Formatter formatter, int offset, char quoteChar) {
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
//        LOG.info("Read ZrString："+build.getOriginalString());
        return build.getEndQuoteIndex() + startIndex + 1;
    }

    private int getClosingComment(int offset) {
        int pos = offset;

        while (pos < myBufferEndOffset - 1) {
            char c = charAt(pos);
            if (c == '*' && (charAt(pos + 1)) == '/') {
                break;
            }
            pos++;
        }

        return pos + 2;
    }

    private int getLineTerminator(int offset) {
        int pos = offset;

        while (pos < myBufferEndOffset) {
            char c = charAt(pos);
            if (c == '\r' || c == '\n') break;
            pos++;
        }

        return pos;
    }

    private int getTextBlockEnd(int offset) {
        int pos = offset;

        while ((pos = getClosingQuote0(pos + 1, '"')) < myBufferEndOffset) {
            char current = charAt(pos);
            if (current == '\\') {
                pos++;
            } else if (current == '"' && pos + 1 < myBufferEndOffset && charAt(pos + 1) == '"') {
                pos += 2;
                break;
            }
        }

        return pos;
    }

    private char charAt(int position) {
        try {
            return myBufferArray != null ? myBufferArray[position] : myBuffer.charAt(position);
        } catch (Exception e) {
            e.printStackTrace();
            return ' ';
        }
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return myBuffer;
    }

    @Override
    public int getBufferEnd() {
        return myBufferEndOffset;
    }


    @Override
    public void start(@NotNull CharSequence buf, int start, int end) {
        start(buf, start, end, STATE_DEFAULT);
    }


    @Override
    public void start(@NotNull CharSequence buf) {
        start(buf, 0, buf.length());

    }

    @Override
    public @NotNull LexerPosition getCurrentPosition() {
        int offset = getTokenStart();
        int intState = getState();
        return new LexerPositionImpl(offset, intState);
    }

    @Override
    public void restore(@NotNull LexerPosition position) {
        start(getBufferSequence(), position.getOffset(), getBufferEnd(), position.getState());
    }

    private int STATE_DEFAULT = 0;


    private class LexerPositionImpl implements LexerPosition {
        private final int offset;
        private final int state;

        public LexerPositionImpl(int offset, int state) {
            this.offset = offset;
            this.state = state;
        }

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public int getState() {
            return state;
        }
    }

}
