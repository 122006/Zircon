package com.by122006.zircon.ijplugin.v252;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @ClassName: ZrJavaPsiFacadeImpl
 * @Author: 122006
 * @Date: 2025/10/10 17:41
 * @Description:
 */
public class ZrJavaPsiFacadeImpl extends JavaPsiFacadeEx {
    JavaPsiFacadeImpl javaPsiFacade;

    public ZrJavaPsiFacadeImpl(Project project) {
        javaPsiFacade = new JavaPsiFacadeImpl(project, null);
    }

    public ZrJavaPsiFacadeImpl(@NotNull Project project, @Nullable CoroutineScope coroutineScope) {
        javaPsiFacade = new JavaPsiFacadeImpl(project, coroutineScope);
    }

    @Override
    public @Nullable PsiClass findClass(@NonNls @NotNull String s, @NotNull GlobalSearchScope globalSearchScope) {
        return javaPsiFacade.findClass(s, globalSearchScope);
    }

    @Override
    public PsiClass @NotNull [] findClasses(@NonNls @NotNull String s, @NotNull GlobalSearchScope globalSearchScope) {
        return javaPsiFacade.findClasses(s, globalSearchScope);
    }

    @Override
    public boolean hasClass(@NonNls @NotNull String s, @NotNull GlobalSearchScope globalSearchScope) {
        return javaPsiFacade.hasClass(s, globalSearchScope);
    }

    @Override
    public @Nullable PsiPackage findPackage(@NonNls @NotNull String s) {
        return javaPsiFacade.findPackage(s);
    }

    @Override
    public @Nullable PsiJavaModule findModule(@NotNull String s, @NotNull GlobalSearchScope globalSearchScope) {
        return javaPsiFacade.findModule(s, globalSearchScope);
    }

    @Override
    public @NotNull Collection<PsiJavaModule> findModules(@NotNull String s, @NotNull GlobalSearchScope globalSearchScope) {
        return javaPsiFacade.findModules(s, globalSearchScope);
    }

    @Override
    public @NotNull PsiElementFactory getElementFactory() {
        return javaPsiFacade.getElementFactory();
    }

    @Override
    public @NotNull PsiJavaParserFacade getParserFacade() {
        return javaPsiFacade.getParserFacade();
    }

    @Override
    public @NotNull PsiResolveHelper getResolveHelper() {
        return javaPsiFacade.getResolveHelper();
    }

    @SuppressWarnings("UnstableApiUsage")
    public @NotNull PsiNameHelper getNameHelper() {
        return javaPsiFacade.getNameHelper();
    }

    @Override
    public @NotNull PsiConstantEvaluationHelper getConstantEvaluationHelper() {
        return javaPsiFacade.getConstantEvaluationHelper();
    }

    @Override
    public boolean isPartOfPackagePrefix(@NotNull String s) {
        return javaPsiFacade.isPartOfPackagePrefix(s);

    }

    @Override
    public boolean isInPackage(@NotNull PsiElement psiElement, @NotNull PsiPackage psiPackage) {
        return javaPsiFacade.isInPackage(psiElement, psiPackage);
    }

    @Override
    public boolean arePackagesTheSame(@NotNull PsiElement psiElement, @NotNull PsiElement psiElement1) {
        return javaPsiFacade.arePackagesTheSame(psiElement, psiElement1);
    }

    @Override
    public @NotNull Project getProject() {
        return javaPsiFacade.getProject();
    }

    @Override
    public boolean isConstantExpression(@NotNull PsiExpression psiExpression) {
        return javaPsiFacade.isConstantExpression(psiExpression);
    }
}
