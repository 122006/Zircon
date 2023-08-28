package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightParameterListBuilder;
import com.intellij.psi.impl.light.LightParameterListWrapper;
import com.intellij.psi.impl.light.LightParameterWrapper;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.ExMethod;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class ZrPsiAugmentProvider extends PsiAugmentProvider {
    private static final Logger LOG = Logger.getInstance(ZrPsiAugmentProvider.class.getName());


    public static class CacheMethodInfo {
        List<PsiType> targetType = new ArrayList<>();
        boolean isStatic = false;
        String name;
        PsiMethod method;
    }

    public static synchronized List<CacheMethodInfo> recoverCache(Project project) {
        return ProgressManager.getInstance().runProcess(() -> {
            final String qualifiedName = ExMethod.class.getName();
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project));
            if (psiClass == null) return Collections.emptyList();
            final List<CacheMethodInfo> collect = ReferencesSearch.search(psiClass).findAll().stream()
                    .map(element -> {
                        final PsiMethod method = PsiTreeUtil.getParentOfType(element.getElement(), PsiMethod.class);
                        if (method == null) return null;
                        return method;
                    })
                    .filter(Objects::nonNull)
                    .filter(PsiElement::isValid)
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
                                final PsiTypeElement childOfType = PsiTreeUtil.getChildOfType(a, PsiTypeElement.class);
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
            System.out.println("find ExtensionMethods size:" + collect.size());
            return collect;
        }, new ProgressIndicatorBase());
    }

    @NotNull
    protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement psiElement,
                                                             @NotNull Class<Psi> eleType) {
        final List<Psi> emptyResult = Collections.emptyList();
        if (!ZrPluginUtil.hasZrPlugin(psiElement.getProject())) return emptyResult;

        if (PsiMethod.class.isAssignableFrom(eleType)) {
            final List<CacheMethodInfo> psiMethods = getCachedAllMethod(psiElement.getProject());
            if (psiElement instanceof PsiClass) {
                PsiClass psiClass = (PsiClass) psiElement;
                final String type2Str = psiClass.getQualifiedName();
                if (type2Str == null) {
                    return Collections.emptyList();
                }
                if (Objects.equals(type2Str, "_Dummy_.__Array__")) {
                    final List<PsiMethod> collect = psiMethods.stream()
                            .map(methodInfo -> {
                                return methodInfo.targetType.stream()
                                        .filter(type1 -> type1 instanceof PsiArrayType)
                                        .map(type -> new ZrPsiExtensionMethod(methodInfo.isStatic, psiClass, methodInfo.method))
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList());
                            })
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList());

                    return (List<Psi>) collect;
                }

                PsiType type2 = PsiTypesUtil.getClassType(psiClass);
                List<PsiMethod> ownMethods = psiClass instanceof PsiExtensibleClass ? ((PsiExtensibleClass) psiClass).getOwnMethods() : List.of();
                final List<PsiMethod> collect = psiMethods.stream()
                        .map(methodInfo -> {
                            return methodInfo.targetType.stream()
                                    .filter(type -> Objects.equals(PsiTypesUtil.getPsiClass(type), psiClass))
                                    .map(type -> new ZrPsiExtensionMethod(methodInfo.isStatic, psiClass, methodInfo.method))
                                    .filter(method -> ownMethods.stream().noneMatch(om ->
                                            Objects.equals(om.getName(), method.getName())
                                                    && Arrays.stream(method.getParameterList().getParameters()).allMatch(a -> a.isVarArgs() == om.isVarArgs() && Arrays.stream(om.getParameterList().getParameters()).anyMatch(b -> a.getType().equals(b.getType())))
                                    ))
                                    .collect(Collectors.toList());
                        })
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());

                System.out.println("type2 :" + type2 + " QualifiedName:" + type2Str + "   found:" + collect.stream().map(PsiMethod::toString).collect(Collectors.joining(",")));
                return (List<Psi>) collect;
            }
        }
        return emptyResult;

    }

//    @Override
//    protected List<PsiExtensionMethod> getExtensionMethods(@NotNull PsiClass aClass, @NotNull String nameHint, @NotNull PsiElement context) {//PsiMethodCallExpression:("2131" + "12312").add("123")
//        return getPsiExtensionMethods(aClass, context);
//    }

