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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
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
        final PsiParameter @NotNull [] parameterTypes = method.getParameterList().getParameters();
        if (parameterTypes.length == 0) return false;
        PsiType type = parameterTypes[0].getType();
        return isAssignableSite(type, psiType2, method.getTypeParameters(), true);
    }

    public static PsiType convertTypeByMethodTypeParameter(PsiType psiType1, PsiTypeParameter[] parameters) {
        for (PsiTypeParameter parameter : parameters) {
            if (Objects.equals(psiType1.getCanonicalText(), parameter.getName())) {
                final PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
                if (extendsListTypes.length == 0) {
                    return PsiType.getJavaLangObject(parameter.getManager(), parameter.getResolveScope());
                } else
                    return PsiSubstitutor.EMPTY.substitute(extendsListTypes[0]);
            }
        }
        return psiType1;
    }

    public static boolean isAssignableSite(PsiType psiType1, PsiType psiType2, PsiTypeParameter[] parameters, boolean allowExtend) {
        if (!psiType1.isValid()) return false;
        if (!psiType2.isValid()) return false;
        if (TypeConversionUtil.isPrimitiveAndNotNull(psiType1) != TypeConversionUtil.isPrimitiveAndNotNull(psiType2))
            return false;
        final PsiType erasure1 = TypeConversionUtil.erasure(psiType1);
        if (erasure1.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return true;
        if (psiType1 instanceof PsiArrayType && psiType2 instanceof PsiArrayType) {
            final PsiType deepComponentType = convertTypeByMethodTypeParameter(psiType1.getDeepComponentType(), parameters);

            final PsiType deepComponentType2 = psiType2.getDeepComponentType();
            if (TypeConversionUtil.isPrimitiveAndNotNull(deepComponentType) && TypeConversionUtil.isPrimitiveAndNotNull(deepComponentType2)) {
                return deepComponentType.equals(deepComponentType2);
            }
            return isAssignableSite(deepComponentType, deepComponentType2, parameters, allowExtend);
        }
//        if (psiType1 instanceof PsiClassType && psiType2 instanceof PsiClassType) {
//            return TypeConversionUtil.areTypesConvertible(psiType1, psiType2);
//        }
        if (psiType1.equals(psiType2) || (allowExtend && TypeConversionUtil.isAssignable(psiType1, psiType2)))
            return true;
        if (psiType1 instanceof PsiClassType && psiType2 instanceof PsiClassType) {
            if (!TypeConversionUtil.isAssignable(erasure1, TypeConversionUtil.erasure(psiType2)))
                return false;
            final PsiType[] parameters1 = ((PsiClassType) psiType1).getParameters();
            if (parameters1.length == 0) {
                return true;
            }
            Predicate<PsiClassType> predicate = nType2 -> {
                PsiType found = psiType2;
                br:
                for (PsiType a : nType2.getSuperTypes()) {
                    if (PsiTypesUtil.getPsiClass(psiType1)==PsiTypesUtil.getPsiClass(a)) {
                        found = a;
                        break;
                    }
                    for (PsiType b : a.getSuperTypes()) {
                        if (PsiTypesUtil.getPsiClass(psiType1)==PsiTypesUtil.getPsiClass(b)) {
                            found = b;
                            break br;
                        }
                    }
                }
                nType2 = (PsiClassType) found;
                final PsiType[] parameters2 = nType2.getParameters();
                if (parameters1.length == parameters2.length) {
                    for (int i = 0; i < parameters1.length; i++) {
                        PsiType param = convertTypeByMethodTypeParameter(parameters1[i], parameters);
                        if (param == parameters1[i]) {
                            //非由方法泛型引入的约束(参数中直接定义)，强制相等
                            final boolean b = PsiTypesUtil.getPsiClass(param)==PsiTypesUtil.getPsiClass(parameters2[i]);
                            if (!b) {
                                return false;
                            } else continue;
                        }
                        //防止循环定义
                        if (PsiTypesUtil.compareTypes(param, psiType1, true)) {
                            continue;
                        }
                        if (!isAssignableSite(param, parameters2[i], parameters, false)) {
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
//            return Arrays.stream(psiType2.getSuperTypes())
//                         .filter(PsiType::isValid)
//                         .filter(nType2 -> !PsiTypesUtil.compareTypes(nType2, erasure1,true))
//                         .filter(nType2 -> nType2 instanceof PsiClassType && TypeConversionUtil.isAssignable(erasure1, nType2))
//                         .map(PsiClassType.class::cast)
//                         .anyMatch(predicate);


        }
        return false;
    }


}
