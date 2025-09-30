package com.by122006.zircon.ijplugin.v252;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class ZrParserDefinition implements ParserDefinition {
    JavaParserDefinition definition = new JavaParserDefinition();

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return definition.createLexer(project);
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return definition.createParser(project);
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return definition.getFileNodeType();
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return definition.getCommentTokens();
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return definition.getStringLiteralElements();
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode astNode) {
        return definition.createElement(astNode);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider fileViewProvider) {
        return definition.createFile(fileViewProvider);
    }
}
