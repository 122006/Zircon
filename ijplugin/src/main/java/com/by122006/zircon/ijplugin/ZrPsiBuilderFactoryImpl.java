package com.by122006.zircon.ijplugin;

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public class ZrPsiBuilderFactoryImpl extends PsiBuilderFactoryImpl {

    @NotNull
    public PsiBuilder createBuilder(@NotNull Project project, @NotNull ASTNode chameleon, @Nullable Lexer lexer, @NotNull Language lang, @NotNull CharSequence seq) {
//            PsiBuilder var10000 = super.createBuilder(project, chameleon, (Lexer)lexer, lang, seq);
//            return var10000;

        ParserDefinition parserDefinition = (ParserDefinition) LanguageParserDefinitions.INSTANCE.forLanguage(lang);
        if (lexer instanceof JavaLexer) {
            LanguageLevel level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
            lexer = new ZrJavaLexer(level);
        } else if (lexer == null) {
            try {
                Method createLexer = PsiBuilderFactoryImpl.class.getDeclaredMethod("createLexer", Project.class, Language.class);
                createLexer.setAccessible(true);
                lexer= (Lexer) createLexer.invoke(null,project,lang);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return new PsiBuilderImpl(project, parserDefinition, lexer, chameleon, seq);
    }
}
