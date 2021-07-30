package com.by122006.zircon.ijplugin;

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
        this.registerSelfStoppingLayer(new StringLiteralLexer('"', JavaTokenType.STRING_LITERAL, false, "$"), new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);
        this.registerSelfStoppingLayer(new StringLiteralLexer('"', JavaTokenType.STRING_LITERAL, false, "s"), new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);
        this.registerSelfStoppingLayer(new StringLiteralLexer('\'', JavaTokenType.STRING_LITERAL), new IElementType[]{JavaTokenType.CHARACTER_LITERAL}, IElementType.EMPTY_ARRAY);
        this.registerSelfStoppingLayer(new StringLiteralLexer('\uffff', JavaTokenType.TEXT_BLOCK_LITERAL, true, "s"), new IElementType[]{JavaTokenType.TEXT_BLOCK_LITERAL}, IElementType.EMPTY_ARRAY);
        LayeredLexer docLexer = new LayeredLexer(ZrJavaParserDefinition.createDocLexer(languageLevel));
        HtmlHighlightingLexer htmlLexer = new HtmlHighlightingLexer((FileType)null);
        htmlLexer.setHasNoEmbeddments(true);
        docLexer.registerLayer(htmlLexer, new IElementType[]{JavaDocTokenType.DOC_COMMENT_DATA});
        this.registerSelfStoppingLayer(docLexer, new IElementType[]{JavaDocElementType.DOC_COMMENT}, IElementType.EMPTY_ARRAY);
    }
}
