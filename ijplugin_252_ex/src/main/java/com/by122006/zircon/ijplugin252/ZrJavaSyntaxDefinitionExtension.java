package com.by122006.zircon.ijplugin252;

import com.intellij.java.frontback.psi.impl.syntax.JavaSyntaxDefinitionExtension;
import com.intellij.platform.syntax.LanguageSyntaxDefinition;
import com.intellij.platform.syntax.SyntaxElementTypeSet;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.parser.OpaqueElementPolicy;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
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
    public @Nullable WhitespaceOrCommentBindingPolicy getWhitespaceOrCommentBindingPolicy() {
        return javaSyntaxDefinitionExtension.getWhitespaceOrCommentBindingPolicy();
    }

    @Override
    public @Nullable OpaqueElementPolicy getOpaqueElementPolicy() {
        return javaSyntaxDefinitionExtension.getOpaqueElementPolicy();
    }

    @Override
    public @NotNull SyntaxElementTypeSet getComments() {
        return javaSyntaxDefinitionExtension.getComments();
    }

    @Override
    public void parse(@NotNull SyntaxTreeBuilder syntaxTreeBuilder) {
        javaSyntaxDefinitionExtension.parse(syntaxTreeBuilder);
    }

    @Override
    public @NotNull Lexer createLexer() {
        return JAVA_LEXER;
    }
}
