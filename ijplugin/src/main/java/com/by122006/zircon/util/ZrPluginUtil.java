package com.by122006.zircon.util;

import com.by122006.zircon.ijplugin.ZirconSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.TypeConversionUtil;

import java.util.Arrays;
import java.util.function.Predicate;

import zircon.ExMethod;

public class ZrPluginUtil {
    public static synchronized boolean hasZrPlugin(Project project) {
        if (project.isDefault() || !project.isInitialized()) {
            return false;
        }
        if (!ZirconSettings.getInstance().enableAll) {
            return false;
        }
        ApplicationManager.getApplication().assertReadAccessAllowed();
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
            PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(ExMethod.class.getPackageName());
            return new CachedValueProvider.Result<>(aPackage, ProjectRootManager.getInstance(project));
        }) != null;
    }

    public static boolean isAssignableSite(PsiMethod method, PsiType psiType2) {
        if (!method.isValid()) return false;
        final PsiClassType javaLangObject = PsiType.getJavaLangObject(method.getManager(), method.getResolveScope());
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        for (PsiTypeParameter typeParameter : method.getTypeParameters()) {
            substitutor = substitutor.put(typeParameter, javaLangObject);
        }
        final PsiType[] parameterTypes = method.getSignature(substitutor).getParameterTypes();
        if (parameterTypes.length == 0) return false;
        PsiType type = parameterTypes[0];
        return isAssignableSite(type, psiType2);
    }

    public static boolean isAssignableSite(PsiType psiType1, PsiType psiType2) {
        if (!psiType1.isValid()) return false;
        if (!psiType2.isValid()) return false;
        if (TypeConversionUtil.isPrimitiveAndNotNull(psiType1) != TypeConversionUtil.isPrimitiveAndNotNull(psiType2))
            return false;
        final PsiType erasure1 = TypeConversionUtil.erasure(psiType1);
        if (erasure1.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return true;
        if (psiType1 instanceof PsiArrayType && psiType2 instanceof PsiArrayType) {
            final PsiType deepComponentType = psiType1.getDeepComponentType();
            final PsiType deepComponentType2 = psiType2.getDeepComponentType();
            if (TypeConversionUtil.isPrimitiveAndNotNull(deepComponentType) && TypeConversionUtil.isPrimitiveAndNotNull(deepComponentType2)) {
                return deepComponentType.equals(deepComponentType2);
            }
            return isAssignableSite(deepComponentType, deepComponentType2);
        }
//        if (psiType1 instanceof PsiClassType && psiType2 instanceof PsiClassType) {
//            return TypeConversionUtil.areTypesConvertible(psiType1, psiType2);
//        }
        if (psiType1.equals(psiType2) || TypeConversionUtil.isAssignable(psiType1, psiType2)) return true;
        if (psiType1 instanceof PsiClassType && psiType2 instanceof PsiClassType) {
            if (!TypeConversionUtil.isAssignable(erasure1, TypeConversionUtil.erasure(psiType2)))
                return false;
            final PsiType[] parameters1 = ((PsiClassType) psiType1).getParameters();
            if (parameters1.length == 0) {
                return true;
            }
            Predicate<PsiClassType> predicate = nType2 -> {
                final PsiType[] parameters2 = nType2.getParameters();
                if (parameters1.length == parameters2.length) {
                    for (int i = 0; i < parameters1.length; i++) {
                        if (parameters1[i].equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) continue;
                        if (!isAssignableSite(parameters1[i], parameters2[i])) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            };
            if (predicate.test((PsiClassType) psiType2)) {
                return true;
            }
            return Arrays.stream(psiType2.getSuperTypes())
                         .filter(PsiType::isValid)
                         .filter(nType2 -> nType2 instanceof PsiClassType && TypeConversionUtil.isAssignable(erasure1, nType2))
                         .map(PsiClassType.class::cast)
                         .anyMatch(predicate);


        }
        return false;
    }


}
