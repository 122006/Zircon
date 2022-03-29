package com.by122006.zircon.util;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.sun.tools.javac.parser.Formatter;
import org.jetbrains.annotations.NotNull;

public class ZrUtil {
    public static Formatter checkPsiLiteralExpression(ASTNode psiElement) {
        if (psiElement == null) return null;
        final String text = psiElement.getText();
        final Formatter formatter = Formatter.getAllFormatters().stream()
                .filter(a -> text.startsWith(a.prefix() + "\"" ))
                .findFirst()
                .orElse(null);
        return formatter;
    }
    public static Formatter checkPsiLiteralExpression(PsiLiteralExpression psiElement) {
        return checkPsiLiteralExpression(psiElement.getNode());
    }

    public static boolean isJavaStringLiteral(PsiElement psiElement){
        return psiElement instanceof PsiLiteralExpression
                && psiElement.getFirstChild() instanceof PsiJavaToken
                && ((PsiJavaToken) psiElement.getFirstChild()).getTokenType() == JavaTokenType.STRING_LITERAL;
    }
}
