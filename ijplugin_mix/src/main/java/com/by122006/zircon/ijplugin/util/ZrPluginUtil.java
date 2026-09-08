package com.by122006.zircon.ijplugin.util;

import com.by122006.zircon.ijplugin.ZirconSettings;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ZrPluginUtil {
    public static boolean hasZrPlugin(PsiElement psiElement) {
        final Project project = psiElement.getProject();
        if (project.isDefault() || !project.isInitialized()) {
            return false;
        }
        if (!ZirconSettings.getInstance().enableAll) {
            return false;
        }
        // Parser/lexer extensions remain active independently of this check.
        // Semantic features must not enter Java indexes while they are rebuilt.
        if (DumbService.isDumb(project)) {
            return false;
        }
        try {
            ApplicationManager.getApplication().assertReadAccessAllowed();
            final PsiFile containingFile = psiElement.getContainingFile();
            if (containingFile == null) return false;
            if (containingFile instanceof PsiCodeFragment) return false;
            @Nullable Module module = ModuleUtilCore.findModuleForPsiElement(containingFile);
            if (module == null) return false;
            return CachedValuesManager.getManager(project).getCachedValue(module, () -> {
                GlobalSearchScope moduleScope = module.getModuleWithDependenciesAndLibrariesScope(true);
                PsiClass psiClass = JavaPsiFacade.getInstance(project)
                        .findClass(ExMethod.class.getName(), moduleScope);
                return new CachedValueProvider.Result<>(
                        psiClass != null,
                        ProjectRootModificationTracker.getInstance(project));
            });
        } catch (IndexNotReadyException ignored) {
            return false;
        } catch (RuntimeException ignored) {
            // PSI can be invalidated between the read checks above during project reload.
            return false;
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
        return isAssignableSite(
                project,
                psiType1,
                psiType2,
                parameters,
                allowExtend,
                new HashSet<>());
    }

    private static boolean isAssignableSite(Project project,
                                            PsiType expected,
                                            PsiType actual,
                                            PsiTypeParameter[] methodParameters,
                                            boolean allowExtend,
                                            Set<String> activeComparisons) {
        if (expected == null || actual == null || !expected.isValid() || !actual.isValid()) {
            return false;
        }

        expected = convertTypeByMethodTypeParameter(expected, methodParameters);
        String comparisonKey = safeTypeKey(expected) + '\u0000' + safeTypeKey(actual);
        // Recursive generic bounds such as T extends Comparable<T> are coinductive:
        // reaching the same pair again means this branch has not found a mismatch.
        if (!activeComparisons.add(comparisonKey)) {
            return true;
        }

        try {
            if (TypeConversionUtil.isPrimitiveAndNotNull(expected)
                    != TypeConversionUtil.isPrimitiveAndNotNull(actual)) {
                return false;
            }

            PsiType expectedErasure = TypeConversionUtil.erasure(expected);
            if (expectedErasure.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
                return true;
            }

            if (Objects.equals(expected, actual)
                    || (allowExtend && TypeConversionUtil.isAssignable(expected, actual))) {
                return true;
            }

            if (expected instanceof PsiArrayType || actual instanceof PsiArrayType) {
                if (!(expected instanceof PsiArrayType) || !(actual instanceof PsiArrayType)) {
                    return false;
                }
                PsiArrayType expectedArray = (PsiArrayType) expected;
                PsiArrayType actualArray = (PsiArrayType) actual;
                return isAssignableSite(
                        project,
                        expectedArray.getComponentType(),
                        actualArray.getComponentType(),
                        methodParameters,
                        allowExtend,
                        activeComparisons);
            }

            if (expected instanceof PsiWildcardType) {
                PsiWildcardType wildcard = (PsiWildcardType) expected;
                PsiType bound = wildcard.getBound();
                if (bound == null) return true;
                return wildcard.isExtends()
                        ? isAssignableSite(project, bound, actual, methodParameters, true, activeComparisons)
                        : TypeConversionUtil.isAssignable(actual, bound);
            }

            if (expected instanceof PsiClassType && actual instanceof PsiClassType) {
                return matchesClassHierarchy(
                        project,
                        (PsiClassType) expected,
                        (PsiClassType) actual,
                        methodParameters,
                        allowExtend,
                        activeComparisons);
            }
            return false;
        } finally {
            activeComparisons.remove(comparisonKey);
        }
    }

    private static boolean matchesClassHierarchy(Project project,
                                                 PsiClassType expected,
                                                 PsiClassType actual,
                                                 PsiTypeParameter[] methodParameters,
                                                 boolean allowExtend,
                                                 Set<String> activeComparisons) {
        PsiClassType.ClassResolveResult expectedResult = expected.resolveGenerics();
        PsiClass expectedClass = expectedResult.getElement();
        if (expectedClass == null) return false;

        Deque<PsiClassType> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(actual);

        while (!pending.isEmpty()) {
            PsiClassType candidate = pending.removeFirst();
            if (!visited.add(safeTypeKey(candidate))) {
                continue;
            }

            PsiClassType.ClassResolveResult candidateResult = candidate.resolveGenerics();
            PsiClass candidateClass = candidateResult.getElement();
            if (candidateClass == null) {
                continue;
            }

            if (candidateClass.getManager().areElementsEquivalent(expectedClass, candidateClass)) {
                PsiTypeParameter[] classParameters = expectedClass.getTypeParameters();
                for (PsiTypeParameter classParameter : classParameters) {
                    PsiType expectedArgument = expectedResult.getSubstitutor().substitute(classParameter);
                    if (expectedArgument == null) {
                        // A raw expected receiver intentionally accepts every specialization.
                        continue;
                    }
                    PsiType actualArgument = candidateResult.getSubstitutor().substitute(classParameter);
                    if (actualArgument == null || !isAssignableSite(
                            project,
                            expectedArgument,
                            actualArgument,
                            methodParameters,
                            allowExtend,
                            activeComparisons)) {
                        return false;
                    }
                }
                return true;
            }

            PsiSubstitutor substitutor = candidateResult.getSubstitutor();
            for (PsiClassType superType : candidateClass.getSuperTypes()) {
                PsiType substituted = substitutor.substitute(superType);
                if (substituted instanceof PsiClassType) {
                    pending.addLast((PsiClassType) substituted);
                }
            }
        }
        return false;
    }

    private static String safeTypeKey(PsiType type) {
        try {
            return type.getCanonicalText();
        } catch (RuntimeException ignored) {
            return type.getClass().getName() + '@' + System.identityHashCode(type);
        }
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
