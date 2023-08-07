package com.by122006.zircon.ijplugin;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import zircon.ExMethod;

public class ZrPluginUtil {
    public static boolean isPluginImport(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
            final Boolean aBoolean = ProgressManager.getInstance().runProcess(() -> {
                final String qualifiedName = ExMethod.class.getName();
                PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project));
                return psiClass != null;
            }, new ProgressIndicatorBase());
            return CachedValueProvider.Result
                    .create(aBoolean, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }
}
