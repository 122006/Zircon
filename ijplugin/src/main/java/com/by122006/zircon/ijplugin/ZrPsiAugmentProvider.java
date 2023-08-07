package com.by122006.zircon.ijplugin;

import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.ExMethod;

import java.util.*;
import java.util.stream.Collectors;

public class ZrPsiAugmentProvider extends PsiAugmentProvider {
    private static final Logger LOG = Logger.getInstance(ZrPsiAugmentProvider.class.getName());

    //    @Override
//    @NotNull
//    protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement psiElement, @NotNull Class<Psi> type) {
//        final List<Psi> emptyResult = Collections.emptyList();
//
//        if ((psiElement instanceof PsiClass) == false) {
//            return emptyResult;
//        }
//        PsiClass psiClass = (PsiClass) psiElement;
//
//
//    }

    public static class CacheMethodInfo {
        List<PsiType> targetType = new ArrayList<>();
        boolean isStatic = false;
        String name;
        PsiMethod method;
    }


    public static List<CacheMethodInfo> recoverCache(Project project) {
        return ProgressManager.getInstance().runProcess(() -> {
            final String qualifiedName = ExMethod.class.getName();
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project));
            if (psiClass == null) return Collections.emptyList();
            final List<CacheMethodInfo> collect = ReferencesSearch.search(psiClass).findAll().stream().map(element -> {
                        final PsiMethod method = PsiTreeUtil.getParentOfType(element.getElement(), PsiMethod.class);
                        if (method == null) return null;
                        return method;
                    })
                    .filter(Objects::nonNull)
                    .map(method -> {
                        final PsiAnnotation annotation = method.getAnnotation(qualifiedName);
                        if (annotation == null) return null;
                        final PsiAnnotationMemberValue ex = annotation.findDeclaredAttributeValue("ex");
                        CacheMethodInfo cacheMethodInfo = new CacheMethodInfo();
                        cacheMethodInfo.name = method.getName();
                        cacheMethodInfo.method = method;
                        cacheMethodInfo.isStatic = ex != null;
                        if (ex != null) {
                            final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValueImpl) ex).getInitializers();
                            cacheMethodInfo.targetType = Arrays.stream(initializers).map(a -> {
                                final PsiTypeElement childOfType = PsiTreeUtil.getChildOfType(initializers[0], PsiTypeElement.class);
                                if (childOfType == null) return null;
                                return childOfType.getType();
                            }).filter(Objects::nonNull).collect(Collectors.toList());
                        } else {
                            final PsiParameterList parameterList = method.getParameterList();
                            if (parameterList.isEmpty()) return null;
                            if (parameterList.getParameter(0) == null) return null;
                            cacheMethodInfo.targetType = List.of(parameterList.getParameter(0).getType());
                        }
                        return cacheMethodInfo;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            LOG.info("find ExtensionMethods size:" + collect.size());
            return collect;
        }, new ProgressIndicatorBase());
    }

    @NotNull
    protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement psiElement,
                                                             @NotNull Class<Psi> eleType) {
        final List<Psi> emptyResult = Collections.emptyList();
        if (PsiMethod.class.isAssignableFrom(eleType)) {
            final List<CacheMethodInfo> psiMethods = getCachedAllMethod(psiElement, psiElement.getProject());
            if (psiElement instanceof PsiClass) {
                PsiClass psiClass = (PsiClass) psiElement;
                final String type2Str = psiClass.getQualifiedName();
                if (type2Str == null) {
                    return Collections.emptyList();
                }

                PsiType type2 = PsiClassType.getTypeByName(type2Str, psiClass.getProject(), GlobalSearchScope.allScope(psiClass.getProject()));
                final List<PsiMethod> collect = psiMethods.stream()
//                        .filter(methodInfo -> nameHint == null || (methodInfo.name.startsWith(nameHint) && !Objects.equals(methodInfo.name, nameHint)))
                        .filter(methodInfo -> methodInfo.targetType.stream().anyMatch(type -> methodInfo.isStatic ? type.equals(type2) : type.isConvertibleFrom(type2)))
                        .map(methodInfo -> {
//                            LightMethodBuilder method = new LightMethodBuilder(psiElement.getManager(), JavaLanguage.INSTANCE, methodInfo.name);
//                            final PsiModifierList modifierList = methodInfo.method.getModifierList();
//                            final PsiModifierList copy = (PsiModifierList) modifierList.copy();
//                            copy.setModifierProperty(PsiModifier.STATIC, methodInfo.isStatic);
//                            method.addModifier(modifierList.getText());
//                            method.setContainingClass(methodInfo.method.getContainingClass());
//                            method.setNavigationElement(methodInfo.method.getContainingClass());
//                            final PsiParameter[] parameters = methodInfo.method.getParameterList().getParameters();
//                            for (int i = methodInfo.isStatic ? 0 : 1; i < parameters.length; i++) {
//                                final PsiParameter parameter = parameters[i];
//                                method.addParameter(parameter.getName(), parameter.getType());
//                            }
//                            method.setMethodReturnType(methodInfo.method.getReturnType());
//                            return method;
                            return new ZrPsiExtensionMethod(methodInfo.isStatic, methodInfo.method);
                        })
                        .collect(Collectors.toList());
                return (List<Psi>) collect;
            }
        }
        return emptyResult;

    }

