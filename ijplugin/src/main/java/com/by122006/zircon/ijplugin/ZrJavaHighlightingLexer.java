package com.by122006.zircon.ijplugin;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;

public class ZrJavaHighlightingLexer extends LayeredLexer {
    public ZrJavaHighlightingLexer(LanguageLevel languageLevel) {
        super(ZrJavaParserDefinition.createLexer(languageLevel));
//        this.registerSelfStoppingLayer(new ZrStringLiteralLexer(JavaTokenType.STRING_LITERAL), new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);
        this.registerSelfStoppingLayer(new StringLiteralLexer('"', JavaTokenType.STRING_LITERAL, false, ""), new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);
    }

}
