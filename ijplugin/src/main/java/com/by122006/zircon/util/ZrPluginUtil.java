package com.by122006.zircon.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import zircon.ExMethod;

public class ZrPluginUtil {
    public static boolean hasZrPlugin(Project project) {
        if (project.isDefault() || !project.isInitialized()) {
            return false;
        }
        ApplicationManager.getApplication().assertReadAccessAllowed();
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
            PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(ExMethod.class.getPackageName());
            return new CachedValueProvider.Result<>(aPackage, ProjectRootManager.getInstance(project));
        }) != null;
    }
}