//    @Override
//    protected List<PsiExtensionMethod> getExtensionMethods(@NotNull PsiClass aClass, @NotNull String nameHint, @NotNull PsiElement context) {//PsiMethodCallExpression:("2131" + "12312").add("123")
//        return getPsiExtensionMethods(aClass, context);
//    }
//
//    @NotNull
//    private List<PsiExtensionMethod> getPsiExtensionMethods(@NotNull PsiClass aClass, @NotNull PsiElement context) {
//        long startTime = System.currentTimeMillis();
//        @NotNull Project project = aClass.getProject();
//        final List<CacheMethodInfo> psiMethods = getCachedAllMethod(aClass, project);
//        final String type2Str = aClass.getQualifiedName();
//        if (type2Str == null) {
//            return Collections.emptyList();
//        }
//        System.out.println("getPsiExtensionMethods aClass:" + aClass.getName() + " text:" + context.getText());
//        PsiType type2 = PsiClassType.getTypeByName(type2Str, aClass.getProject(), GlobalSearchScope.allScope(aClass.getProject()));
//        final String name;
//        if (context instanceof PsiMethodCallExpression) {
//            name = ((PsiMethodCallExpression) context).getMethodExpression().getLastChild().getText();
//        } else {
//            name = context.getText();
//        }
//        final List<PsiExtensionMethod> collect = psiMethods.stream()
//                .filter(methodInfo -> name != null && methodInfo.name != null && methodInfo.name.startsWith(name)
//                        && methodInfo.targetType.stream().anyMatch(type -> methodInfo.isStatic ? type.equals(type2) : type.isConvertibleFrom(type2)))
//                .map(methodInfo -> new ZrPsiExtensionMethod(methodInfo.isStatic, methodInfo.method))
//                .collect(Collectors.toList());
//        System.out.println("use " + (System.currentTimeMillis() - startTime) + "ms");
//
//        return collect;
//    }

    private List<CacheMethodInfo> getCachedAllMethod(@NotNull PsiElement aClass, @NotNull Project project) {
        return CachedValuesManager.getCachedValue(aClass, () -> CachedValueProvider.Result
                .create(recoverCache(project), PsiModificationTracker.MODIFICATION_COUNT));
    }


    class ZrPsiExtensionMethod extends PsiMethodImpl implements PsiExtensionMethod {
        public PsiMethod targetMethod = null;
        public boolean isStatic = false;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ZrPsiExtensionMethod that = (ZrPsiExtensionMethod) o;
            if (isStatic != that.isStatic) return false;
            return targetMethod != null ? targetMethod.equals(that.targetMethod) : that.targetMethod == null;
        }

        @Override
        public int hashCode() {
            int result = targetMethod.hashCode();
            result = 31 * result + (isStatic ? 1 : 0);
            return result;
        }

        public ZrPsiExtensionMethod(boolean isStatic, PsiMethod targetMethod) {
            super(targetMethod.getNode());
            this.isStatic = isStatic;
            this.targetMethod = targetMethod;
        }

        @Override
        public PsiTypeParameter @NotNull [] getTypeParameters() {
            return isStatic ?
                    targetMethod.getTypeParameters()
                    : Arrays.stream(targetMethod.getTypeParameters()).skip(1)
                    .toArray(PsiTypeParameter[]::new);
        }

        @Override
        public JvmParameter @NotNull [] getParameters() {
            return isStatic ?
                    targetMethod.getParameters()
                    : Arrays.stream(targetMethod.getParameters()).skip(1)
                    .toArray(JvmParameter[]::new);
        }

        @Override
        public @NotNull PsiParameterList getParameterList() {

            if (isStatic) return targetMethod.getParameterList();
            else {
                final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
                String[] names = new String[parameters.length - 1];
                PsiType[] types = new PsiType[parameters.length - 1];
                for (int i = 1; i < parameters.length; i++) {
                    names[i - 1] = parameters[i].getName();
                    types[i - 1] = parameters[i].getType();
                }
                return PsiElementFactory.getInstance(targetMethod.getProject()).createParameterList(names, types);
            }
        }

        @Override
        public PsiTypeParameterList getTypeParameterList() {
            final PsiTypeParameterList typeParameterList = targetMethod.getTypeParameterList();
            if (typeParameterList == null) return null;
            if (isStatic) return typeParameterList;
            else {
                final PsiTypeParameter[] parameters = typeParameterList.getTypeParameters();
                final PsiTypeParameterList returnList = PsiElementFactory.getInstance(targetMethod.getProject()).createTypeParameterList();
                for (int i = 1; i < parameters.length; i++) {
                    returnList.add(parameters[i]);
                }
                return returnList;
            }
        }

        @Override
        public PsiType getReturnType() {
            return targetMethod.getReturnType();
        }

        @Override
        public @NotNull PsiModifierList getModifierList() {
            final PsiModifierList modifierList = targetMethod.getModifierList();
            final PsiModifierList copy = (PsiModifierList) modifierList.copy();
            copy.setModifierProperty(PsiModifier.STATIC, isStatic);
            return copy;
        }

        @Override
        public @NotNull PsiReferenceList getThrowsList() {
            return targetMethod.getThrowsList();
        }

        @Override
        public PsiClass getContainingClass() {
            return targetMethod.getContainingClass();
        }

        @Override
        public @NotNull PsiMethod getTargetMethod() {
            return targetMethod;
        }

        @Override
        public @Nullable PsiParameter getTargetReceiverParameter() {
            return targetMethod.getParameterList().getParameter(0);
        }

        @Override
        public @Nullable PsiParameter getTargetParameter(int index) {
            return targetMethod.getParameterList().getParameter(index + 1);
        }
    }
}
