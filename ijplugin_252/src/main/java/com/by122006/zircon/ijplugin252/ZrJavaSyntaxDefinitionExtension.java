package com.by122006.zircon.ijplugin252;

import com.intellij.java.frontback.psi.impl.syntax.JavaSyntaxDefinitionExtension;
import com.intellij.platform.syntax.LanguageSyntaxDefinition;
import com.intellij.platform.syntax.SyntaxElementTypeSet;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.parser.OpaqueElementPolicy;
import com.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @ClassName: ZrJavaSyntaxDefinitionExtension
 * @Author: 122006
 * @Date: 2025/8/25 16:46
 * @Description:
 */
public class ZrJavaSyntaxDefinitionExtension implements LanguageSyntaxDefinition {
    public static final ZrJavaLexer252 JAVA_LEXER = new ZrJavaLexer252(LanguageLevel.HIGHEST);
    JavaSyntaxDefinitionExtension javaSyntaxDefinitionExtension = new JavaSyntaxDefinitionExtension();

    @Override
    public @NotNull Lexer getLexer() {
        return JAVA_LEXER;
    }

    @Override
    public @NotNull SyntaxElementTypeSet getWhitespaceTokens() {
        return javaSyntaxDefinitionExtension.getWhitespaceTokens();
    }

    @Override
    public @NotNull SyntaxElementTypeSet getCommentTokens() {
        return javaSyntaxDefinitionExtension.getCommentTokens();
    }

    @Override
    public @Nullable WhitespaceOrCommentBindingPolicy getWhitespaceOrCommentBindingPolicy() {
        return javaSyntaxDefinitionExtension.getWhitespaceOrCommentBindingPolicy();
    }

    @Override
    public @Nullable OpaqueElementPolicy getOpaqueElementPolicy() {
        return javaSyntaxDefinitionExtension.getOpaqueElementPolicy();
    }
}
