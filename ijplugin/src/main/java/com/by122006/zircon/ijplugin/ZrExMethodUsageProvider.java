package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import zircon.ExMethod;

public class ZrExMethodUsageProvider implements ImplicitUsageProvider {
    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        if (!(element instanceof PsiMethod)) return false;
        if (DumbService.isDumb(element.getProject())) return false;
        final PsiAnnotation annotation = ((PsiMethod) element).getAnnotation(ExMethod.class.getName());
        return annotation != null;
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
    }
}
