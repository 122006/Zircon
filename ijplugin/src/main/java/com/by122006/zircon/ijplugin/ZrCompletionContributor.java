package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.EqTailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.Consumer;
import com.intellij.util.containers.JBIterable;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;

public class ZrCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        if (parameters.getCompletionType() != CompletionType.BASIC) return;
        if (!ZrPluginUtil.hasZrPlugin(parameters.getEditor().getProject())){
            return;
        }
        final PsiElement position = parameters.getPosition();
        if (!isInJavaContext(position)) {
            return;
        }
        if (position.getParent() instanceof PsiReferenceExpressionImpl) {
            final PsiExpression qualifierExpression = ((PsiReferenceExpressionImpl) position.getParent()).getQualifierExpression();
            final PsiType erasure;
            if (qualifierExpression == null) {
                JBIterable.generate(position, PsiElement::getParent)
                        .takeWhile(e -> !(e instanceof PsiFile))
                        .filter(PsiClass.class)
                        .forEach(psiClass -> {
                            final PsiClassType classType = PsiTypesUtil.getClassType(psiClass);
                            checkBySiteType(result, position, classType);
                        });

            } else {
                final PsiType type = qualifierExpression.getType();
                erasure = TypeConversionUtil.erasure(type);
                if (erasure == null) return;
                checkBySiteType(result, position, erasure);
            }
        } else {
            System.out.println(position.getParent());
        }
    }

    private void checkBySiteType(CompletionResultSet result, PsiElement position, PsiType erasure) {
        final PsiClass aClass = JavaPsiFacade.getInstance(position.getProject()).findClass(JAVA_LANG_OBJECT, position.getResolveScope());
        ZrPsiAugmentProvider.getCachedAllMethod(position.getProject()).forEach(cacheMethodInfo -> {
            ProgressManager.checkCanceled();
            cacheMethodInfo.targetType.stream().filter(b -> TypeConversionUtil.isAssignable(b, erasure) || b.equalsToText(JAVA_LANG_OBJECT))
                    .forEach(targetType -> {
                        PsiClass psiClass;
                        if (targetType instanceof PsiArrayType) {
                            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(position.getProject());
                            psiClass = elementFactory.getArrayClass(PsiUtil.getLanguageLevel(position.getProject()));
                        } else if (targetType instanceof PsiClassType) {
                            psiClass = PsiTypesUtil.getPsiClass(targetType);
                            if (targetType.equalsToText(JAVA_LANG_OBJECT)) {
                                psiClass = aClass;
                            }
                        } else {
                            System.err.println("checkBySiteType fail :" + targetType.getClass());
                            return;
                        }
                        if (psiClass == null) {
                            return;
                        }
                        final ZrPsiAugmentProvider.ZrPsiExtensionMethod method = ZrPsiAugmentProvider.buildMethodBy(cacheMethodInfo.isStatic, psiClass, cacheMethodInfo.method, erasure);
                        if (method != null) {
                            @NotNull PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
                            LookupElementBuilder builder = LookupElementBuilder.create(method, method.getName())
                                    .withIcon(method.getIcon(Iconable.ICON_FLAG_VISIBILITY))
                                    .withPresentableText(method.getName())
                                    .withTailText(PsiFormatUtil.formatMethod(method, substitutor,
                                            PsiFormatUtilBase.SHOW_PARAMETERS,
                                            PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE));
                            final PsiClass containingClass = cacheMethodInfo.method.getContainingClass();
                            builder = builder.withInsertHandler((InsertHandler) (context, item) -> {
                                final Editor editor = context.getEditor();
//                        context.commitDocument();
                                final Document document = context.getDocument();
                                final int end = position.getTextOffset() + method.getName().length();
                                if (!document.getText(TextRange.create(end, end + 1)).equals("(")) {
                                    final PsiParameterList parameterList = method.getParameterList();
                                    if (parameterList.isEmpty()) {
                                        document.insertString(end, "()");
                                        editor.getCaretModel().moveToOffset(end + 2);
                                    } else {
                                        final String params = Arrays.stream(parameterList.getParameters())
                                                .map(a -> "")
                                                .collect(Collectors.joining(", "));
                                        document.insertString(end, "(" + params + ")");
                                        editor.getCaretModel().moveToOffset(end + 1);
                                    }
                                }
                                PsiDocumentManager.getInstance(editor.getProject()).commitDocument(document);
                                final String qualifiedName = containingClass.getQualifiedName();
                                final boolean canBeImported = ImportUtils.nameCanBeImported(qualifiedName, position) && ZrAnnotator.canImport(containingClass, position);
                                if (canBeImported) {
                                    ImportUtils.addImportIfNeeded(containingClass, position.getParent());//为啥不生效呢
                                }
                            });
                            final PsiType returnType = method.getReturnType();
                            if (returnType != null) {
                                builder = builder.withTypeText(substitutor.substitute(returnType).getPresentableText());
                            }
                            if (targetType.equals(erasure)){
                                builder = builder.bold();
                            }
                            final String name = containingClass == null ? "unknown" : containingClass.getName();
                            if (cacheMethodInfo.isStatic) {
                                builder = builder.appendTailText(" static for " + targetType.getPresentableText() + " by " + name, true);
                            } else {
                                final PsiParameter parameter = cacheMethodInfo.method.getParameterList().getParameter(0);
                                builder = builder.appendTailText(" for " + (parameter == null ? "unknown" : parameter.getType().getPresentableText()) + " by " + name, true);
                            }
                            result.addElement(builder);
                        }
                    });
        });
    }

    public static boolean isInJavaContext(PsiElement position) {
        return PsiUtilCore.findLanguageFromElement(position).isKindOf(JavaLanguage.INSTANCE);
    }


}
