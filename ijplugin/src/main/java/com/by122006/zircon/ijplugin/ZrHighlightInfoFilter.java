package com.by122006.zircon.ijplugin;

import com.by122006.zircon.ijplugin.util.ZrPluginUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
        if (highlightInfo.getDescription() != null) {
            final String matchString = InspectionGadgetsBundle.message("simplifiable.conditional.expression.problem.descriptor", ".*", ".*")
                    .trim()
                    .replaceAll("<.*?>.*?</.*?>", "'.*'")
                    .replaceAll("#[a-zA-Z0-9]*", "")
                    .trim();
            if (highlightInfo.getDescription().matches(matchString)) {
                final int startOffset = highlightInfo.getStartOffset();
                if (startOffset <= 0) {
                    return false;
                }
                final PsiElement elementAt = file.findElementAt(startOffset);
                if (elementAt == null) return true;
                final ZrPsiConditionalExpressionImpl expr = PsiTreeUtil.getParentOfType(elementAt, ZrPsiConditionalExpressionImpl.class);
                if (expr == null) return true;
                return false;
            }
        }
        if (highlightInfo.getDescription() != null) {
            final String regex = JavaAnalysisBundle.message("dataflow.message.npe.method.invocation")
                    .replaceAll("<.*?>.*?</.*?>", "'.*'")
                    .replaceAll("#[a-zA-Z0-9]* ", "");
            final String regex2 = JavaAnalysisBundle.message("dataflow.message.npe.method.invocation.sure")
                    .replaceAll("<.*?>.*?</.*?>", "'.*'")
                    .replaceAll("#[a-zA-Z0-9]* ", "");

            if (highlightInfo.getDescription().matches(regex) || highlightInfo.getDescription().matches(regex2)) {
                final PsiElement elementAt = file.findElementAt(highlightInfo.getStartOffset());
                if (elementAt == null) {
                    return true;
                }
                final PsiElement parent = elementAt.getParent();
                if (!(parent instanceof PsiReferenceExpression)) {
                    return true;
                }
                final PsiElement prevSibling = elementAt.getPrevSibling().getPrevSibling();
                if (!(prevSibling instanceof PsiJavaToken)) {
                    return true;
                }
                if (((PsiJavaToken) prevSibling).getTokenType() != JavaTokenType.DOT) {
                    return true;
                }
                if (prevSibling.getText().equals("?.")) {
                    return false;
                }

                return true;
            }
        }
        if (highlightInfo.getDescription() != null) {
            final String matchString = JavaAnalysisBundle.message("dataflow.message.unboxing", ".*", ".*")
                    .trim()
                    .replaceAll("<.*?>.*?</.*?>", "'.*'")
                    .replaceAll("#[a-zA-Z0-9]* ", "")
                    .trim();
            if (highlightInfo.getDescription().matches(matchString) || highlightInfo.getDescription().contains("Unreachable code")) {
                final int startOffset = highlightInfo.getStartOffset();
                if (startOffset <= 0) {
                    return false;
                }
                final PsiElement elementAt = file.findElementAt(startOffset);
                if (elementAt == null) return true;
                final ZrPsiConditionalExpressionImpl expr = PsiTreeUtil.getParentOfType(elementAt, ZrPsiConditionalExpressionImpl.class);
                if (expr == null) return true;
                if (highlightInfo.getStartOffset() == expr.getStartOffset()) return false;
                if (highlightInfo.getStartOffset() == expr.getStartOffset() + (expr.getElseExpression() ?.getStartOffsetInParent() ?:
                0)){
                    return false;
                }
//                if (expr.getElseExpression() == highlightInfo.getStartOffset()) return false;
                return true;
            }
        }
        return true;
    }
}
