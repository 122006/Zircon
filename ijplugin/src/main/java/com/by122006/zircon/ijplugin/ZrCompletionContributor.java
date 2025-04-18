package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExObject;
import zircon.example.ExReflection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;

public class ZrCompletionContributor extends CompletionContributor {
    ZirconSettings settings = ZirconSettings.getInstance();

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        doFillCompletionVariants(parameters, result);
    }

    public void doFillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        if (parameters.getCompletionType() != CompletionType.BASIC) return;
        if (!ZrPluginUtil.hasZrPlugin(parameters.getPosition())) {
            return;
        }
        final PsiElement position = parameters.getPosition();
        if (!isInJavaContext(position)) {
            return;
        }
        final PsiExpression qualifierExpression;
        if (position.getParent() instanceof PsiReferenceExpression) {
            qualifierExpression = ((PsiReferenceExpression) position.getParent()).getQualifierExpression();
        } else {
            System.out.println(position.getParent());
            return;
        }
        final PsiType erasure;
        if (qualifierExpression == null) {
            JBIterable.generate(position, PsiElement::getParent)
                    .takeWhile(e -> !(e instanceof PsiFile))
                    .filter(PsiClass.class)
                    .forEach(psiClass -> {
                        final PsiClassType classType = PsiTypesUtil.getClassType(psiClass);
                        checkBySiteType(result, position, classType, true);
                    });

        } else {
            final PsiType type = qualifierExpression.getType();
            if (type == null) {
                if (qualifierExpression instanceof PsiReferenceExpression) {
                    final PsiElement resolve = ((PsiReferenceExpression) qualifierExpression).resolve();
                    if (resolve instanceof PsiClass) {
                        erasure = PsiTypesUtil.getClassType((PsiClass) resolve);
                        checkBySiteType(result, position, erasure, false);
                    }
                }
            } else {
                checkBySiteType(result, position, type, false);
            }
        }
    }

    private void checkBySiteType(CompletionResultSet result, PsiElement position, PsiType psiType, boolean invokeDirectly) {
        try {
            if (TypeConversionUtil.isPrimitiveAndNotNull(psiType)) {
                return;
            }
            boolean isStatic;
            PsiElement resolve;
            if (position.getParent() instanceof PsiReferenceExpression) {
                resolve = ((PsiReferenceExpression) position.getParent()).getQualifierExpression();
            } else {
                System.out.println(position.getParent());
                return;
            }
//            if (psiType instanceof PsiArrayType) {
//                isStatic = false;
//                final PsiElementFactory factory = JavaPsiFacade.getInstance(position.getProject()).getElementFactory();
//                final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(position);
//                resolveClass = factory.getArrayClass(languageLevel);
//            } else if (resolve instanceof PsiExpression) {
//                isStatic = false;
//                resolveClass = PsiTypesUtil.getPsiClass(((PsiExpression) resolve).getType());
//            } else if (resolve instanceof PsiLocalVariable) {
//                isStatic = false;
//                resolveClass = PsiTypesUtil.getPsiClass(((PsiLocalVariable) resolve).getType());
//            } else if (resolve instanceof PsiParameter) {
//                isStatic = false;
//                resolveClass = PsiTypesUtil.getPsiClass(((PsiParameter) resolve).getType());
//            } else if (resolve instanceof PsiReference) {
//                isStatic = false;
//                resolveClass = (PsiClass) ((PsiReference) resolve).resolve();
//            } else if (resolve instanceof PsiClass) {
//                isStatic = true;
//                resolveClass = (PsiClass) resolve;
//            } else {
//                isStatic = PsiUtil.getEnclosingStaticElement(position, PsiTreeUtil.getParentOfType(position, PsiClass.class)) != null;
//                resolveClass = PsiTypesUtil.getPsiClass(psiType);
//            }

            if (resolve == null) {
                isStatic = PsiUtil.getEnclosingStaticElement(position, PsiTreeUtil.getParentOfType(position, PsiClass.class)) != null;
            } else {
                isStatic = resolve instanceof PsiClass;
            }

            List<LookupElement> list = new ArrayList<>();
            final List<ZrPsiAugmentProvider.CacheMethodInfo> cachedAllMethod = ZrPsiAugmentProvider.getCachedAllMethod(position);
            exMethod:
            for (ZrPsiAugmentProvider.CacheMethodInfo cacheMethodInfo : cachedAllMethod) {
                ProgressManager.checkCanceled();
                if (!invokeDirectly && cacheMethodInfo.shouldInvokeDirectly) continue;
                final List<PsiType> collect;
                if (cacheMethodInfo.isStatic) {
                    if (isStatic == Boolean.FALSE && !settings.allowUseStaticOnNoStaticMethod) {
                        continue;
                    }
                    collect = cacheMethodInfo.targetType
                            .stream()
                            .filter(b -> TypeConversionUtil.isAssignable(b, psiType) || b.equalsToText(JAVA_LANG_OBJECT))
                            .collect(Collectors.toList());
                } else {
                    if (isStatic == Boolean.TRUE) {
                        continue;
                    }
                    final PsiType type = cacheMethodInfo.method.getParameterList().getParameters()[0].getType();
                    final PsiMethod method = cacheMethodInfo.method;
                    boolean isAssignable = ZrPluginUtil.isAssignableSite(method, psiType);
                    if (isAssignable)
                        collect = List.of(TypeConversionUtil.erasure(type));
                    else {
                        collect = Collections.emptyList();
                    }
                }
                final List<PsiType> filterAnnotation = cacheMethodInfo.filterAnnotation;
                if (filterAnnotation != null && !filterAnnotation.isEmpty()) {
                    PsiClass psiClass = PsiTypesUtil.getPsiClass(psiType);
                    if (psiClass == null) continue;
                    for (PsiType type : filterAnnotation) {
                        final PsiAnnotation annotation = psiClass.getAnnotation(type.getCanonicalText(false));
                        if (annotation == null) continue exMethod;
                    }
                }
                if (collect.isEmpty()) continue;
                PsiClass resolveClass;
                if (cacheMethodInfo.isStatic) {
                    resolveClass = PsiTypesUtil.getPsiClass(psiType);
                } else {
                    final PsiParameter parameter = cacheMethodInfo.method.getParameterList()
                            .getParameter(0);
                    resolveClass = PsiTypesUtil.getPsiClass(parameter.getType());
                }
                final ZrPsiExtensionMethod method = ZrPsiAugmentProvider.buildMethodBy(cacheMethodInfo, resolveClass, psiType);
                if (method != null) {
                    @NotNull PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
                    LookupElementBuilder builder = LookupElementBuilder.create(method, method.getName())
                            .withIcon(method.getIcon(Iconable.ICON_FLAG_VISIBILITY))
                            .withPresentableText(method.getName())
                            .withTailText(PsiFormatUtil.formatMethod(method, substitutor,
                                    PsiFormatUtilBase.SHOW_PARAMETERS,
                                    PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE));
                    final PsiClass containingClass = cacheMethodInfo.method.getContainingClass();
                    builder = builder.withInsertHandler((context, item) -> {
                        try {
                            final Editor editor = context.getEditor();
                            Document document = context.getDocument();
                            int end;
                            if (editor instanceof EditorImpl) {
                                end = position.getTextOffset() + method.getName().length();
                                if (!document.getText(TextRange.create(end, end + 1)).equals("(")) {
                                    final PsiParameterList parameterList = method.getParameterList();
                                    if (parameterList.isEmpty()) {
                                        document.insertString(end, "()");
                                        try {
                                            editor.getCaretModel().moveToOffset(end + 2);
                                        } catch (Exception e) {
                                            //do nothing
                                        }
                                    } else {
                                        final String params = Arrays.stream(parameterList.getParameters())
                                                .filter(a -> !a.isVarArgs())
                                                .map(a -> "")
                                                .collect(Collectors.joining(", "));
                                        document.insertString(end, "(" + params + ")");
                                        try {
                                            editor.getCaretModel().moveToOffset(end + 1);
                                        } catch (Exception e) {
                                            //do nothing
                                        }
                                    }
                                }
                            } else {
                                final EditorEx nowEditor = (EditorEx) editor;
                                end = nowEditor.getExpectedCaretOffset();
                                if (!document.getText(TextRange.create(end, end + 1)).equals("(")) {
                                    final PsiParameterList parameterList = method.getParameterList();
                                    if (parameterList.isEmpty()) {
                                        document.insertString(end, "()");
                                    } else {
                                        final String params = Arrays.stream(parameterList.getParameters())
                                                .filter(a -> !a.isVarArgs())
                                                .map(a -> "")
                                                .collect(Collectors.joining(", "));
                                        document.insertString(end, "(" + params + ")");
                                    }
                                }
                            }

                            final Project project = editor.getProject();
                            if (project != null && containingClass != null) {
                                PsiDocumentManager.getInstance(project).commitDocument(document);
                                final String qualifiedName = containingClass.getQualifiedName();
                                if (qualifiedName != null) {
                                    final boolean canBeImported = ImportUtils.nameCanBeImported(qualifiedName, position) && ZrAnnotator.canImport(containingClass, position);
                                    if (canBeImported) {
//                                            if (!(editor instanceof EditorEx)) {
//                                                ImportUtils.addImportIfNeeded(containingClass, context.getFile());
//                                            } else {
                                        final VirtualFile virtualFile = editor.reflectionInvokeMethod("getVirtualFile");
                                        final PsiFile file = PsiManager.getInstance(project)
                                                .findFile(virtualFile);
                                        if (file != null) {
                                            ImportUtils.addImportIfNeeded(containingClass, file);
                                        }
//                                            }
                                    }
                                }

                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    final PsiType returnType = method.getReturnType();
                    if (returnType != null) {
                        builder = builder.withTypeText(substitutor.substitute(returnType)
                                .getPresentableText());
                    }
                    if (method.targetClass.equals(TypeConversionUtil.erasure(psiType))) {
                        builder = builder.bold();
                    }
                    final String name = containingClass == null ? "unknown" : containingClass.getName();
                    if (cacheMethodInfo.isStatic) {
                        builder = builder.appendTailText(" static for " + psiType.getPresentableText() + " by " + name, true);
                    } else {
                        final PsiParameter parameter = cacheMethodInfo.method.getParameterList()
                                .getParameter(0);
                        builder = builder.appendTailText(" for " + (parameter == null ? "unknown" : parameter
                                .getType().getPresentableText()) + " by " + name, true);
                    }
                    LookupElement add = builder;
                    add = PrioritizedLookupElement.withGrouping(add, cacheMethodInfo.isStatic ? 100 : 122006);
                    add = PrioritizedLookupElement.withPriority(add, cacheMethodInfo.isStatic ? -100 : -122006);
                    list.add(add);
                }
            }
            ;
            result.addAllElements(list);
        } catch (PsiInvalidElementAccessException e) {
            e.printStackTrace();
        }
    }

    public static boolean isInJavaContext(PsiElement position) {
        return PsiUtilCore.findLanguageFromElement(position).isKindOf(JavaLanguage.INSTANCE);
    }


}
