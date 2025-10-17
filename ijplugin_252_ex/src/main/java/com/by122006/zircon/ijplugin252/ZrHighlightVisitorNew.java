package com.by122006.zircon.ijplugin252;

import com.by122006.zircon.ijplugin.ZrCheckLevelHighlightInfoHolder;
import com.by122006.zircon.ijplugin.util.ZrUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExCollection;
import zircon.example.ExObject;
import zircon.example.ExReflection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ZrHighlightVisitorNew extends HighlightVisitorImpl {
    Logger logger = Logger.getInstance(ZrHighlightVisitorNew.class);
    ZrCheckLevelHighlightInfoHolder holder;

    public boolean suitableForFile(@NotNull PsiFile psiFile) {
        return super.suitableForFile(psiFile);
    }

    public @NotNull ZrHighlightVisitorNew clone() {
        return new ZrHighlightVisitorNew();
    }


    @Override
    public void visit(@NotNull PsiElement psiElement) {
        super.visit(psiElement);
        if (holder != null && psiElement instanceof PsiLiteralExpression && psiElement.getContainingFile() != null && psiElement.getContainingFile().isPhysical()) {
            @NotNull PsiLiteralExpression expression = (PsiLiteralExpression) psiElement;
            final String text = expression.getText();
            final Formatter formatter = ZrUtil.checkPsiLiteralExpression(expression);
            if (formatter != null) {
                highlightZrLiteralExpression2(psiElement, expression, text, formatter);
            }
        }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
    }

    private void highlightZrLiteralExpression2(@NotNull PsiElement psiElement, @NotNull PsiLiteralExpression expression, String text, Formatter formatter) {
        final ZrStringModel model = formatter.build(text);
        final List<Pair<String, PsiExpression>> collect = model.getList().filter(b -> b.codeStyle == 1)
                .map(b -> {
                    try {
                        if (!expression.getParent().isValid()) return null;
                        final PsiExpression expressionFromText = JavaPsiFacade
                                .getElementFactory(expression.getProject())
                                .createExpressionFromText(b.stringVal.trim(), expression.getParent());
                        return Pair.create(b.stringVal, expressionFromText);
                    } catch (ProcessCanceledException e) {
                        throw e;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                });

        model.getList().filter(b -> b.codeStyle == 1)
                .forEach(b -> {
                    final Pair<String, PsiExpression> expressionFromTextPair = collect.find(a -> Objects.equals(a.first, b.stringVal));
                    if (expressionFromTextPair == null) return;
                    PsiExpression expressionFromText = expressionFromTextPair.getSecond();
                    if (expressionFromText == null) return;
                    final TextRange textRange = expression.getTextRange();
                    int startOffset = textRange.getStartOffset();
                    startOffset += b.startIndex;
                    try {
                        holder.updateStartIndex(startOffset);
                        holder.setPsiElement(expressionFromText);
                        super.visit(expressionFromText);
                        Object myCollector = this.reflectionFieldValue("myCollector");
                        if (myCollector != null) {
                            final @NotNull List<PsiMethodCallExpression> methodCallExpressionList = findExpressions(expressionFromText, PsiMethodCallExpression.class);
                            methodCallExpressionList.forEach(exp -> {
                                final PsiMethod psiMethod = exp.resolveMethod();
                                if (psiMethod == null) {
                                    ((JavaErrorCollector) myCollector).processElement(exp.getMethodExpression());
                                }
                            });
                        } else {
                            final @NotNull List<PsiMethodCallExpression> methodCallExpressionList = findExpressions(expressionFromText, PsiMethodCallExpression.class);
                            methodCallExpressionList.forEach(exp -> {
                                final PsiMethod psiMethod = exp.resolveMethod();
                                if (psiMethod == null) {
                                    this.reflectionInvokeMethod("visitReferenceExpression", exp.getMethodExpression());
                                }
                            });
                        }
                    } finally {
                        holder.updateStartIndex(0);
                    }
                });
    }

    public static <T extends PsiElement> List<T> findExpressions(PsiElement root, Class<T> clazz) {
        List<T> methodCalls = new ArrayList<>();
        root.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element.isInstanceOf(clazz)) {
                    methodCalls.add((T) element);
                }
                super.visitElement(element);
            }
        });
        return methodCalls;
    }

    @Override
    public boolean analyze(@NotNull PsiFile psiFile, boolean b, @NotNull HighlightInfoHolder highlightInfoHolder, @NotNull Runnable runnable) {
        final boolean analyze;
        try {
            holder = new ZrCheckLevelHighlightInfoHolder(psiFile, highlightInfoHolder, 0);
            analyze = super.analyze(psiFile, b, holder, runnable);
        } finally {
            holder = null;
        }
        return analyze;
    }
}

