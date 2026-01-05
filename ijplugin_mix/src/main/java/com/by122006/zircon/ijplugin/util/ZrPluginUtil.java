package com.by122006.zircon.ijplugin.util;

import com.by122006.zircon.ijplugin.ZirconSettings;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.ExMethod;
import zircon.example.ExArray;
import zircon.example.ExObject;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT;

public class ZrPluginUtil {
    static int EnablePluginChecker = 3;

    public static synchronized boolean hasZrPlugin(PsiElement psiElement) {
        final Project project = psiElement.getProject();
        if (project.isDefault() || !project.isInitialized()) {
            return false;
        }
        if (!ZirconSettings.getInstance().enableAll) {
            return false;
        }
        long start = System.currentTimeMillis();
        try {
            ApplicationManager.getApplication().assertReadAccessAllowed();
            // 获取当前模块
            final PsiFile containingFile = psiElement.getContainingFile();
            if (containingFile instanceof PsiCodeFragment) return false;
            @Nullable Module module = ModuleUtilCore.findModuleForPsiElement(containingFile);
            if (module == null) return false;
            // 获取模块的搜索范围
            try {
                final boolean b = CachedValuesManager.getManager(project).getCachedValue(module, () -> {
                    GlobalSearchScope moduleScope = module.getModuleWithDependenciesAndLibrariesScope(true);
                    PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(ExMethod.class.getName(), moduleScope);
                    return new CachedValueProvider.Result<>(psiClass, MODIFICATION_COUNT);
                }) != null;
                if (!b) {
                    EnablePluginChecker--;
                    return EnablePluginChecker > 0;
                } else {
                    EnablePluginChecker = 3;
                    return b;
                }
            } catch (Exception e) {
                return true;
            }
        } finally {
            if (System.currentTimeMillis() - start > 100)
                System.out.println("ZirconPluginUtil.hasZrPlugin: " + (System.currentTimeMillis() - start) + " ms");

        }
    }

    public static boolean isAssignableSite(PsiMethod method, PsiType psiType2) {
        if (!method.isValid()) return false;
        final PsiParameter[] parameterTypes = method.getParameterList().getParameters();
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
            final PsiType deepComponentType = convertTypeByMethodTypeParameter(psiType1.getDeepComponentType(), parameters);
            final PsiType[] parameters1 = ((PsiClassType) deepComponentType).getParameters();
            Predicate<PsiType> test = _psiType2 -> {
                if (TypeConversionUtil.erasure(_psiType2).equals(TypeConversionUtil.erasure(psiType1))) {
                    final PsiType[] parameters2 = ((PsiClassType) _psiType2).getParameters();
                    if (parameters2.size() != parameters1.size())
                        return false;
                    for (int i = 0; i < parameters2.length; i++) {
                        if (parameters1[i].equals(psiType1) && parameters2[i].equals(psiType2)) {
                            //防止类似T extends A<T>的递归
                            continue;
                        }
                        final boolean assignable = isAssignableSite(project, parameters1[i], parameters2[i], parameters, allowExtend);
                        if (!assignable) return false;
                    }
                    return true;
                }
                return false;
            };
            Function<PsiType, PsiType> find = new Function<>() {
                @Override
                public PsiType apply(PsiType psiType) {
                    if (test.test(psiType)) return psiType;
                    final PsiType[] superTypes = psiType.getSuperTypes();
                    for (PsiType superType : superTypes) {
                        final PsiType apply = apply(superType);
                        if (apply != null) return apply;
                    }
                    return null;
                }
            };
            return find.apply(psiType2) != null;
        }
        return false;
    }

    public static int getLineNumberOfPsiMethod(PsiMethod psiMethod) {
        // 获取PsiMethod所在的PsiFile
        PsiFile psiFile = psiMethod.getContainingFile();
        if (psiFile == null) {
            return -1; // 如果PsiFile为空，返回-1表示未找到
        }

        // 获取PsiFile对应的Document
        Document document = PsiDocumentManager.getInstance(psiMethod.getProject()).getDocument(psiFile);
        if (document == null) {
            return -1; // 如果Document为空，返回-1表示未找到
        }

        // 获取PsiMethod的文本范围
        TextRange textRange = psiMethod.getTextRange();
        if (textRange == null) {
            return -1; // 如果文本范围为空，返回-1表示未找到
        }

        // 将文本范围的起始偏移量转换为行号
        int lineNumber = document.getLineNumber(textRange.getStartOffset());
        return lineNumber + 1; // 行号从0开始，所以需要加1
    }

    static BuildNumber build = ApplicationInfo.getInstance().getBuild();

    public static int getBuildVersion() {
        return build.getBaselineVersion();
    }
}
