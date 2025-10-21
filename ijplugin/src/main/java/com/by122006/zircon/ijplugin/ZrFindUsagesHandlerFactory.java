package com.by122006.zircon.ijplugin;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.ExMethod;
import zircon.example.ExCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ZrFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        if (!(element instanceof PsiMethod)) return false;
        final PsiAnnotation annotation = ((PsiMethod) element).getAnnotation(ExMethod.class.getName());
        return annotation != null;
    }

    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
        @Nullable Module module = ModuleUtilCore.findModuleForPsiElement(element.getContainingFile());
        if (module == null) return null;
        final GlobalSearchScope searchScope = module.getModuleWithDependenciesAndLibrariesScope(true);
        final Project project = element.getProject();
        final List<ZrPsiAugmentProvider.CacheMethodInfo> psiMethods
                = ZrPsiAugmentProvider.getCachedAllMethod(element);
        return new FindUsagesHandler(element) {
            @NotNull
            public PsiElement[] getSecondaryElements() {
                try {
                    final ZrPsiAugmentProvider.CacheMethodInfo cacheMethodInfo = psiMethods.find(a -> a.method.isEquivalentTo(element));
                    if (cacheMethodInfo == null) return PsiElement.EMPTY_ARRAY;
                    final List<ZrPsiExtensionMethod> list = cacheMethodInfo.targetType.map(type -> {
                        PsiClass psiClass;
                        if (type instanceof PsiClassReferenceType) {
                            final String qualifiedName = ((PsiClassReferenceType) type).getReference().getQualifiedName();
                            final PsiClass[] classes = JavaPsiFacade.getInstance(project)
                                    .findClasses(qualifiedName, searchScope);
                            psiClass = classes.length > 0 ? classes[0] : null;
                        } else {
                            psiClass = PsiTypesUtil.getPsiClass(type);
                        }
                        if (psiClass == null) return null;
                        return ZrPsiAugmentProvider.buildMethodBy(cacheMethodInfo, psiClass, PsiTypesUtil.getClassType(psiClass));
                    }).filterNoNull();
                    return list.toArray(PsiElement.EMPTY_ARRAY);
                } catch (PsiInvalidElementAccessException e) {
                    e.printStackTrace();
                    return new PsiElement[0];
                }
            }
        };
    }
}
