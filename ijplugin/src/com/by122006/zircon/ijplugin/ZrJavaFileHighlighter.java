package com.by122006.zircon.ijplugin;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.*;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class ZrJavaFileHighlighter extends JavaFileHighlighter {

    public static final TextAttributesKey COMMENT = createTextAttributesKey("SIMPLE_COMMENT", SyntaxHighlighterColors.LINE_COMMENT);

    public ZrJavaFileHighlighter() {
        this(LanguageLevel.HIGHEST);
    }

    public ZrJavaFileHighlighter(@NotNull LanguageLevel languageLevel) {
        super(languageLevel);
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new ZrJavaHighlightingLexer(this.myLanguageLevel);
    }


    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(JavaTokenType.STRING_LITERAL)) {
            return new TextAttributesKey[]{COMMENT};
        }
        return super.getTokenHighlights(tokenType);
    }

}
