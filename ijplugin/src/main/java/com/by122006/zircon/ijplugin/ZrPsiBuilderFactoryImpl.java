package com.by122006.zircon.ijplugin;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import zircon.example.ExReflection;

public class ZrPsiBuilderFactoryImpl extends PsiBuilderFactoryImpl {
    @NotNull
    public PsiBuilder createBuilder(@NotNull Project project, @NotNull ASTNode chameleon, @Nullable Lexer lexer, @NotNull Language lang, @NotNull CharSequence seq) {
        ParserDefinition parserDefinition = reflectionInvokeMethod("getParserDefinition",lang, chameleon.getElementType());
        if (lexer instanceof JavaLexer) {
            LanguageLevel level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
            lexer = new ZrJavaLexer(level);
        } else if (lexer == null) {
            try {
                lexer=parserDefinition.createLexer(project);
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("createLexer error:"+e.getMessage());
            }
        }

        return new PsiBuilderImpl(project, parserDefinition, lexer, chameleon, seq);
    }
}
