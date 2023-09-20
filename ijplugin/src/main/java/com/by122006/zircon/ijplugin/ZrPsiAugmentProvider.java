package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.lang.Language;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameterListBuilder;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.jetbrains.sa.jdi.ClassTypeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.ExMethod;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ZrPsiAugmentProvider extends PsiAugmentProvider {
    private static final Logger LOG = Logger.getInstance(ZrPsiAugmentProvider.class.getName());


    public static class CacheMethodInfo {
        List<PsiType> targetType = new ArrayList<>();
        boolean isStatic = false;
        boolean cover = false;
        String name;
        PsiMethod method;
    }


    public static synchronized List<CacheMethodInfo> recoverCache(Project project) {
        return ProgressManager.getInstance().runProcess(() -> {
            final String qualifiedName = ExMethod.class.getName();
            PsiClass @NotNull [] psiClasses = JavaPsiFacade.getInstance(project).findClasses(qualifiedName, GlobalSearchScope.allScope(project));
            final List<CacheMethodInfo> collect = Arrays.stream(psiClasses).map(psiClass -> ReferencesSearch.search(psiClass, GlobalSearchScope.allScope(project)).findAll()).flatMap(Collection::stream).filter(element -> element.getElement().getParent() instanceof PsiAnnotation).map(element -> {
                final PsiMethod method = PsiTreeUtil.getParentOfType(element.getElement(), PsiMethod.class);
                if (method == null) return null;
                return method;
            }).filter(Objects::nonNull).distinct().filter(PsiElement::isValid).map(method -> {
                final PsiAnnotation annotation = method.getAnnotation(qualifiedName);
                if (annotation == null) return null;
                CacheMethodInfo cacheMethodInfo = new CacheMethodInfo();
                cacheMethodInfo.name = method.getName();
                cacheMethodInfo.method = method;
                PsiAnnotationMemberValue ex = annotation.findDeclaredAttributeValue("ex");
                if (ex != null) {
                    if (ex instanceof PsiClassObjectAccessExpression) {
                        cacheMethodInfo.targetType.add(((PsiImmediateClassType) ((PsiClassObjectAccessExpression) ex).getType()).getParameters()[0]);
                    } else if (ex instanceof PsiArrayInitializerMemberValue) {
                        final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) ex).getInitializers();
                        final List<PsiType> psiTypes = Arrays.stream(initializers).map(a -> {
                            final PsiTypeElement childOfType = PsiTreeUtil.getChildOfType(a, PsiTypeElement.class);
                            if (childOfType == null) return null;
                            final PsiType type = childOfType.getType();
                            return type;
                        }).filter(Objects::nonNull).collect(Collectors.toList());
                        cacheMethodInfo.targetType = psiTypes;
                    } else {
                        System.out.println(ex.getText() + "[" + ex.getClass().getName());
                    }
                    if (cacheMethodInfo.targetType.isEmpty()) ex = null;

                }
                cacheMethodInfo.isStatic = ex != null;
                PsiAnnotationMemberValue cover = annotation.findDeclaredAttributeValue("cover");
                if (cover != null) {
                    if (cover instanceof PsiLiteralExpression) {
                        final Boolean value = (Boolean) ((PsiLiteralExpression) cover).getValue();
                        cacheMethodInfo.cover = value != null && value;
                    }
                }
                if (ex == null) {
                    final PsiParameterList parameterList = method.getParameterList();
                    if (parameterList.isEmpty()) return null;
                    final PsiParameter parameter = parameterList.getParameter(0);
                    if (parameter == null) return null;
                    final List<PsiTypeParameter> typeParameters = Arrays.stream(method.getTypeParameters()).filter(a -> Objects.equals(a.getName(), parameter.getType().getCanonicalText())).collect(Collectors.toList());
                    if (typeParameters.isEmpty()) {
                        cacheMethodInfo.targetType = List.of(parameter.getType());
                    } else {
                        final PsiClassType[] referencedTypes = typeParameters.get(0).getExtendsList().getReferencedTypes();
                        final PsiClassType typeByName = PsiClassType.getJavaLangObject(annotation.getManager(), GlobalSearchScope.projectScope(project));
                        cacheMethodInfo.targetType = referencedTypes.length == 0 ? List.of(typeByName) : Arrays.asList(referencedTypes);
                    }
                }
                return cacheMethodInfo;
            }).filter(Objects::nonNull).collect(Collectors.toList());
            System.out.println("find ExtensionMethods size:" + collect.size());
            return collect;
        }, new ProgressIndicatorBase());
    }

    @NotNull
    protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement psiElement, @NotNull Class<Psi> eleType) {
        final List<Psi> emptyResult = Collections.emptyList();
        if (!ZrPluginUtil.hasZrPlugin(psiElement.getProject())) return emptyResult;
        if (PsiMethod.class.isAssignableFrom(eleType)) {
            final List<CacheMethodInfo> psiMethods = getCachedAllMethod(psiElement.getProject()).stream().filter(a -> !a.cover).collect(Collectors.toList());
            if (psiElement instanceof PsiClass) {
                PsiClass psiClass = (PsiClass) psiElement;
                final String type2Str = psiClass.getQualifiedName();
                if (type2Str == null) {
                    return Collections.emptyList();
                }
                if (Objects.equals(type2Str, "_Dummy_.__Array__")) {
                    final List<PsiMethod> collect = psiMethods.stream().map(methodInfo -> {
                        return methodInfo.targetType.stream().filter(type1 -> type1 instanceof PsiArrayType).map(type -> buildMethodBy(methodInfo.isStatic, psiClass, methodInfo.method)).filter(Objects::nonNull).collect(Collectors.toList());
                    }).flatMap(Collection::stream).collect(Collectors.toList());
                    return (List<Psi>) collect;
                }

                PsiType type2 = PsiTypesUtil.getClassType(psiClass);
                List<PsiMethod> ownMethods = psiClass instanceof PsiExtensibleClass ? ((PsiExtensibleClass) psiClass).getOwnMethods() : List.of();
                final List<PsiMethod> collect = psiMethods.stream().map(methodInfo -> {
                    return methodInfo.targetType.stream().filter(type -> {
                        try {
                            return type.getCanonicalText().split("<")[0].equals(type2.getCanonicalText().split("<")[0]);
                        } catch (ProcessCanceledException e) {
                            throw e;
                        } catch (Exception e) {
                            return false;
                        }
                    }).map(type -> {
                        final PsiClass psiClass2 = PsiTypesUtil.getPsiClass(type);
                        final PsiParameterList parameterList = methodInfo.method.getParameterList();
                        if (psiClass2 == null || (!methodInfo.isStatic && parameterList.getParameters().length == 0) || !parameterList.isValid()) {
                            return buildMethodBy(methodInfo.isStatic, psiClass, methodInfo.method);
                        }
                        PsiClass targetClass = methodInfo.isStatic ? psiClass2 : PsiTypesUtil.getPsiClass(parameterList.getParameter(0).getType());
                        if (targetClass instanceof PsiTypeParameter) {
                            final JvmReferenceType[] bounds = ((PsiTypeParameter) targetClass).getBounds();
                            if (bounds.length == 0) {
                                targetClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, psiClass.getResolveScope());
                            } else {
                                targetClass = (PsiClass) bounds[0].resolve();
                            }
                        }
                        if (targetClass == null) return null;
                        return buildMethodBy(methodInfo.isStatic, targetClass, methodInfo.method);
                    }).filter(Objects::nonNull).filter(method -> ownMethods.stream().noneMatch(om -> {
                        if (!Objects.equals(om.getName(), method.getName())) {
                            return false;
                        }
                        final PsiParameter[] oParameters = om.getParameterList().getParameters();
                        final PsiParameter[] parameters = method.getParameterList().getParameters();
                        if (oParameters.length != method.getParameterList().getParameters().length) {
                            return false;
                        }
                        for (int i = 0, oParametersLength = oParameters.length; i < oParametersLength; i++) {
                            PsiParameter oParameter = oParameters[i];
                            PsiParameter parameter = parameters[i];
                            if (!Objects.equals(oParameter.getType(), parameter.getType())) {
                                return false;
                            }
                        }
                        return true;
                    })).collect(Collectors.toList());
                }).flatMap(Collection::stream).collect(Collectors.toList());

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
//        final List<CacheMethodInfo> psiMethods = getCachedAllMethod(project);
//        final String type2Str = aClass.getQualifiedName();
//        if (type2Str == null) {
//            return Collections.emptyList();
//        }
//        System.out.println("getPsiExtensionMethods aClass:" + aClass.getName() + " text:" + context.getText());
////        PsiType type2 = PsiClassType.getTypeByName(type2Str, aClass.getProject(), GlobalSearchScope.allScope(aClass.getProject()));
//        final List<ZrPsiExtensionMethod> collect = psiMethods.stream()
//                .filter(methodInfo -> methodInfo.name != null
//                        && methodInfo.targetType.stream().anyMatch(type -> Objects.equals(PsiTypesUtil.getPsiClass(type), aClass)))
//                .map(methodInfo -> {
//                    final List<PsiType> psiTypes = methodInfo.targetType.stream().filter(type -> Objects.equals(PsiTypesUtil.getPsiClass(type), aClass)).collect(Collectors.toList());
//                    return new ZrPsiExtensionMethod(methodInfo.isStatic, PsiTypesUtil.getPsiClass(psiTypes.get(0)), methodInfo.method);
//                })
//                .collect(Collectors.toList());
//        System.out.println("use " + (System.currentTimeMillis() - startTime) + "ms");
//        return collect;
//    }

    public static synchronized List<CacheMethodInfo> getCachedAllMethod(@NotNull Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> CachedValueProvider.Result.create(recoverCache(project), ProjectRootManager.getInstance(project)));
    }

    public static PsiMethod buildMethodBy(boolean isStatic, PsiClass targetClass, PsiMethod targetMethod) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(targetClass.getManager().getProject());
        final StringBuilder methodText = new StringBuilder();
        final LightModifierList newModifierList;
        final PsiTypeParameterList newPsiTypeParameterList;
        final PsiParameterList newPsiParameterList;

        {
            final PsiModifierList modifierList = targetMethod.getModifierList();
            for (@PsiModifier.ModifierConstant String m : PsiModifier.MODIFIERS) {
                if (!m.equalsIgnoreCase("static")) {
                    if (modifierList.hasModifierProperty(m))
                        methodText.append(m);
                } else if (isStatic) {
                    methodText.append(m);
                }
                methodText.append(" ");
            }
        }
        {
            Supplier<PsiTypeParameterList> supplier = () -> {
                final PsiTypeParameterList oTypeParameterList = targetMethod.getTypeParameterList();
                if (oTypeParameterList == null) return null;
                final LightTypeParameterListBuilder builder = new LightTypeParameterListBuilder(targetClass.getManager(), targetClass.getLanguage());
                if (isStatic) return oTypeParameterList;
                if (targetMethod.getParameterList().isEmpty()) return oTypeParameterList;
                final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
                PsiTypesUtil.TypeParameterSearcher searcher = new PsiTypesUtil.TypeParameterSearcher();
                final PsiType type = parameters[0].getType();
                type.accept(searcher);
                final Set<PsiTypeParameter> typeParameters = searcher.getTypeParameters();
                if (typeParameters.size() == 1 && Objects.equals(((PsiTypeParameter) typeParameters.toArray()[0]).getName(), type.getCanonicalText())) {
                    return oTypeParameterList;
                }
                Arrays.stream(oTypeParameterList.getTypeParameters())
                        .filter(a -> typeParameters.stream().noneMatch(o -> Objects.equals(o.getName(), a.getName())))
                        .forEach(builder::addParameter);
                return builder.getTypeParameters().length == 0 ? null : builder;
            };
            newPsiTypeParameterList = supplier.get();
            if (newPsiTypeParameterList != null && newPsiTypeParameterList.getTypeParameters().length != 0) {
                final String collect = Arrays.stream(newPsiTypeParameterList.getTypeParameters()).map(NavigationItem::getName).collect(Collectors.joining(","));
                methodText.append("<").append(collect).append(">");
            }
            methodText.append(" ");
        }
        {
            final PsiType returnType = targetMethod.getReturnType();
            if (null != returnType && returnType.isValid()) {
                methodText.append(returnType.getCanonicalText());
            } else {
                methodText.append("void");
            }
        }
        methodText.append(" ");
        {
            methodText.append(targetMethod.getName());
        }
        {
            Supplier<PsiParameterList> supplier = () -> {
                if (isStatic) return targetMethod.getParameterList();
                else {
                    final PsiParameterList parameterList = targetMethod.getParameterList();
                    final LightParameterListBuilder lightParameterListBuilder = new LightParameterListBuilder(targetMethod.getManager(), targetClass.getLanguage());
                    for (int i = 1; i < parameterList.getParameters().length; i++) {
                        lightParameterListBuilder.addParameter(parameterList.getParameter(i));
                    }
                    return lightParameterListBuilder;
                }
            };
            newPsiParameterList = supplier.get();
            final String collect = Arrays.stream(newPsiParameterList.getParameters())
                    .map(a -> a.getType().getCanonicalText(true) + " " + a.getName())
                    .collect(Collectors.joining(","));
            methodText.append("(").append(collect).append(")");

        }
        methodText.append("{ }");
        PsiMethod result = elementFactory.createMethodFromText(methodText.toString(), targetClass);
        final LightMethodBuilder builder = new ZrPsiExtensionMethod(isStatic, targetClass, targetMethod, targetClass.getManager(), targetMethod.getLanguage(), result.getName(),
                result.getParameterList(), result.getModifierList(), result.getThrowsList(), result.getTypeParameterList());
        builder.setContainingClass(targetClass);
        builder.setMethodReturnType(result.getReturnType());
        builder.setConstructor(false);
        return builder;
    }

    static class ZrPsiExtensionMethod extends LightMethodBuilder implements PsiExtensionMethod {
        boolean isStatic;
        PsiClass targetClass;
        PsiMethod targetMethod;

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
            result = 31 * result + (isStatic ? 1 : 0);
            result = 31 * result + (targetClass != null ? targetClass.hashCode() : 0);
            result = 31 * result + targetMethod.hashCode();
            return result;
        }

        public ZrPsiExtensionMethod(boolean isStatic, PsiClass targetClass, PsiMethod targetMethod, PsiManager manager, Language language, @NlsSafe @NotNull String name, PsiParameterList parameterList, PsiModifierList modifierList, PsiReferenceList throwsList, PsiTypeParameterList typeParameterList) {
            super(manager, language, name, parameterList, modifierList, throwsList, typeParameterList);
            this.isStatic = isStatic;
            this.targetClass = targetClass;
            this.targetMethod = targetMethod;
        }

        @Override
        public @NotNull PsiElement getNavigationElement() {
            return targetMethod;
        }

        @Override
        public @NotNull PsiMethod getTargetMethod() {
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
