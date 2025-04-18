package com.by122006.zircon.util;

import com.by122006.zircon.ijplugin.ZirconSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.ExMethod;
import zircon.example.ExObject;

import java.util.Objects;
import java.util.function.Predicate;

import static com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT;

public class ZrPluginUtil {
    public static synchronized boolean hasZrPlugin(PsiElement psiElement) {
        final Project project = psiElement.getProject();
        if (project == null)
            return false;
        if (project.isDefault() || !project.isInitialized()) {
            return false;
        }
        if (!ZirconSettings.getInstance().enableAll) {
            return false;
        }
        ApplicationManager.getApplication().assertReadAccessAllowed();
        // 获取当前模块
        @Nullable Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
        if (module == null) return false;
        // 获取模块的搜索范围
        GlobalSearchScope moduleScope = module.getModuleWithDependenciesAndLibrariesScope(true);
        return CachedValuesManager.getManager(project).getCachedValue(module, () -> {
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(ExMethod.class.getName(), moduleScope);
            return new CachedValueProvider.Result<>(psiClass, MODIFICATION_COUNT);
        }) != null;
    }

    public static boolean isAssignableSite(PsiMethod method, PsiType psiType2) {
        if (!method.isValid()) return false;
        final PsiParameter @NotNull [] parameterTypes = method.getParameterList().getParameters();
        if (parameterTypes.length == 0) return false;
        PsiType type = parameterTypes[0].getType();
        return isAssignableSite(method.getProject(), type, psiType2, method.getTypeParameters(), true);
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

    public static boolean isAssignableSite(Project project, PsiType psiType1, PsiType psiType2, PsiTypeParameter[] parameters, boolean allowExtend) {
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
            return isAssignableSite(project, deepComponentType, deepComponentType2, parameters, allowExtend);
        }
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
                    if (PsiTypesUtil.getPsiClass(psiType1) == PsiTypesUtil.getPsiClass(a)) {
                        found = a;
                        break;
                    }
                    for (PsiType b : a.getSuperTypes()) {
                        if (PsiTypesUtil.getPsiClass(psiType1) == PsiTypesUtil.getPsiClass(b)) {
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
                            if (param instanceof PsiWildcardType) {
                                if (!((PsiWildcardType) param).isBounded()) {
                                    param = PsiClassType.getTypeByName("java.lang.Object", project, GlobalSearchScope.allScope(project));
                                } else {
                                    param = ((PsiWildcardType) param).getBound();
                                }
                            }
                            //非由方法泛型引入的约束(参数中直接定义)，强制相等
                            final boolean b = Objects.equals(PsiTypesUtil.getPsiClass(param), PsiTypesUtil.getPsiClass(parameters2[i]));
                            if (b) {
                                continue;
                            }
                        }
                        //防止循环定义
                        if (PsiTypesUtil.compareTypes(param, psiType1, true)) {
                            continue;
                        }
                        if (!isAssignableSite(project, param, parameters2[i], parameters, false)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            };
            return predicate.test((PsiClassType) psiType2);
        }
        return false;
    }


}
