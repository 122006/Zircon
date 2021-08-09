package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ZrJavaHighlightingLexer extends LayeredLexer {
    public ZrJavaHighlightingLexer(LanguageLevel languageLevel) {
        super(ZrJavaParserDefinition.createLexer(languageLevel));
        this.registerSelfStoppingLayer(new StringLiteralLexer('"', JavaTokenType.STRING_LITERAL, false, "$\""), new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);
    }
}
