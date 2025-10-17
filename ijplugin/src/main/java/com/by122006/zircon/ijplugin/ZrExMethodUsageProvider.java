package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import zircon.ExMethod;

public class ZrExMethodUsageProvider implements ImplicitUsageProvider {
    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        if (!(element instanceof PsiMethod)) return false;
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
