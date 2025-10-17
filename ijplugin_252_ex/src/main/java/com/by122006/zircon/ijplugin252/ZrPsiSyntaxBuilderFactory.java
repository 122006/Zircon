package com.by122006.zircon.ijplugin252;

import com.by122006.zircon.ijplugin252.ZrJavaLexer252;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilder;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ZrPsiSyntaxBuilderFactory implements PsiSyntaxBuilderFactory {
    public final ZrJavaLexer252 JAVA_LEXER = new ZrJavaLexer252(LanguageLevel.HIGHEST);

    @Override
    public @NotNull PsiSyntaxBuilder createBuilder(@NotNull ASTNode astNode, @Nullable Lexer lexer, @NotNull Language language, @NotNull CharSequence charSequence) {
        if (lexer != null && !(lexer instanceof ZrJavaLexer252) && lexer instanceof JavaLexer) {
            lexer = new ZrJavaLexer252((JavaLexer) lexer);
        }
        final PsiSyntaxBuilder builder = PsiSyntaxBuilderFactory.Companion.defaultBuilderFactory().createBuilder(astNode, lexer, language, charSequence);
        return builder;
    }

    @Override
    public @NotNull PsiSyntaxBuilder createBuilder(@NotNull LighterLazyParseableNode lighterLazyParseableNode, @Nullable Lexer lexer, @NotNull Language language, @NotNull CharSequence charSequence) {
        if (lexer != null && !(lexer instanceof ZrJavaLexer252) && lexer instanceof JavaLexer) {
            lexer = new ZrJavaLexer252((JavaLexer) lexer);
        }
        final PsiSyntaxBuilder builder = PsiSyntaxBuilderFactory.Companion.defaultBuilderFactory().createBuilder(lighterLazyParseableNode, lexer, language, charSequence);
        return builder;
    }
}
