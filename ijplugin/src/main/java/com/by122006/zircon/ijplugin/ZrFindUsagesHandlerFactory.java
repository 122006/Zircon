package com.by122006.zircon.ijplugin;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.ExMethod;

import java.util.*;
import java.util.stream.Collectors;

public class ZrFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        if (!(element instanceof PsiMethod)) return false;
        if (DumbService.isDumb(element.getProject())) return false;
        final PsiAnnotation annotation = ((PsiMethod) element).getAnnotation(ExMethod.class.getName());
        return annotation != null;
    }

    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
        return new FindUsagesHandler(element) {
            @NotNull
            @Override
            public PsiElement[] getSecondaryElements() {
                final List<ZrPsiAugmentProvider.CacheMethodInfo> psiMethods
                        = ZrPsiAugmentProvider.getCachedAllMethod(element.getProject());
                final Optional<ZrPsiAugmentProvider.CacheMethodInfo> first = psiMethods.stream().filter(a -> a.method == element)
                        .findFirst();
                if (first.isEmpty()) return PsiElement.EMPTY_ARRAY;
                final ZrPsiAugmentProvider.CacheMethodInfo cacheMethodInfo = first.get();
                final List<PsiMethod> list = cacheMethodInfo.targetType.stream().map(type -> {
                    final PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
                    if (psiClass == null) return null;
                    return ZrPsiAugmentProvider.buildMethodBy(cacheMethodInfo.isStatic, psiClass, cacheMethodInfo.method);
                }).filter(Objects::nonNull).collect(Collectors.toList());
                return list.toArray(PsiElement.EMPTY_ARRAY);
            }
        };
    }
}
