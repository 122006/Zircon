package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeMapper;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.TypeAnnotationProvider;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.light.LightParameterListBuilder;
import com.intellij.psi.impl.light.LightTypeParameter;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.resolve.graphInference.PsiGraphInferenceHelper;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import zircon.ExMethod;

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
            PsiClass @NotNull [] psiClasses = JavaPsiFacade.getInstance(project)
                                                           .findClasses(qualifiedName, GlobalSearchScope.allScope(project));
            final List<CacheMethodInfo> collect = Arrays.stream(psiClasses)
                                                        .map(psiClass -> {
                                                            final String annotationShortName = ReadAction.compute(psiClass::getName);
                                                            if (annotationShortName == null) return null;
                                                            return JavaAnnotationIndex.getInstance()
                                                                                      .get(annotationShortName, project, GlobalSearchScope.allScope(project));
                                                        })
                                                        .filter(Objects::nonNull)
                                                        .flatMap(Collection::stream).map(element -> {
                        final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
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
                                if (type instanceof PsiPrimitiveType) {
                                    type = ((PsiPrimitiveType) type).getBoxedType(ex);
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
                            final List<PsiTypeParameter> typeParameters = Arrays.stream(method.getTypeParameters())
                                                                                .filter(a -> Objects.equals(a.getName(), parameter
                                                                                        .getType().getCanonicalText()))
                                                                                .collect(Collectors.toList());
                            if (typeParameters.isEmpty()) {
                                cacheMethodInfo.targetType = Arrays.asList(TypeConversionUtil.erasure(parameter.getType()));
                            } else {
                                final PsiClassType[] referencedTypes = typeParameters.get(0).getExtendsList()
                                                                                     .getReferencedTypes();
                                final PsiClassType typeByName = PsiClassType.getJavaLangObject(annotation.getManager(), GlobalSearchScope.projectScope(project));
                                cacheMethodInfo.targetType = referencedTypes.length == 0 ? Arrays.asList(typeByName) : Arrays.asList(referencedTypes);
                            }
                        }
                        return cacheMethodInfo;
                    }).filter(Objects::nonNull).collect(Collectors.toList());
            System.out.println("find ExtensionMethods size:" + collect.size());
            return collect;
        }, new ProgressIndicatorBase());
    }

    public static PsiTypeParameter isTypeParams(String name, PsiTypeParameter[] typeParams) {
        return Arrays.stream(typeParams).filter(a -> Objects.equals(a.getName(), name)).findFirst().orElse(null);
    }

    @Override
    protected List<PsiExtensionMethod> getExtensionMethods(@NotNull PsiClass siteClass, @NotNull String nameHint, @NotNull PsiElement context) {
        final List<PsiExtensionMethod> emptyResult = Collections.emptyList();
        if (!ZrPluginUtil.hasZrPlugin(siteClass.getProject())) return emptyResult;
        if (DumbService.isDumb(context.getProject())) return emptyResult;
        Predicate<ZrPsiExtensionMethod> predicate;
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
                    final PsiMethod targetMethod = extensionMethod.getTargetMethod();
                    if (targetMethod.getParameterList().isEmpty()) return false;
                    final PsiParameter parameter = targetMethod.getParameterList().getParameter(0);
                    if (parameter == null) return false;
                    PsiType methodFirstParamType = parameter.getType();
//                    final PsiTypeParameter typeParameter = isTypeParams(methodFirstParamType.getCanonicalText(), targetMethod.getTypeParameters());
//                    if (typeParameter!=null) {
//                        extensionMethod.sitePsiSubstitutor = PsiSubstitutor.EMPTY.put(typeParameter, type);
//                    }
                    methodFirstParamType = TypeConversionUtil.erasure(methodFirstParamType);
                    return TypeConversionUtil.isAssignable(methodFirstParamType, type);
                };
            } else {
                final PsiElement resolve = ((PsiReferenceExpression) qualifierExpression).resolve();
                if (resolve instanceof PsiClass) {
                    ownType = PsiTypesUtil.getClassType((PsiClass) resolve);
                    predicate = extensionMethod -> {
                        if (!extensionMethod.isStatic) return true;
                        return TypeConversionUtil.isAssignable(PsiTypesUtil.getClassType(extensionMethod.targetClass), ownType);
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
            predicate = extensionMethod -> {
                if (!extensionMethod.isStatic) return false;
                return true;
            };
        } else {
            ownType = PsiTypesUtil.getClassType(siteClass);
            predicate = a -> true;
        }
        if (ownType == null) return emptyResult;
        final List<CacheMethodInfo> psiMethods = getCachedAllMethod(siteClass.getProject()).stream()
                                                                                           .filter(a -> Objects.equals(a.name, nameHint))
                                                                                           .filter(a -> !a.cover)
                                                                                           .collect(Collectors.toList());
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(siteClass.getManager().getProject());
        if (elementFactory == null) return emptyResult;
        final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(siteClass);
        final PsiClass arrayClass = elementFactory.getArrayClass(languageLevel);
        if (ownClass == null) return emptyResult;
        if (Objects.equals(ownClass, arrayClass)) {
            final List<PsiExtensionMethod> collect = psiMethods.stream().map(methodInfo -> {
                return methodInfo.targetType.stream()
                                            .filter(type1 -> type1 instanceof PsiArrayType || type1.equalsToText(CommonClassNames.JAVA_LANG_OBJECT))
                                            .map(type -> buildMethodBy(methodInfo.isStatic, siteClass, methodInfo.method, ownType))
                                            .filter(Objects::nonNull).collect(Collectors.toList());
            }).flatMap(Collection::stream).filter(predicate).collect(Collectors.toList());
            return collect;
        }

        List<PsiMethod> ownMethods = siteClass instanceof PsiExtensibleClass ? ((PsiExtensibleClass) siteClass).getOwnMethods() : Arrays.asList();

        final List<PsiExtensionMethod> collect = psiMethods.stream().map(methodInfo -> {
            return methodInfo.targetType.stream().filter(type -> {
                try {
                    type = TypeConversionUtil.erasure(type);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return TypeConversionUtil.isAssignable(type, ownType);
            }).map(type -> {
                final PsiClass psiClass2 = PsiTypesUtil.getPsiClass(type);
                final PsiParameterList parameterList = methodInfo.method.getParameterList();
                if (psiClass2 == null || (!methodInfo.isStatic && parameterList.getParameters().length == 0) || !parameterList.isValid()) {
                    return buildMethodBy(methodInfo.isStatic, siteClass, methodInfo.method, ownType);
                }
                PsiClass targetClass = methodInfo.isStatic ? psiClass2 : PsiTypesUtil.getPsiClass(TypeConversionUtil.erasure(parameterList
                        .getParameter(0).getType()));
                if (targetClass == null) return null;
                return buildMethodBy(methodInfo.isStatic, targetClass, methodInfo.method, ownType);
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
            })).filter(predicate).collect(Collectors.toList());
        }).flatMap(Collection::stream).collect(Collectors.toList());

        return collect;

    }
    static final Key cachedAllExMethod = Key.create("CachedAllExMethod");

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
                    Arrays.stream(oTypeParameterList.getTypeParameters()).filter(a -> Arrays.stream(classTypeParameters)
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

    static class ZrPsiExtensionMethod extends LightMethodBuilder implements PsiExtensionMethod {
        boolean isStatic;
        PsiClass targetClass;
        PsiMethod targetMethod;
        @Nullable PsiSubstitutor sitePsiSubstitutor;

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
        public @NotNull MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
            if (sitePsiSubstitutor != null) substitutor = substitutor.putAll(sitePsiSubstitutor);
            return super.getSignature(substitutor);
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
            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(targetClass.getManager()
                                                                                                .getProject());
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
                        final Method registerImportStatement = refCountHolder.getClass()
                                                                             .getDeclaredMethod("registerImportStatement", PsiReference.class, PsiImportStatementBase.class);
                        final PsiReference reference = getReference();
                        final PsiImportStatement importStatement = PsiElementFactory.getInstance(getProject())
                                                                                    .createImportStatement(targetMethod.getContainingClass());
                        registerImportStatement.invoke(registerImportStatement, reference, importStatement);
                    }
                } catch (ProcessCanceledException e) {
                    throw e;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
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
                    final PsiType newBoundBound = ((PsiWildcardType) newBound).getBound();
                    return !((PsiWildcardType) newBound).isBounded() ? PsiWildcardType.createUnbounded(wildcardType.getManager())
                            : rebound(wildcardType, newBoundBound);
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
