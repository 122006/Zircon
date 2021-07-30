package com.by122006.zircon.ijplugin;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;

public class ZrElementUtil {
    public static boolean isJavaStringLiteral(PsiElement psiElement){
        return psiElement instanceof PsiLiteralExpression
                && psiElement.getFirstChild() instanceof PsiJavaToken
                && ((PsiJavaToken) psiElement.getFirstChild()).getTokenType() == JavaTokenType.STRING_LITERAL;
    }
}
