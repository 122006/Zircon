package com.by122006.zircon.ijplugin;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiParser;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZrJavaParserDefinition extends JavaParserDefinition {
    private static final Logger LOG = Logger.getInstance(ZrJavaParserDefinition.class.getName());

    public ZrJavaParserDefinition() {
        LOG.info( "ZrJavaParserDefinition" );
    }

    @NotNull
    @Override
    public Lexer createLexer(@Nullable Project project) {
        LanguageLevel level = project != null ? LanguageLevelProjectExtension.getInstance(project).getLanguageLevel() : LanguageLevel.HIGHEST;
        return createLexer(level);
    }

    @NotNull
    public static Lexer createLexer(@NotNull LanguageLevel level) {
        return new ZrJavaLexer(level);
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return super.createParser(project);
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        if (node.getText().contains("?:")){
            return super.createElement(node);
        }
        return super.createElement(node);
    }
}
