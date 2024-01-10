package com.by122006.zircon.util;

import com.by122006.zircon.ijplugin.ZirconSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

import zircon.ExMethod;

public class ZrPluginUtil {
    public static synchronized boolean hasZrPlugin(Project project) {
        if (project.isDefault() || !project.isInitialized()) {
            return false;
        }
        if (!ZirconSettings.getInstance().enableAll){
            return false;
        }
        ApplicationManager.getApplication().assertReadAccessAllowed();
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
            PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(ExMethod.class.getPackageName());
            return new CachedValueProvider.Result<>(aPackage, ProjectRootManager.getInstance(project));
        }) != null;
    }
}
