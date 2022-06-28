package com.by122006.zircon.ijplugin;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
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
        LOG.info( "createLexer" );
        LanguageLevel level = project != null ? LanguageLevelProjectExtension.getInstance(project).getLanguageLevel() : LanguageLevel.HIGHEST;
        return createLexer(level);
    }

    @NotNull
    public static Lexer createLexer(@NotNull LanguageLevel level) {
        LOG.info( "createLexer" );
        return new ZrJavaLexer(level);
    }
}
