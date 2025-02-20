package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.resolve.graphInference.PsiGraphInferenceHelper;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.sun.tools.javac.parser.CompareSameMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.ExMethod;
import zircon.example.ExCollection;
import zircon.example.ExStream;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class ZrPsiAugmentProvider extends PsiAugmentProvider {
    private static final Logger LOG = Logger.getInstance(ZrPsiAugmentProvider.class.getName());


    public static class CacheMethodInfo {
        List<PsiType> targetType = new ArrayList<>();
        boolean isStatic = false;
        boolean cover = false;

        boolean innerInvoker = false;
        String name;
        PsiMethod method;
    }


    public static synchronized List<CacheMethodInfo> recoverCache(Project project) {
        return ProgressManager.getInstance().runProcess(() -> {
            final String qualifiedName = ExMethod.class.getName();
            PsiClass @NotNull [] psiClasses = JavaPsiFacade.getInstance(project)
                    .findClasses(qualifiedName, GlobalSearchScope.allScope(project));
            final List<PsiMethod> distinctMethods = Arrays.stream(psiClasses)
                    .map(psiClass -> {
                        final String annotationShortName = ReadAction.compute(psiClass::getName);
                        if (annotationShortName == null) return null;
                        return JavaAnnotationIndex.getInstance()
                                .get(annotationShortName, project, GlobalSearchScope.allScope(project));
                    })
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream).map(element -> {
                        return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
                    }).filter(Objects::nonNull).distinct().collect(Collectors.toList());
            final List<CacheMethodInfo> collect = distinctMethods.filter(PsiElement::isValid).map(method -> {
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
                        if (type instanceof PsiPrimitiveType) {
                            type = ((PsiPrimitiveType) type).getBoxedType(ex);
                        }
                        cacheMethodInfo.targetType.add(type);
                    } else if (ex instanceof PsiArrayInitializerMemberValue) {
                        final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) ex).getInitializers();
                        cacheMethodInfo.targetType = Arrays.stream(initializers).map(a -> {
                            final PsiTypeElement childOfType = PsiTreeUtil.getChildOfType(a, PsiTypeElement.class);
                            if (childOfType == null) return null;
                            return childOfType.getType();
                        }).filter(Objects::nonNull).collect(Collectors.toList());
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
                    final List<PsiTypeParameter> typeParameters = Arrays.stream(method.getTypeParameters())
                            .filter(a -> Objects.equals(a.getName(), parameter
                                    .getType().getCanonicalText()))
                            .collect(Collectors.toList());
                    if (typeParameters.isEmpty()) {
                        cacheMethodInfo.targetType = Collections.singletonList(TypeConversionUtil.erasure(parameter.getType()));
                    } else {
                        final PsiClassType[] referencedTypes = typeParameters.get(0).getExtendsList()
                                .getReferencedTypes();
                        final PsiClassType typeByName = PsiClassType.getJavaLangObject(annotation.getManager(), GlobalSearchScope.projectScope(project));
                        cacheMethodInfo.targetType = referencedTypes.length == 0 ? List.of(typeByName) : Arrays.asList(referencedTypes);
                    }
                }
                return cacheMethodInfo;
            }).filterNoNull();
            System.out.println("find ExtensionMethods size:" + collect.size());
            return collect;
        }, new ProgressIndicatorBase());
    }

    @Override
    protected List<PsiExtensionMethod> getExtensionMethods(@NotNull PsiClass siteClass, @NotNull String nameHint, @NotNull PsiElement context) {
        final List<PsiExtensionMethod> emptyResult = Collections.emptyList();
        if (!ZrPluginUtil.hasZrPlugin(siteClass.getProject())) return emptyResult;
        if (DumbService.isDumb(context.getProject())) return emptyResult;
        List<CacheMethodInfo> allCacheMethodInfoList = getCachedAllMethod(siteClass.getProject()).stream()
                .filter(a -> Objects.equals(a.name, nameHint))
                .collect(Collectors.toList());
        if (allCacheMethodInfoList.isEmpty()) return emptyResult;
        Predicate<CacheMethodInfo> predicate;
        final PsiType ownType;
        PsiClass ownClass = siteClass;
        if (context instanceof PsiMethodCallExpression) {
            final PsiExpression qualifierExpression = ((PsiMethodCallExpressionImpl) context).getMethodExpression()
                    .getQualifierExpression();
            if (qualifierExpression == null) return emptyResult;
            PsiType type = qualifierExpression.getType();
            if (type != null) {
                ownType = type;
                predicate = extensionMethod -> {
                    if (extensionMethod.isStatic) return true;
                    final PsiMethod targetMethod = extensionMethod.method;
                    return ZrPluginUtil.isAssignableSite(targetMethod, ownType);
                };
            } else {
                final PsiElement resolve = ((PsiReferenceExpression) qualifierExpression).resolve();
                if (resolve instanceof PsiClass) {
                    ownType = PsiTypesUtil.getClassType((PsiClass) resolve);
                    predicate = extensionMethod -> {
                        if (!extensionMethod.isStatic) return true;
                        return extensionMethod.targetType.stream()
                                .anyMatch(a -> TypeConversionUtil.isAssignable(a, ownType));
                    };
                } else {
                    System.out.println("未知调用方类型" + (resolve == null ? null : resolve.getClass().getName()));
                    ownType = null;
                    predicate = a -> true;
                }
            }
        } else if (context instanceof PsiReferenceExpression) {
            if (siteClass instanceof PsiAnonymousClass) {
                ownType = ((PsiAnonymousClass) siteClass).getBaseClassType();
                ownClass = PsiTypesUtil.getPsiClass(ownType);
                predicate = a -> true;
            } else {
                ownType = PsiTypesUtil.getClassType(siteClass);
                predicate = a -> true;
            }
        } else if (context instanceof PsiJavaCodeReferenceElement) {
            ownType = PsiTypesUtil.getClassType(siteClass);
            predicate = extensionMethod -> extensionMethod.isStatic;
        } else {
            ownType = PsiTypesUtil.getClassType(siteClass);
            predicate = a -> true;
        }
        if (ownType == null || !ownType.isValid()) return emptyResult;
        List<CacheMethodInfo> psiMethods = allCacheMethodInfoList.stream()
                .filter(a -> !a.cover)
                .filter(predicate)
                .collect(Collectors.toList());
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(siteClass.getManager().getProject());
        if (elementFactory == null) return emptyResult;
        if (ownClass == null) return emptyResult;
        if (PsiUtil.isArrayClass(ownClass)) {
            List<PsiExtensionMethod> collect = psiMethods.stream().map(methodInfo -> {
                return methodInfo.targetType.filter(type1 -> ZrPluginUtil.isAssignableSite(methodInfo.method, ownType))
                        .map(type -> {
                            final PsiClass targetClass = ownType instanceof PsiCapturedWildcardType ? PsiTypesUtil.getPsiClass(TypeConversionUtil.erasure(ownType)) : siteClass;
                            return buildMethodBy(methodInfo.isStatic, targetClass, methodInfo.method, ownType);
                        })
                        .filterNoNull();
            }).flatMap(Collection::stream).collect(Collectors.toList());
            filterSameMethods(context, collect);
            return collect;
        }

        List<PsiMethod> ownMethods = siteClass instanceof PsiExtensibleClass ? ((PsiExtensibleClass) siteClass).getOwnMethods() : List.of();

        final List<PsiExtensionMethod> collect = psiMethods.stream().map(methodInfo -> {
            return methodInfo.targetType.stream().filter(type -> {
                try {
                    type = TypeConversionUtil.erasure(type);
                } catch (ProcessCanceledException processCanceledException) {
                    throw processCanceledException;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!type.isValid()) return false;
                return TypeConversionUtil.isAssignable(type, ownType);
            }).map(type -> {
                final PsiParameterList parameterList = methodInfo.method.getParameterList();
                if (methodInfo.isStatic) {
                    final PsiClass psiClass2 = PsiTypesUtil.getPsiClass(type);
                    if (psiClass2 == null || !parameterList.isValid()) {
                        return null;
                    }
                    return buildMethodBy(methodInfo.isStatic, psiClass2, methodInfo.method, ownType);
                } else {
                    final PsiClass targetClass = ownType instanceof PsiCapturedWildcardType ? PsiTypesUtil.getPsiClass(TypeConversionUtil.erasure(ownType)) : siteClass;
                    if (targetClass == null) return null;
                    if (parameterList.getParameters().length == 0 || !parameterList.isValid()) {
                        return buildMethodBy(methodInfo.isStatic, targetClass, methodInfo.method, ownType);
                    }
                    return buildMethodBy(methodInfo.isStatic, targetClass, methodInfo.method, ownType);
                }

            }).filter(Objects::nonNull).filter(method -> {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                return ownMethods.stream().noneMatch(om -> {
                    if (!Objects.equals(om.getName(), method.getName())) {
                        return false;
                    }
                    final PsiParameter[] oParameters = om.getParameterList().getParameters();
                    if (oParameters.length != method.getParameterList().getParameters().length) {
                        return false;
                    }
                    if (oParameters.length == 0) {
                        return false;
                    }
                    if (context instanceof PsiMethodCallExpression) {
                        final PsiMethodCallExpression psiMethodCallExpression = (PsiMethodCallExpression) context;
                        final PsiExpressionList argumentList = psiMethodCallExpression.getArgumentList();
                        boolean any = true;
                        if (argumentList.getExpressions().length == oParameters.length) {
                            for (int i = 0, oParametersLength = oParameters.length; i < oParametersLength; i++) {
                                PsiParameter oParameter = oParameters[i];
                                final PsiExpression expression = argumentList.getExpressions()[i];
                                if (expression.getType() == null) continue;
                                final boolean assignableFrom = expression.getType().isConvertibleFrom(oParameter.getType());
                                if (!assignableFrom) {
                                    any = false;
                                    break;
                                }
                            }
                        }
                        if (any) return true;
                    }
                    for (int i = 0, oParametersLength = oParameters.length; i < oParametersLength; i++) {
                        PsiParameter oParameter = oParameters[i];
                        PsiParameter parameter = parameters[i];
                        if (!Objects.equals(PsiTypesUtil.getPsiClass(oParameter.getType()), PsiTypesUtil.getPsiClass(parameter.getType()))) {
                            return false;
                        }
                    }
                    return true;
                });
            }).collect(Collectors.toList());
        }).flatMap(Collection::stream).collect(Collectors.toList());
        filterSameMethods(context, collect);
        return collect;

    }

    @Nullable
    private List<PsiExtensionMethod> filterSameMethods(@NotNull PsiElement context, List<PsiExtensionMethod> psiMethods) {
        Function<PsiElement, String> getPsiClassName = (e) -> {
            final PsiClass psiClass = PsiTreeUtil.getTopmostParentOfType(e, PsiClass.class);
            if (psiClass == null) return null;
            return psiClass.getQualifiedName();
        };
        final String apply = getPsiClassName.apply(context);
        if (apply != null) {
            final List<PsiExtensionMethod> list1 = psiMethods
                    .groupBy(a -> a.getTargetMethod().getSignature(PsiSubstitutor.EMPTY))
                    .values().stream().map(list -> {
                        final CompareSameMethod.CompareEnv env = CompareSameMethod.CompareEnv.create(apply);
                        list.sort((a, b) -> -CompareSameMethod.compare(env
                                , CompareSameMethod.MethodInfo.create(getPsiClassName.apply(a.getTargetMethod()), a)
                                , CompareSameMethod.MethodInfo.create(getPsiClassName.apply(b.getTargetMethod()), b)));
                        return list.limit(1);
                    }).filterNoNull().flat().filterNoNull().list();
            psiMethods.clear();
            psiMethods.addAll(list1);
        }

        return psiMethods;
    }


    static final Key<CachedValue<List<CacheMethodInfo>>> cachedAllExMethod = Key.create("CachedAllExMethod");

    public static synchronized List<CacheMethodInfo> getCachedAllMethod(@NotNull Project project) {
        return CachedValuesManager.getManager(project)
                .getCachedValue(project, cachedAllExMethod, () -> CachedValueProvider.Result.create(recoverCache(project), ProjectRootManager.getInstance(project)), false);
    }

    public static synchronized List<CacheMethodInfo> freshCachedAllMethod(@NotNull Project project) {
        project.putUserData(cachedAllExMethod, null);
        return getCachedAllMethod(project);
    }

    public static @Nullable ZrPsiExtensionMethod buildMethodBy(boolean isStatic, PsiClass targetClass, PsiMethod targetMethod, @NotNull PsiType siteType) {
        try {
            final PsiTypeParameterList newPsiTypeParameterList;
            final PsiParameterList newPsiParameterList;
            if (!targetMethod.isValid()) return null;
            final PsiElementFactory factory = JavaPsiFacade.getInstance(targetMethod.getProject()).getElementFactory();
            final PsiManager targetManager = targetClass.getManager();
            final LightModifierList newModifierList = new LightModifierList(targetManager);
            {
                final PsiModifierList modifierList = targetMethod.getModifierList();
                for (@PsiModifier.ModifierConstant String m : PsiModifier.MODIFIERS) {
                    if (!m.equalsIgnoreCase("static")) {
                        if (modifierList.hasModifierProperty(m)) newModifierList.addModifier(m);
                    } else if (isStatic) {
                        newModifierList.addModifier(m);
                    }
                }
            }
            HashMap<PsiTypeParameter, PsiType> typesMapping = new LinkedHashMap<>();
            {
                Supplier<PsiTypeParameterList> supplier = () -> {
                    final PsiTypeParameterList oTypeParameterList = targetMethod.getTypeParameterList();
                    if (oTypeParameterList == null) return null;
                    final LightTypeParameterListBuilder builder = new LightTypeParameterListBuilder(targetMethod.getManager(), targetMethod.getLanguage());
                    if (isStatic) return oTypeParameterList;
                    if (targetMethod.getParameterList().isEmpty()) return oTypeParameterList;
                    final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
                    PsiType firstParamType = parameters[0].getType();

                    final PsiTypeParameter[] classTypeParameters;
                    if (firstParamType instanceof PsiArrayType) {
                        classTypeParameters = targetClass.getTypeParameters();
                    } else if (firstParamType instanceof PsiClassReferenceType) {
                        final PsiClass firstParamPsiClass = PsiTypesUtil.getPsiClass(firstParamType);
                        if (firstParamPsiClass == null) return oTypeParameterList;
                        classTypeParameters = firstParamPsiClass.getTypeParameters();
                    } else {
                        return oTypeParameterList;
                    }
                    for (PsiTypeParameter classTypeParameter : classTypeParameters) {
                        final String oName = classTypeParameter.getName();
                        final String nName = "ZR" + oName;
                        final Optional<PsiTypeParameter> first = Arrays.stream(targetMethod.getTypeParameters())
                                .filter(a -> Objects.equals(a.getName(), oName))
                                .findFirst();
                        if (first.isPresent()) {
                            final PsiTypeParameter typeParameter = factory.createTypeParameter(nName, new PsiClassType[0]);
                            final PsiClassType type;
                            type = factory.createType(typeParameter, classTypeParameter.getExtendsList()
                                    .getReferencedTypes());
                            typesMapping.put(first.get(), type);
                            builder.addParameter(typeParameter);
                        }
                    }
                    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(targetClass);

                    final PsiSubstitutor psiSubstitutor = new PsiGraphInferenceHelper(targetClass.getManager()).inferTypeArguments(targetMethod.getTypeParameters(), new PsiType[]{firstParamType}, new PsiType[]{siteType}, languageLevel);
                    psiSubstitutor.getSubstitutionMap().forEach((typeParameter, psiType) -> {
                        if (psiType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return;
                        typesMapping.remove(typeParameter);
                        typesMapping.put(typeParameter, psiType);
                    });
                    Arrays.stream(oTypeParameterList.getTypeParameters())
                            .filter(a -> Arrays.stream(classTypeParameters)
                                    .noneMatch(o -> Objects.equals(o.getName(), a.getName())))
                            .forEach(builder::addParameter);
                    return builder.getTypeParameters().length == 0 ? null : builder;
                };

                newPsiTypeParameterList = supplier.get();
            }

            Function<PsiType, PsiType> typeMapping = (oType) -> oType.accept(new MyPsiTypeMapper(typesMapping));

            final PsiType returnType = typeMapping.apply(targetMethod.getReturnType());
            {
                Supplier<PsiParameterList> supplier = () -> {
                    if (isStatic) return targetMethod.getParameterList();
                    else {
                        final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
                        final LightParameterListBuilder lightParameterListBuilder = new LightParameterListBuilder(targetMethod.getManager(), targetMethod.getLanguage());
                        for (int i = 1; i < parameters.length; i++) {
                            final PsiParameter parameter = parameters[i];
                            LightParameter lightParameter = new LightParameter(parameter.getName(), typeMapping.apply(parameter.getType()), targetMethod.getNavigationElement());
                            lightParameterListBuilder.addParameter(lightParameter);
                        }

                        return lightParameterListBuilder;
                    }
                };
                newPsiParameterList = supplier.get();
            }
            ZrPsiExtensionMethod builder;
            builder = new ZrPsiExtensionMethod(isStatic, targetClass, targetMethod, targetClass.getManager(), targetMethod.getLanguage(), targetMethod.getName()
                    , newPsiParameterList
                    , newModifierList, targetMethod.getThrowsList(), newPsiTypeParameterList);
            builder.setContainingClass(targetClass);
            builder.setMethodReturnType(returnType);
            builder.setConstructor(false);
            return builder;
        } catch (IncorrectOperationException e) {
            if (e.getCause() instanceof ProcessCanceledException) {
                throw (ProcessCanceledException) e.getCause();
            }
            throw e;
        }
    }


    private static class MyPsiTypeMapper extends PsiTypeMapper {
        private final HashMap<PsiTypeParameter, PsiType> typesMapping;

        public MyPsiTypeMapper(HashMap<PsiTypeParameter, PsiType> typesMapping) {
            this.typesMapping = typesMapping;
        }

        @Override
        public PsiType visitType(@NotNull PsiType type) {
            return null;
        }

        @Override
        public PsiType visitWildcardType(@NotNull PsiWildcardType wildcardType) {
            final PsiType bound = wildcardType.getBound();
            if (bound == null) {
                return wildcardType;
            } else {
                final PsiType newBound = bound.accept(this);
                if (newBound == null) {
                    return null;
                }
                assert newBound.isValid() : newBound.getClass() + "; " + bound.isValid();
                if (newBound instanceof PsiWildcardType) {
                    if (!((PsiWildcardType) newBound).isBounded())
                        return PsiWildcardType.createUnbounded(wildcardType.getManager());
                    final PsiType unbound = ((PsiWildcardType) newBound).getBound();
                    assert unbound != null;
                    return rebound(wildcardType, unbound);
                }

                return newBound == PsiType.NULL ? newBound : rebound(wildcardType, newBound);
            }
        }

        @NotNull
        private PsiWildcardType rebound(@NotNull PsiWildcardType type, @NotNull PsiType newBound) {
            LOG.assertTrue(type.getBound() != null);
            LOG.assertTrue(newBound.isValid());

            if (type.isExtends()) {
                if (newBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
                    return PsiWildcardType.createUnbounded(type.getManager());
                }
                return PsiWildcardType.createExtends(type.getManager(), newBound);
            }
            return PsiWildcardType.createSuper(type.getManager(), newBound);
        }

        @Override
        public PsiType visitClassType(@NotNull final PsiClassType classType) {
            final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
            final PsiClass aClass = resolveResult.getElement();
            if (aClass == null) return classType;

            PsiUtilCore.ensureValid(aClass);
            if (aClass instanceof PsiTypeParameter) {
                final PsiTypeParameter typeParameter = (PsiTypeParameter) aClass;
                final PsiType result = getFromMap(typeParameter);
                if (PsiType.VOID.equals(result)) {
                    return classType;
                }
                if (result != null) {
                    PsiUtil.ensureValidType(result);
                    if (result instanceof PsiClassType || result instanceof PsiArrayType || result instanceof PsiWildcardType) {
                        return result.annotate(getMergedProvider(classType, result));
                    }
                }
                return result;
            }
            final Map<PsiTypeParameter, PsiType> hashMap = new HashMap<>(2);
            if (!processClass(aClass, resolveResult.getSubstitutor(), hashMap)) {
                return null;
            }
            PsiClassType result = JavaPsiFacade.getElementFactory(aClass.getProject())
                    .createType(aClass, PsiSubstitutor.createSubstitutor(hashMap), classType.getLanguageLevel());
            PsiUtil.ensureValidType(result);
            return result.annotate(classType.getAnnotationProvider());
        }

        private PsiType substituteInternal(@NotNull PsiType type) {
            return type.accept(this);
        }

        private boolean processClass(@NotNull PsiClass resolve, @NotNull PsiSubstitutor originalSubstitutor, @NotNull Map<PsiTypeParameter, PsiType> substMap) {
            final PsiTypeParameter[] params = resolve.getTypeParameters();
            for (final PsiTypeParameter param : params) {
                final PsiType original = originalSubstitutor.substitute(param);
                if (original == null) {
                    substMap.put(param, null);
                } else {
                    substMap.put(param, substituteInternal(original));
                }
            }
            if (resolve.hasModifierProperty(PsiModifier.STATIC)) return true;

            final PsiClass containingClass = resolve.getContainingClass();
            return containingClass == null ||
                    processClass(containingClass, originalSubstitutor, substMap);
        }

        @NotNull
        private static TypeAnnotationProvider getMergedProvider(@NotNull PsiType type1, @NotNull PsiType type2) {
            if (type1.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type1 instanceof PsiClassReferenceType)) {
                return type2.getAnnotationProvider();
            }
            if (type2.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type2 instanceof PsiClassReferenceType)) {
                return type1.getAnnotationProvider();
            }
            return () -> ArrayUtil.mergeArrays(type1.getAnnotations(), type2.getAnnotations());
        }

        private PsiType getFromMap(@NotNull PsiTypeParameter typeParameter) {
            if (typeParameter instanceof LightTypeParameter && ((LightTypeParameter) typeParameter).useDelegateToSubstitute()) {
                typeParameter = ((LightTypeParameter) typeParameter).getDelegate();
            }
            return typesMapping.getOrDefault(typeParameter, PsiType.VOID);
        }
    }
}