//    @NotNull
//    private List<PsiExtensionMethod> getPsiExtensionMethods(@NotNull PsiClass aClass, @NotNull PsiElement context) {
//        long startTime = System.currentTimeMillis();
//        @NotNull Project project = aClass.getProject();
//        final List<CacheMethodInfo> psiMethods = getCachedAllMethod(project);
//        final String type2Str = aClass.getQualifiedName();
//        if (type2Str == null) {
//            return Collections.emptyList();
//        }
//        System.out.println("getPsiExtensionMethods aClass:" + aClass.getName() + " text:" + context.getText());
////        PsiType type2 = PsiClassType.getTypeByName(type2Str, aClass.getProject(), GlobalSearchScope.allScope(aClass.getProject()));
//        final List<PsiExtensionMethod> collect = psiMethods.stream()
//                .filter(methodInfo -> methodInfo.name != null
//                        && methodInfo.targetType.stream().anyMatch(type -> Objects.equals(PsiTypesUtil.getPsiClass(type), aClass)))
//                .map(methodInfo -> new ZrPsiExtensionMethod(methodInfo.isStatic, aClass, methodInfo.method))
//                .collect(Collectors.toList());
//        System.out.println("use " + (System.currentTimeMillis() - startTime) + "ms");
//        return collect;
//    }

    private List<CacheMethodInfo> getCachedAllMethod(@NotNull Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project
                , () -> CachedValueProvider.Result.create(recoverCache(project), PsiModificationTracker.MODIFICATION_COUNT));
    }


    public static class ZrPsiExtensionMethod extends LightMethodBuilder implements PsiExtensionMethod {
        private final PsiClass targetClass;
        public PsiMethod targetMethod = null;
        public boolean isStatic = false;
        private ASTNode myASTNode;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            ZrPsiExtensionMethod that = (ZrPsiExtensionMethod) o;

            if (isStatic != that.isStatic) return false;
            if (targetClass != null ? !targetClass.equals(that.targetClass) : that.targetClass != null) return false;
            return targetMethod.equals(that.targetMethod);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (targetClass != null ? targetClass.hashCode() : 0);
            result = 31 * result + targetMethod.hashCode();
            result = 31 * result + (isStatic ? 1 : 0);
            return result;
        }

        public ZrPsiExtensionMethod(boolean isStatic, PsiClass targetClass, PsiMethod targetMethod) {
            super(targetClass.getManager(), JavaLanguage.INSTANCE, targetMethod.getName());
            this.isStatic = isStatic;
            this.targetClass = targetClass;
            this.targetMethod = targetMethod;
        }

        @Override
        public PsiDocComment getDocComment() {
            return targetMethod.getDocComment();
        }

        @Override
        public PsiTypeParameter @NotNull [] getTypeParameters() {
            return targetMethod.getTypeParameters();
        }

        @Override
        public @NotNull PsiParameterList getParameterList() {

            if (isStatic) return targetMethod.getParameterList();
            else {
                final PsiParameterList parameterList = targetMethod.getParameterList();
                final LightParameterListBuilder lightParameterListBuilder = new LightParameterListBuilder(getManager(), getLanguage());
                for (int i = 1; i < parameterList.getParameters().length; i++) {
                    lightParameterListBuilder.addParameter(parameterList.getParameter(i));
                }
                return lightParameterListBuilder;
            }
        }

        @Override
        public boolean isEquivalentTo(final PsiElement another) {
            return targetMethod.isEquivalentTo(another);
        }


        @Override
        public PsiTypeParameterList getTypeParameterList() {
            return targetMethod.getTypeParameterList();
        }

        @Override
        public PsiCodeBlock getBody() {
            return targetMethod.getBody();
        }

        @Override
        public @NotNull PsiElement getNavigationElement() {
            return targetMethod;
        }

        @Override
        public PsiAnnotation @NotNull [] getAnnotations() {
            return Arrays.stream(super.getAnnotations()).filter(a -> !Objects.equals(a.getQualifiedName(), ExMethod.class.getName()))
                    .toArray(PsiAnnotation[]::new);
        }

        @Override
        public @Nullable PsiAnnotation getAnnotation(@NotNull @NonNls String fqn) {
            if (fqn.equals(ExMethod.class.getName())) return null;
            return super.getAnnotation(fqn);
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
            return targetClass;
        }

        public PsiClass getTargetClass() {
            return targetClass;
        }

        public PsiMethod getTargetMethod() {
            return targetMethod;
        }


        @Override
        public @Nullable PsiParameter getTargetReceiverParameter() {
            return isStatic ? null : targetMethod.getParameterList().getParameter(0);
        }

        @Override
        public @Nullable PsiParameter getTargetParameter(int index) {
            return targetMethod.getParameterList().getParameter(isStatic ? index : (index + 1));
        }
    }
}
