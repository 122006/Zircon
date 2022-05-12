package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrUtil;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.StringRange;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ZrHighlightVisitor implements HighlightVisitor, DumbAware {
    Logger logger = Logger.getInstance(ZrHighlightVisitor.class);
    HighlightInfoHolder holder;
    HighlightVisitorImpl highlightVisitor;

    public HighlightVisitorImpl getHighlightVisitor(Project project) {
        if (highlightVisitor != null)
            return highlightVisitor;
        final HighlightVisitor[] extensions = HighlightVisitorImpl.EP_HIGHLIGHT_VISITOR.getExtensions(project);
        highlightVisitor = (HighlightVisitorImpl) Arrays.stream(extensions).filter(a -> a instanceof HighlightVisitorImpl).findFirst().orElse(null);
        return highlightVisitor;
    }

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
        return file instanceof PsiImportHolder && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
    }

    @Override
    public void visit(@NotNull PsiElement psiElement) {
        if (psiElement instanceof PsiLiteralExpression) {
            @NotNull PsiLiteralExpression expression = (PsiLiteralExpression) psiElement;
            final String text = expression.getText();
            final Formatter formatter = ZrUtil.checkPsiLiteralExpression(expression);
            if (formatter != null) {
                final ZrStringModel model = formatter.build(text);
                model.getList().stream().filter(b -> b.codeStyle == 1)
                        .forEach(b -> {
                            final PsiElement expressionFromText;
                            try {
                                expressionFromText = JavaPsiFacade
                                        .getElementFactory(expression.getProject())
                                        .createExpressionFromText(b.stringVal.trim(), expression.getParent());
                            } catch (ProcessCanceledException e) {
                                return;
                            } catch (Exception e) {
                                return;
                            }
                            final HighlightVisitorImpl highlightVisitor = getHighlightVisitor(psiElement.getProject());
                            Consumer<PsiElement> consumer = null;
                            consumer = new Consumer<>() {
                                @Override
                                public void accept(PsiElement elem) {
                                    final PsiElement[] children = elem.getChildren();
                                    for (PsiElement c : children) {
                                        accept(c);
                                    }
                                    highlightVisitor.visit(elem);
                                }
                            };
                            final TextRange textRange = expression.getTextRange();
                            int startOffset = textRange.getStartOffset();
                            startOffset += b.startIndex;
                            try {
                                final HighlightInfoHolder myHolder = getMyHolder(highlightVisitor);
                                if (myHolder == null) {
                                    return;
                                }
                                final ZrCheckLevelHighlightInfoHolder newHolder = new ZrCheckLevelHighlightInfoHolder(myHolder.getContextFile(), holder, startOffset);
                                setMyHolder(highlightVisitor, newHolder);
                                newHolder.setPsiElement(expressionFromText);
                                consumer.accept(expressionFromText);
                                newHolder.setPsiElement(null);
                                setMyHolder(highlightVisitor, myHolder);
                            } catch (ReflectiveOperationException e) {
                                e.printStackTrace();
                                logger.error( "ZirconString的错误检查功能不支持该idea版本:" + e);
                            } catch (ProcessCanceledException e) {
                                throw e;
                            } catch (AssertionError e) {
                                logger.warn(e);
                            } catch (Exception e) {
                                e.printStackTrace();
                                logger.error(e);
//                                            holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression, startOffset + errorElement[0].getTextRange().getStartOffset(), startOffset + errorElement[0].getTextRange().getEndOffset()).create());
                            }
                        });
            }
        }
    }

    private HighlightInfoHolder getMyHolder(HighlightVisitorImpl highlightVisitor) throws NoSuchFieldException, IllegalAccessException {
        final Field myHolder = highlightVisitor.getClass().getDeclaredField( "myHolder");
        myHolder.setAccessible(true);
        return (HighlightInfoHolder) myHolder.get(highlightVisitor);
    }

    private void setMyHolder(HighlightVisitorImpl highlightVisitor, HighlightInfoHolder highlightInfoHolder) throws NoSuchFieldException, IllegalAccessException {
        final Field myHolder = highlightVisitor.getClass().getDeclaredField( "myHolder");
        myHolder.setAccessible(true);
        myHolder.set(highlightVisitor, highlightInfoHolder);
    }

    @Override
    public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable highlight) {
        this.holder = holder;
        if (!file.isPhysical()) {
            return true;
        }
        highlight.run();
        return true;
    }

    @NotNull
    public ZrHighlightVisitor clone() {
        return new ZrHighlightVisitor();
    }
}
