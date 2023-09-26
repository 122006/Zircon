package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrUtil;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

public class ZrHighlightVisitor implements HighlightVisitor, DumbAware {
    Logger logger = Logger.getInstance(ZrHighlightVisitor.class);
    HighlightInfoHolder holder;

    public HighlightVisitorImpl getHighlightVisitor(Project project) {
        final HighlightVisitor[] extensions = HighlightVisitor.EP_HIGHLIGHT_VISITOR.getExtensions(project);
        HighlightVisitorImpl highlightVisitor = (HighlightVisitorImpl) Arrays.stream(extensions).filter(a -> a instanceof HighlightVisitorImpl).findFirst().orElse(null);
        return highlightVisitor;
    }

    @Override
    public boolean suitableForFile(@NotNull PsiFile file) {
        return file instanceof PsiImportHolder && file.getLanguage() == JavaLanguage.INSTANCE && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
    }

//    Object refCountHolder = null;

    @Override
    public void visit(@NotNull PsiElement psiElement) {
//        if (psiElement instanceof PsiMethodCallExpression) {
//            final PsiMethod method = ((PsiMethodCallExpression) psiElement).resolveMethod();
//            if (method instanceof ZrPsiAugmentProvider.ZrPsiExtensionMethod) {
//                try {
//                    if (refCountHolder != null) {
//                        final Method registerImportStatement = refCountHolder.getClass().getDeclaredMethod("registerImportStatement", PsiReference.class, PsiImportStatementBase.class);
//                        final PsiReference reference = method.getReference();
//                        final PsiImportStatement importStatement = PsiElementFactory.getInstance(psiElement.getProject()).createImportStatement(((ZrPsiAugmentProvider.ZrPsiExtensionMethod) method).targetMethod.getContainingClass());
//                        registerImportStatement.invoke(registerImportStatement, reference, importStatement);
//                    }
//                } catch (Exception e) {
//                    logger.warn("不支持的idea版本", e);
//                }
//            }
//        }

        if (psiElement instanceof PsiLiteralExpression && psiElement.getContainingFile() != null && psiElement.getContainingFile().isPhysical()) {
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
                                throw e;
                            } catch (Exception e) {
                                e.printStackTrace();
                                return;
                            }
                            final HighlightVisitorImpl highlightVisitor = getHighlightVisitor(psiElement.getProject());
                            Consumer<PsiElement> consumer = new Consumer<PsiElement>() {
                                @Override
                                public void accept(PsiElement elem) {
                                    final PsiElement[] children = elem.getChildren();
                                    for (PsiElement c : children) {
                                        accept(c);
                                    }
                                    try {
                                        highlightVisitor.visit(elem);
                                    } catch (ProcessCanceledException e) {
                                        throw e;
                                    } catch (Exception e) {
                                        ;
                                    }
                                }
                            };

                            final TextRange textRange = expression.getTextRange();
                            int startOffset = textRange.getStartOffset();
                            startOffset += b.startIndex;
                            try {
                                final HighlightInfoHolder myHolder = getMyHolder(highlightVisitor);
                                if (myHolder == null) {
                                    logger.warn("myHolder==null");
                                }
                                if (psiElement.getContainingFile() == null) {
                                    logger.warn("getContainingFile()==null");
                                    return;
                                }
                                final ZrCheckLevelHighlightInfoHolder newHolder = new ZrCheckLevelHighlightInfoHolder(psiElement.getContainingFile(), holder, startOffset);
                                setMyHolder(highlightVisitor, newHolder);
                                newHolder.setPsiElement(expressionFromText);
                                consumer.accept(expressionFromText);
                                newHolder.setPsiElement(null);
                                if (myHolder != null)
                                    setMyHolder(highlightVisitor, myHolder);
                            } catch (ReflectiveOperationException e) {
                                e.printStackTrace();
                                logger.error("ZirconString的错误检查功能不支持该idea版本:" + e);
                            } catch (ProcessCanceledException e) {
                                throw e;
                            } catch (AssertionError e) {
                                logger.warn(e);
                            } catch (Exception e) {
                                logger.error(e);
//                                            holder.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression, startOffset + errorElement[0].getTextRange().getStartOffset(), startOffset + errorElement[0].getTextRange().getEndOffset()).create());
                            }
                        });
            }
        }
    }

    private HighlightInfoHolder getMyHolder(@NotNull HighlightVisitorImpl highlightVisitor) throws NoSuchFieldException, IllegalAccessException {
        final Field myHolder = HighlightVisitorImpl.class.getDeclaredField("myHolder");
        myHolder.setAccessible(true);
        return (HighlightInfoHolder) myHolder.get(highlightVisitor);
    }

    private void setMyHolder(@NotNull HighlightVisitorImpl highlightVisitor, @NotNull HighlightInfoHolder highlightInfoHolder) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        final Method myHolder = HighlightVisitorImpl.class.getDeclaredMethod("prepare", HighlightInfoHolder.class, PsiFile.class);
        myHolder.setAccessible(true);
        myHolder.invoke(highlightVisitor, highlightInfoHolder, highlightInfoHolder.getContextFile());
    }

    @Override
    public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable highlight) {
        this.holder = holder;
        if (!file.isPhysical()) {
            return true;
        }
        try {
            highlight.run();
        } finally {
            this.holder = null;
        }
        file.putUserData(ZrHighlightInfoFilter.CACHE_IMPORT_EXMETHOD,null);
//        try {
//            final Field[] declaredFields = highlight.getClass().getDeclaredFields();
//            final Optional<Field> first = Arrays.stream(declaredFields).filter(a -> {
//                final Class<?> type = a.getType();
//                return type.isArray() && type.getComponentType() == HighlightVisitor.class;
//            }).findFirst();
//            if (first.isPresent()){
//                Field field=first.get();
//                field.setAccessible(true);
//                final HighlightVisitor[] o = (HighlightVisitor[])field.get(highlight);
//                final Optional<HighlightVisitor> highlightVisitor = Arrays.stream(o).filter(a -> a instanceof HighlightVisitorImpl).findFirst();
//                if (highlightVisitor.isPresent()){
//                    final HighlightVisitorImpl highlightVisitorImpl = (HighlightVisitorImpl) highlightVisitor.get();
//                    final Field myRefCountHolder = HighlightVisitorImpl.class.getDeclaredField("myRefCountHolder");
//                    myRefCountHolder.setAccessible(true);
//                    refCountHolder = myRefCountHolder.get(highlightVisitorImpl);
//                }
//            };
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return true;
    }

    @NotNull
    public ZrHighlightVisitor clone() {
        return new ZrHighlightVisitor();
    }
}

