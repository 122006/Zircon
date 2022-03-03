package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.*;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UField;

public class ZrJavaHighlightingLexer extends LayeredLexer {
    public ZrJavaHighlightingLexer(LanguageLevel languageLevel) {
        super(ZrJavaParserDefinition.createLexer(languageLevel));
//        this.registerSelfStoppingLayer(new ZrStringLiteralLexer(JavaTokenType.STRING_LITERAL), new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);
        this.registerSelfStoppingLayer(new StringLiteralLexer('"', JavaTokenType.STRING_LITERAL, false, "" ), new IElementType[]{JavaTokenType.STRING_LITERAL}, IElementType.EMPTY_ARRAY);
    }

}
