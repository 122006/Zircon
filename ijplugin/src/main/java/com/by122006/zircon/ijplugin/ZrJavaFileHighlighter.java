package com.by122006.zircon.ijplugin;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExObject;


public class ZrJavaFileHighlighter extends JavaFileHighlighter {

    public static final TextAttributesKey COMMENT = TextAttributesKey.createTextAttributesKey("SIMPLE_COMMENT", HighlighterColors.TEXT);

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
