package com.by122006.zircon.ijplugin252;

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


public class ZrPsiSyntaxBuilderEmptyFactory implements PsiSyntaxBuilderFactory {

    @Override
    public @NotNull PsiSyntaxBuilder createBuilder(@NotNull ASTNode astNode, @Nullable Lexer lexer, @NotNull Language language, @NotNull CharSequence charSequence) {
        final PsiSyntaxBuilder builder = Companion.defaultBuilderFactory().createBuilder(astNode, lexer, language, charSequence);
        return builder;
    }

    @Override
    public @NotNull PsiSyntaxBuilder createBuilder(@NotNull LighterLazyParseableNode lighterLazyParseableNode, @Nullable Lexer lexer, @NotNull Language language, @NotNull CharSequence charSequence) {
        final PsiSyntaxBuilder builder = Companion.defaultBuilderFactory().createBuilder(lighterLazyParseableNode, lexer, language, charSequence);
        return builder;
    }
}
