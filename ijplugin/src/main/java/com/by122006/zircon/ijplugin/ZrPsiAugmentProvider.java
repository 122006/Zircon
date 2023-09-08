package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import io.github.classgraph.TypeParameter;
import org.jetbrains.annotations.NonNls;
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
        String name;
        com.intellij.psi.PsiMethod method;
    }


    public static synchronized List<CacheMethodInfo> recoverCache(Project project) {
        return ProgressManager.getInstance().runProcess(() -> {
            final String qualifiedName = ExMethod.class.getName();
            PsiClass @NotNull [] psiClasses = JavaPsiFacade.getInstance(project).findClasses(qualifiedName, GlobalSearchScope.allScope(project));
            final List<CacheMethodInfo> collect = Arrays.stream(psiClasses).map(psiClass -> ReferencesSearch.search(psiClass, GlobalSearchScope.allScope(project)).findAll()).flatMap(Collection::stream).filter(element -> element.getElement().getParent() instanceof PsiAnnotation).map(element -> {
                final com.intellij.psi.PsiMethod method = PsiTreeUtil.getParentOfType(element.getElement(), com.intellij.psi.PsiMethod.class);
                if (method == null) return null;
                return method;
            }).filter(Objects::nonNull).filter(PsiElement::isValid).map(method -> {
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
                        final PsiType type = childOfType.getType();
                        return type;
                    }).filter(Objects::nonNull).collect(Collectors.toList());
                } else {
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
        if (com.intellij.psi.PsiMethod.class.isAssignableFrom(eleType)) {
            final List<CacheMethodInfo> psiMethods = getCachedAllMethod(psiElement.getProject());
            if (psiElement instanceof PsiClass) {
                PsiClass psiClass = (PsiClass) psiElement;
                final String type2Str = psiClass.getQualifiedName();
                if (type2Str == null) {
                    return Collections.emptyList();
                }
                if (Objects.equals(type2Str, "_Dummy_.__Array__")) {
                    final List<com.intellij.psi.PsiMethod> collect = psiMethods.stream().map(methodInfo -> {
                        return methodInfo.targetType.stream().filter(type1 -> type1 instanceof PsiArrayType).map(type -> buildMethodBy(methodInfo.isStatic, psiClass, methodInfo.method)).filter(Objects::nonNull).collect(Collectors.toList());
                    }).flatMap(Collection::stream).collect(Collectors.toList());
                    return (List<Psi>) collect;
                }

                PsiType type2 = PsiTypesUtil.getClassType(psiClass);
                List<com.intellij.psi.PsiMethod> ownMethods = psiClass instanceof PsiExtensibleClass ? ((PsiExtensibleClass) psiClass).getOwnMethods() : List.of();
                final List<com.intellij.psi.PsiMethod> collect = psiMethods.stream().map(methodInfo -> {
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
                        return buildMethodBy(methodInfo.isStatic, methodInfo.isStatic ? psiClass2 : PsiTypesUtil.getPsiClass(parameterList.getParameter(0).getTypeElement().getType()), methodInfo.method);
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

    private synchronized List<CacheMethodInfo> getCachedAllMethod(@NotNull Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> CachedValueProvider.Result.create(recoverCache(project), ProjectRootManager.getInstance(project)));
    }

    public static PsiMethod buildMethodBy(boolean isStatic, PsiClass targetClass, com.intellij.psi.PsiMethod targetMethod) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(targetClass.getManager().getProject());
        final StringBuilder methodText = new StringBuilder();
        {
            final PsiModifierList modifierList = targetMethod.getModifierList();
            final PsiModifierList copy = (PsiModifierList) modifierList.copy();
            copy.setModifierProperty(PsiModifier.STATIC, isStatic);
            methodText.append(copy.getText());
        }
        methodText.append(" ");
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
            final PsiTypeParameterList psiTypeParameterList = supplier.get();
            if (psiTypeParameterList != null && psiTypeParameterList.getTypeParameters().length != 0) {
                final String collect = Arrays.stream(psiTypeParameterList.getTypeParameters()).map(NavigationItem::getName).collect(Collectors.joining(","));
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
            final PsiParameterList psiTypeParameterList = supplier.get();
            final String collect = Arrays.stream(psiTypeParameterList.getParameters())
                    .map(a -> a.getType().getCanonicalText(true) + " " + a.getName())
                    .collect(Collectors.joining(","));
            methodText.append("(").append(collect).append(")");

        }
        methodText.append("{ }");
        PsiMethod result = elementFactory.createMethodFromText(methodText.toString(), targetClass);
//        return new ZrPsiExtensionMethod2(isStatic, targetClass, result);
        final LightMethodBuilder builder = new LightMethodBuilder(targetClass.getManager(), targetMethod.getLanguage(), result.getName(),
                result.getParameterList(), result.getModifierList(), result.getThrowsList(), result.getTypeParameterList());
        builder.setContainingClass(targetClass);
        builder.setMethodReturnType(result.getReturnType());
        builder.setConstructor(false);
        builder.setNavigationElement(targetMethod);
        System.out.println("==============\n" + methodText + "\nresult.getTypeParameterList()=" + (result.getTypeParameterList() == null ? null : result.getTypeParameterList().getText()));
        return builder;
    }

    public static class TestClass<E> {
        public <M extends E> E a(Function<List<E>, ?> function) {
            return null;
        }
    }
}
