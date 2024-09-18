package com.by122006.zircon.ijplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @ClassName: ZrDeReplaceAllAction
 * @Author: 122006
 * @Date: 2024/8/26 10:21
 * @Description:
 */
public class ZrDeReplaceAllAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(ZrReplaceAllAction.class.getName());

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            return;
        }
        final List<ZrPsiAugmentProvider.CacheMethodInfo> psiMethods = ZrPsiAugmentProvider.freshCachedAllMethod(project);
        if (psiMethods.isEmpty()) return;

        WriteCommandAction.runWriteCommandAction(project, () -> {
            for (ZrPsiAugmentProvider.CacheMethodInfo zrMethod : psiMethods) {
                if (zrMethod.cover) continue;
                List<Pair<PsiMethodCallExpression, ZrPsiExtensionMethod>> list = new ArrayList<>();
                final PsiMethod method = zrMethod.method;
                PsiSearchHelper.getInstance(project).processElementsWithWord((element, offsetInElement) -> {
                    if (element instanceof PsiJavaToken) {
                        final PsiElement parent = element.getParent().getParent();
                        if (parent instanceof PsiMethodCallExpression) {
                            final PsiMethod extMethod = ((PsiMethodCallExpression) parent).resolveMethod();
                            if (extMethod instanceof ZrPsiExtensionMethod) {
                                list.add(new Pair<>((PsiMethodCallExpression) parent, (ZrPsiExtensionMethod) extMethod));
                            }
                        }
                    }
                    return true;
                }, ProjectScope.getContentScope(project), zrMethod.name, UsageSearchContext.ANY, true);
                list.forEach((a) -> {
                    try {
                        final PsiMethodCallExpression parent = a.getFirst();
                        final ZrPsiExtensionMethod extMethod = a.getSecond();
                        final PsiClass containingClass = extMethod.getTargetMethod().getContainingClass();
                        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                        final PsiExpression[] expressions = parent.getArgumentList().getExpressions();
                        final String collect = Arrays.stream(expressions)
                                .map(PsiElement::getText).collect(Collectors.joining(","));
                        String s;
                        if (zrMethod.isStatic) {
                            s = containingClass.getQualifiedName() + "." + method.getName() + "(" + collect + ")";
                        } else {
                            final PsiElement site = parent.getMethodExpression().getFirstChild();
                            final String siteText = site.getText();
                            if (collect.isEmpty()) {
                                s = containingClass.getQualifiedName() + "." + method.getName() + "(" + siteText + ")";
                            } else {
                                s = containingClass.getQualifiedName() + "." + method.getName() + "(" + (siteText.isEmpty() ? "this" : siteText) + "," + collect + ")";
                            }
                        }
                        @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(s, parent);
                        try {
                            parent.replace(codeBlockFromText);
                            LOG.warn("success replace " + parent.getText() + " to " + codeBlockFromText);
                        } catch (Exception ex) {
                            LOG.warn("fail replace " + parent.getText() + " to " + codeBlockFromText);
                            ex.printStackTrace();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

            }
        });

    }

    public PsiMethod[] getMapping(Project project, ZrPsiAugmentProvider.CacheMethodInfo cacheMethodInfo) {
        final List<PsiMethod> list = cacheMethodInfo.targetType.stream().map(type -> {
            PsiClass psiClass;
            if (type instanceof PsiClassReferenceType) {
                final String qualifiedName = ((PsiClassReferenceType) type).getReference().getQualifiedName();
                final PsiClass[] classes = JavaPsiFacade.getInstance(project)
                        .findClasses(qualifiedName, GlobalSearchScope.allScope(project));
                psiClass = classes.length > 0 ? classes[0] : null;
            } else {
                psiClass = PsiTypesUtil.getPsiClass(type);

            }
            if (psiClass == null) return null;
            LOG.warn(" psiClass:" + psiClass.getQualifiedName());
            return ZrPsiAugmentProvider.buildMethodBy(cacheMethodInfo.isStatic, psiClass, cacheMethodInfo.method, PsiTypesUtil.getClassType(psiClass));
        }).filter(Objects::nonNull).collect(Collectors.toList());
        return list.toArray(PsiMethod.class);
    }
}
