package com.by122006.zircon.ijplugin252;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.java.frontback.psi.impl.syntax.JavaSyntaxDefinitionExtension;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.platform.syntax.LanguageSyntaxDefinition;
import com.intellij.platform.syntax.SyntaxElementTypeSet;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.parser.OpaqueElementPolicy;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
import com.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy;
import com.intellij.pom.java.LanguageLevel;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExReflection;

/**
 * @ClassName: ZrJavaSyntaxDefinitionExtension
 * @Author: 122006
 * @Date: 2025/8/25 16:46
 * @Description:
 */
public class ZrJavaSyntaxDefinitionExtension implements LanguageSyntaxDefinition {

    Logger logger = Logger.getInstance(ZrJavaSyntaxDefinitionExtension.class);

    public static final ZrJavaLexer252 JAVA_LEXER = new ZrJavaLexer252(LanguageLevel.HIGHEST);
    LanguageSyntaxDefinition javaSyntaxDefinitionExtension;

    public ZrJavaSyntaxDefinitionExtension() {
        try {
            final Class<?> aClass = Class.forName("com.intellij.java.frontback.psi.impl.syntax.JavaSyntaxDefinitionExtension");
            javaSyntaxDefinitionExtension = (LanguageSyntaxDefinition) aClass.getConstructor().newInstance();
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public @Nullable WhitespaceOrCommentBindingPolicy getWhitespaceOrCommentBindingPolicy() {
        return javaSyntaxDefinitionExtension.reflectionInvokeMethod("getWhitespaceOrCommentBindingPolicy");
    }

    @Override
    public @Nullable OpaqueElementPolicy getOpaqueElementPolicy() {
        return javaSyntaxDefinitionExtension.reflectionInvokeMethod("getOpaqueElementPolicy");
    }

    @Override
    public @NotNull SyntaxElementTypeSet getComments() {
        return javaSyntaxDefinitionExtension.reflectionInvokeMethod("getComments");
    }

    @Override
    public void parse(@NotNull SyntaxTreeBuilder syntaxTreeBuilder) {
        javaSyntaxDefinitionExtension.reflectionInvokeMethod("parse", syntaxTreeBuilder);
    }

    @Override
    public @NotNull Lexer createLexer() {
        return JAVA_LEXER;
    }
}
