package com.by122006.zircon.ijplugin.util;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.tools.javac.parser.Formatter;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExArray;
import zircon.example.ExCollection;
import zircon.example.ExObject;

public class ZrUtil {
    public static Formatter checkPsiLiteralExpression(ASTNode psiElement) {
        if (psiElement == null) return null;
        final String text = psiElement.getText();
        return Formatter.getAllFormatters()
                .find(a -> text.startsWith(a.prefix() + "\""));
    }

    public static Formatter checkPsiLiteralExpression(PsiLiteralExpression psiElement) {
        return checkPsiLiteralExpression(psiElement.getNode());
    }

    public static boolean isJavaStringLiteral(PsiElement psiElement) {
        return psiElement instanceof PsiLiteralExpression
                && psiElement.getFirstChild() instanceof PsiJavaToken
                && ((PsiJavaToken) psiElement.getFirstChild()).getTokenType() == JavaTokenType.STRING_LITERAL;
    }

    public static boolean isInJavaStringLiteral(PsiElement psiElement) {
        return PsiTreeUtil.findFirstParent(psiElement, a -> a instanceof PsiClass && (((PsiClass) a).getName()?.equals("__ZRStringObj") ?: false)) != null;
    }

    public static boolean hasOptionalChaining(PsiElement element) {
        if (element instanceof PsiMethodCallExpression || element instanceof PsiReferenceExpression) {
            final PsiReferenceExpression methodExpression = element instanceof PsiReferenceExpression
                    ? (PsiReferenceExpression) element
                    : ((PsiMethodCallExpression) element).getMethodExpression();
            final @NotNull PsiElement[] children = methodExpression.getChildren();
            if (children.length <= 1) return false;
            final PsiElement firstElement = children.get(1);
            if (firstElement == null) return false;
            if (!(firstElement instanceof PsiJavaToken)) {
                return false;
            }
            final PsiJavaToken javaToken = (PsiJavaToken) children.get(1);
            if (javaToken.getTokenType() == JavaTokenType.DOT) {
                if (javaToken.getText().equals("?.")) {
                    return true;
                } else {
                    return hasOptionalChaining(children[0]);
                }
            }
            return false;
        }
        return false;

    }
}
