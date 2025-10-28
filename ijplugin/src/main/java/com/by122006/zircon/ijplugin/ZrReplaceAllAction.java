package com.by122006.zircon.ijplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExArray;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @ClassName: ZrReplaceAllAction
 * @Author: 122006
 * @Date: 2024/8/26 9:22
 * @Description:
 */
public class ZrReplaceAllAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(ZrReplaceAllAction.class.getName());

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            return;
        }
        final List<ZrPsiAugmentProvider.CacheMethodInfo> psiMethods = ZrPsiAugmentProvider.freshCachedAllMethod(project);
        WriteCommandAction.runWriteCommandAction(project, () -> {
            for (ZrPsiAugmentProvider.CacheMethodInfo zrMethod : psiMethods) {
                if (zrMethod.cover || zrMethod.isStatic) continue;
                final Collection<PsiReference> all = ReferencesSearch.search(zrMethod.method, ProjectScope.getProjectScope(project)).findAll();
                for (PsiReference reference : all) {
                    if (!(reference instanceof PsiReferenceExpression)) {
                        LOG.info("unknown reference type" + reference.getClass().getSimpleName() + " by method" + reference.resolve());
                        continue;
                    }
                    final PsiElement parent = ((PsiReferenceExpressionImpl) reference).getParent();
                    if (!(parent instanceof PsiMethodCallExpression)) {
                        LOG.info("unknown reference parent type" + parent.getClass().getSimpleName() + " by method" + reference.resolve());
                        continue;
                    }
                    final PsiMethodCallExpression element = (PsiMethodCallExpression) parent;
                    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                    final PsiExpression[] expressions = element.getArgumentList().getExpressions();
                    final String collect = Arrays.stream(expressions)
                            .map(PsiElement::getText).collect(Collectors.joining(","));
                    LOG.info(collect);
                    if (expressions.size() == 0) {
                        continue;
                    }
                    final String skip1 = Arrays.stream(expressions)
                            .map(PsiElement::getText).skip(1).collect(Collectors.joining(","));
                    String s = expressions.get(0).getText() + "." + zrMethod.method.getName() + "(" + skip1 + ")";
                    @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(s, element);
                    element.replace(codeBlockFromText);

                }
            }
        });
    }
}
