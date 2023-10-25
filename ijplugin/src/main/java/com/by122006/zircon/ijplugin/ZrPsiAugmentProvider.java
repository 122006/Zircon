package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.lang.Language;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import zircon.ExMethod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
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
                        final PsiTypeElement childOfType = PsiTreeUtil.getChildOfType(ex, PsiTypeElement.class);
                        if (childOfType == null) return null;
                        PsiType type = childOfType.getType();
                        if (type instanceof PsiPrimitiveType){
                            type=((PsiPrimitiveType) type).getBoxedType(ex);
                        }
                        cacheMethodInfo.targetType.add(type);
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
                final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiElement.getManager().getProject());
                if (Objects.equals(type2Str, elementFactory.getArrayClass(PsiUtil.getLanguageLevel(psiElement)).getQualifiedName())) {
                    final List<PsiMethod> collect = psiMethods.stream().map(methodInfo -> {
                        return methodInfo.targetType.stream().filter(type1 -> type1 instanceof PsiArrayType).map(type -> buildMethodBy(methodInfo.isStatic, psiClass, methodInfo.method)).filter(Objects::nonNull).collect(Collectors.toList());
                    }).flatMap(Collection::stream).collect(Collectors.toList());
                    return (List<Psi>) collect;
                }

                List<PsiMethod> ownMethods = psiClass instanceof PsiExtensibleClass ? ((PsiExtensibleClass) psiClass).getOwnMethods() : List.of();
                final List<PsiMethod> collect = psiMethods.stream().map(methodInfo -> {
                    return methodInfo.targetType.stream().filter(type -> {
                        try {
                            return TypeConversionUtil.erasure(type).equalsToText(type2Str);
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
                        PsiClass targetClass = methodInfo.isStatic ? psiClass2 : PsiTypesUtil.getPsiClass(TypeConversionUtil.erasure(parameterList.getParameter(0).getType()));
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
    static final Key cachedAllExMethod = Key.create("CachedAllExMethod");

    public static synchronized List<CacheMethodInfo> getCachedAllMethod(@NotNull Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, cachedAllExMethod, () -> CachedValueProvider.Result.create(recoverCache(project), ProjectRootManager.getInstance(project)), false);
    }

    public static synchronized List<CacheMethodInfo> freshCachedAllMethod(@NotNull Project project) {
        project.putUserData(cachedAllExMethod, null);
        return getCachedAllMethod(project);
    }

    public static PsiMethod buildMethodBy(boolean isStatic, PsiClass targetClass, PsiMethod targetMethod) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(targetClass.getManager().getProject());
        final StringBuilder methodText = new StringBuilder();
        final LightModifierList newModifierList;
        final PsiTypeParameterList newPsiTypeParameterList;
        final PsiParameterList newPsiParameterList;
        if (!targetMethod.isValid()) return targetMethod;

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
        HashMap<PsiTypeParameter, PsiType> typesMapping = new LinkedHashMap<>();
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
                final PsiClass firstParamPsiClass = PsiTypesUtil.getPsiClass(type);
                if (firstParamPsiClass == null) return oTypeParameterList;
                if (!(type instanceof PsiClassReferenceType)) return oTypeParameterList;
                final PsiTypeParameter[] classTypeParameters = firstParamPsiClass.getTypeParameters();
                final PsiType @NotNull [] paramsTypeParameters = ((PsiClassReferenceType) type).getParameters();
                for (int i = 0; i < classTypeParameters.length; i++) {
                    PsiTypeParameter classTypeParameter = classTypeParameters[i];
                    final String oName = classTypeParameter.getName();
//                    final PsiClassType oType = PsiClassType.getTypeByName(oName, targetMethod.getProject(), targetMethod.getResolveScope());
                    final String nName = "ZR" + oName;
                    final Optional<PsiTypeParameter> first = Arrays.stream(targetMethod.getTypeParameters()).filter(a -> a.getName().equals(oName))
                            .findFirst();
                    if (first.isPresent()) {
                        typesMapping.put(first.get(), new PsiImmediateClassType(new LightPsiClassBuilder(targetMethod, nName), PsiSubstitutor.EMPTY));
                        builder.addParameter(new LightTypeParameterBuilder(nName, targetMethod, i));
                    }
                }
                for (int i = 0; i < paramsTypeParameters.length; i++) {
                    final PsiType paramsTypeParameter = paramsTypeParameters[i];
                    final Optional<PsiTypeParameter> first = Arrays.stream(oTypeParameterList.getTypeParameters())
                            .filter(a -> paramsTypeParameter instanceof PsiClassReferenceType
                                    && Objects.equals(a.getName(), ((PsiClassReferenceType) paramsTypeParameter).getName()))
                            .findFirst();
                    final PsiTypeParameter typeParameter = firstParamPsiClass.getTypeParameters()[i];
                    first.ifPresent(psiTypeParameter -> typesMapping.put(psiTypeParameter, PsiTypesUtil.getClassType(typeParameter)));
                }
                Arrays.stream(oTypeParameterList.getTypeParameters())
                        .filter(a -> Arrays.stream(classTypeParameters).noneMatch(o -> Objects.equals(o.getName(), a.getName())))
                        .forEach(builder::addParameter);
                return builder.getTypeParameters().length == 0 ? null : builder;
            };
            newPsiTypeParameterList = supplier.get();
            if (newPsiTypeParameterList != null && newPsiTypeParameterList.getTypeParameters().length != 0) {
                final String collect = Arrays.stream(newPsiTypeParameterList.getTypeParameters())
                        .map(NavigationItem::getName).collect(Collectors.joining(","));
                methodText.append("<").append(collect).append(">");
            }
            methodText.append(" ");
        }
        PsiSubstitutor substitutor = new EmptySubstitutor().putAll(typesMapping);
        {
            final PsiType returnType = targetMethod.getReturnType();
            if (null != returnType && returnType.isValid()) {
                methodText.append(substitutor.substitute(returnType).getCanonicalText());
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

                    final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
                    final LightParameterListBuilder lightParameterListBuilder = new LightParameterListBuilder(targetMethod.getManager(), targetClass.getLanguage());
                    for (int i = 1; i < parameters.length; i++) {
                        final PsiParameter parameter = parameters[i];
                        LightParameter lightParameter = new LightParameter(parameter.getName(), substitutor.substitute(parameter.getType()), targetMethod);
                        lightParameterListBuilder.addParameter(lightParameter);
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
        LightMethodBuilder builder;
        try {
            final Class<?> aClass = Class.forName("com.intellij.psi.augment.PsiExtensionMethod");
            final Class<?> zrPsiExtensionMethodUpper203 = Class.forName("com.by122006.zircon.ijplugin.ZrPsiExtensionMethodUpper203");
            builder= (LightMethodBuilder) zrPsiExtensionMethodUpper203.getConstructors()[0].newInstance(isStatic, targetClass, targetMethod, targetClass.getManager(), targetMethod.getLanguage(), result.getName(),
                    result.getParameterList(), result.getModifierList(), result.getThrowsList(), result.getTypeParameterList());
        }catch (Exception e){
            builder =new ZrPsiExtensionMethod(isStatic, targetClass, targetMethod, targetClass.getManager(), targetMethod.getLanguage(), result.getName(),
                    result.getParameterList(), result.getModifierList(), result.getThrowsList(), result.getTypeParameterList());
        }
        builder.setContainingClass(targetClass);
        builder.setMethodReturnType(result.getReturnType());
        builder.setConstructor(false);
        return builder;
    }

    static class ZrPsiExtensionMethod extends LightMethodBuilder{
        boolean isStatic;
        PsiClass targetClass;
        PsiMethod targetMethod;

        @Override
        public @NotNull MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
            MethodSignature methodSignatureBackedByPsiMethod;
            if (!isStatic) {
                methodSignatureBackedByPsiMethod = MethodSignatureBackedByPsiMethod.create(targetMethod, substitutor);
                final PsiType[] parameterTypes = methodSignatureBackedByPsiMethod.getParameterTypes();
                PsiType[] newTypes = new PsiType[parameterTypes.length - 1];
                System.arraycopy(parameterTypes, 0, newTypes, 0, parameterTypes.length - 1);
                final Constructor<?> constructor = MethodSignatureBackedByPsiMethod.class.getDeclaredConstructors()[0];
                try {
                    constructor.setAccessible(true);
                    final MethodSignatureBackedByPsiMethod methodSignature = (MethodSignatureBackedByPsiMethod) constructor
                            .newInstance(this, substitutor, PsiUtil.isRawSubstitutor(targetMethod, substitutor), newTypes, methodSignatureBackedByPsiMethod.getTypeParameters());
                    return methodSignature;
                } catch (Exception e) {
                    e.printStackTrace();
                    return super.getSignature(substitutor);
                }
            } else {
                methodSignatureBackedByPsiMethod = super.getSignature(substitutor);
            }
            return methodSignatureBackedByPsiMethod;
        }

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
            return this;
        }

        @Override
        public PsiReference @NotNull [] getReferences() {
            return new PsiReference[]{getReference()};
        }

        @Override
        public PsiReference getReference() {
            final PsiClass containingClass = targetMethod.getContainingClass();
            if (containingClass == null) return null;
            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(targetClass.getManager().getProject());
            return elementFactory.createClassReferenceElement(containingClass);
        }

        @Override
        public @NotNull GlobalSearchScope getResolveScope() {
            return super.getResolveScope();
        }


        @Override
        public void accept(@NotNull PsiElementVisitor visitor) {
            super.accept(visitor);
            if (visitor instanceof HighlightVisitorImpl) {
                HighlightVisitorImpl highlightVisitor = (HighlightVisitorImpl) visitor;
                try {
                    final Field myRefCountHolder = highlightVisitor.getClass().getDeclaredField("myRefCountHolder");
                    myRefCountHolder.setAccessible(true);
                    final Object refCountHolder = myRefCountHolder.get(highlightVisitor);
                    if (refCountHolder != null) {
                        final Method registerImportStatement = refCountHolder.getClass().getDeclaredMethod("registerImportStatement", PsiReference.class, PsiImportStatementBase.class);
                        final PsiReference reference = getReference();
                        final PsiImportStatement importStatement = PsiElementFactory.getInstance(getProject()).createImportStatement(targetMethod.getContainingClass());
                        registerImportStatement.invoke(registerImportStatement, reference, importStatement);
                    }
                } catch (ProcessCanceledException e) {
                    throw e;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }


    }


}
