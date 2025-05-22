package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.by122006.zircon.util.ZrPluginUtil.hasOptionalChaining;

public class ZrHighlightInfoFilter implements HighlightInfoFilter {
    public static final Key<Set<String>> CACHE_IMPORT_EXMETHOD = Key.create("ZrHighlightInfoFilter_CACHE_IMPORT_EXMETHOD");

    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
        if (file == null || file.getLanguage() != JavaLanguage.INSTANCE) return true;
        if (!ZrPluginUtil.hasZrPlugin(file)) return true;
        if (Objects.equals(highlightInfo.getDescription(), JavaAnalysisBundle.message("unused.import.statement"))) {
            final PsiElement elementAt = file.findElementAt(highlightInfo.getStartOffset());
            if (elementAt == null) return true;
            final PsiImportStatement importStatement = PsiTreeUtil.getParentOfType(elementAt, PsiImportStatement.class);
            if (importStatement == null) return true;
            final String qualifiedName = importStatement.getQualifiedName();
            Set<String> cacheExMethodClasses;
            if ((file.getUserData(CACHE_IMPORT_EXMETHOD)) == null) {
                cacheExMethodClasses = new HashSet<>();
                file.putUserData(CACHE_IMPORT_EXMETHOD, cacheExMethodClasses);
                file.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                        super.visitMethodCallExpression(expression);
                        final PsiElement resolve = expression.resolveMethod();
                        if (resolve instanceof ZrPsiExtensionMethod) {
                            final PsiClass containingClass = ((ZrPsiExtensionMethod) resolve).targetMethod.getContainingClass();
                            if (containingClass == null) return;
                            cacheExMethodClasses.add(containingClass.getQualifiedName());
                        }
                    }

                    @Override
                    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
                        super.visitMethodReferenceExpression(expression);
                        final PsiElement resolve = expression.resolve();
                        if (resolve instanceof ZrPsiExtensionMethod) {
                            final PsiClass containingClass = ((ZrPsiExtensionMethod) resolve).targetMethod.getContainingClass();
                            if (containingClass == null) return;
                            cacheExMethodClasses.add(containingClass.getQualifiedName());
                        }
                    }
                });
            } else {
                cacheExMethodClasses = file.getUserData(CACHE_IMPORT_EXMETHOD);
            }
            return !cacheExMethodClasses.contains(qualifiedName);
        }
        if (highlightInfo.getDescription() != null && highlightInfo.getDescription().matches(JavaErrorBundle.message("binary.operator.not.applicable", ".*", ".*", ".*"))) {
            final PsiElement elementAt = file.findElementAt(highlightInfo.getStartOffset());
            if (elementAt == null) return true;
            final PsiBinaryExpression expr = PsiTreeUtil.getParentOfType(elementAt, PsiBinaryExpression.class);
            if (expr == null) return true;
            if (expr.getOperationTokenType() == JavaTokenType.OROR
                    && expr.getLOperand() != null && expr.getLOperand().getType() != null && ZrPluginUtil.hasOptionalChaining(expr.getLOperand())
                    && expr.getROperand() != null && expr.getROperand().getType() != null
                    && TypeConversionUtil.isAssignable(expr.getLOperand().getType(), expr.getROperand().getType())) {
                return false;
            }
        }
        return true;
    }
}
