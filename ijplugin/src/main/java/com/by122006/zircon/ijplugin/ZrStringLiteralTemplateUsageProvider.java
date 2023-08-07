package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

public class ZrStringLiteralTemplateUsageProvider implements ImplicitUsageProvider {
    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
        return checkReferencedFromZrStringLiteral(element);
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
        return checkReferencedFromZrStringLiteral(element);
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
    }

    private boolean checkReferencedFromZrStringLiteral(PsiElement elem) {
        if (elem.getLanguage() != JavaLanguage.INSTANCE) return false;
        PsiNamedElement namedElement = this.findNamedElement(elem);
        if (namedElement != null) {
            PsiFile containingFile = elem.getContainingFile();
            if (containingFile instanceof PsiJavaFile) {
                return CachedValuesManager.getCachedValue(elem, () -> {
                    final Boolean aBoolean = ProgressManager.getInstance().runProcess(() -> {
                        return !ReferencesSearch.search(namedElement, new LocalSearchScope(containingFile))
                                .forEach((e) -> !this.isInStringLiteral(e.getElement()));
                    }, new ProgressIndicatorBase());
                    return CachedValueProvider.Result
                            .create(aBoolean, PsiModificationTracker.MODIFICATION_COUNT);
                });

            }
        }

        return false;
    }

    private boolean isInStringLiteral(PsiElement element) {
        if (element == null) {
            return false;
        } else {
            return element instanceof PsiField && "_zr_obj_str".equals(((PsiField) element).getName()) || this.isInStringLiteral(element.getParent());
        }
    }

    private PsiNamedElement findNamedElement(PsiElement elem) {
        if (elem == null) {
            return null;
        } else {
            for (int offset = elem.getTextOffset(); elem != null && elem.getTextOffset() == offset && !(elem instanceof PsiNamedElement); elem = elem.getParent()) {
            }
            return (PsiNamedElement) elem;
        }
    }
}
