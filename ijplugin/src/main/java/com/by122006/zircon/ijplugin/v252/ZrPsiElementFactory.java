package com.by122006.zircon.ijplugin.v252;

import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.java.syntax.parser.ExpressionParser;
import com.intellij.java.syntax.parser.JavaParser;
import com.intellij.java.syntax.parser.PrattExpressionParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import lombok.val;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExArray;
import zircon.example.ExReflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Map;

import static com.intellij.java.syntax.parser.PrattExpressionParserKt.CONDITIONAL_EXPR_PRECEDENCE;

/**
 * @ClassName: ZrPsiElementFactory
 * @Author: 122006
 * @Date: 2025/10/10 17:48
 * @Description:
 */
public class ZrPsiElementFactory implements PsiElementFactory {
    PsiElementFactoryImpl psiElementFactory;

    public ZrPsiElementFactory(@NotNull Project project) {
        psiElementFactory = new PsiElementFactoryImpl(project);
    }

    @Override
    public @NotNull PsiClass createClass(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createClass(s);
    }

    @Override
    public @NotNull PsiClass createInterface(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createInterface(s);
    }

    @Override
    public @NotNull PsiClass createEnum(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createEnum(s);
    }

    @Override
    public @NotNull PsiClass createRecord(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createRecord(s);
    }

    @Override
    public @NotNull PsiClass createAnnotationType(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createAnnotationType(s);
    }

    @Override
    public @NotNull PsiField createField(@NotNull String s, @NotNull PsiType psiType) throws IncorrectOperationException {
        return psiElementFactory.createField(s, psiType);
    }

    @Override
    public @NotNull PsiMethod createMethod(@NotNull String s, PsiType psiType) throws IncorrectOperationException {
        return psiElementFactory.createMethod(s, psiType);
    }

    @Override
    public @NotNull PsiMethod createMethod(@NotNull String s, PsiType psiType, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createMethod(s, psiType, psiElement);
    }

    @Override
    public @NotNull PsiMethod createConstructor() {
        return psiElementFactory.createConstructor();
    }

    @Override
    public @NotNull PsiMethod createConstructor(@NotNull String s) {
        return psiElementFactory.createConstructor(s);
    }

    @Override
    public @NotNull PsiMethod createConstructor(@NotNull String s, @Nullable PsiElement psiElement) {
        return psiElementFactory.createConstructor(s, psiElement);
    }

    @Override
    public @NotNull PsiClassInitializer createClassInitializer() throws IncorrectOperationException {
        return psiElementFactory.createClassInitializer();
    }

    @Override
    public @NotNull PsiParameter createParameter(@NotNull String s, @NotNull PsiType psiType) throws IncorrectOperationException {
        return psiElementFactory.createParameter(s, psiType);
    }

    @Override
    public PsiParameter createParameter(@NotNull String s, @NotNull PsiType psiType, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createParameter(s, psiType, psiElement);
    }

    @Override
    public @NotNull PsiCodeBlock createCodeBlock() {
        return psiElementFactory.createCodeBlock();
    }

    @Override
    public @NotNull PsiClassType createType(@NotNull PsiClass psiClass, @NotNull PsiSubstitutor psiSubstitutor) {
        return psiElementFactory.createType(psiClass, psiSubstitutor);
    }

    @Override
    public @NotNull PsiClassType createType(@NotNull PsiClass psiClass, @NotNull PsiSubstitutor psiSubstitutor, @Nullable LanguageLevel languageLevel) {
        return psiElementFactory.createType(psiClass, psiSubstitutor, languageLevel);
    }

    @Override
    public @NotNull PsiClassType createType(@NotNull PsiJavaCodeReferenceElement psiJavaCodeReferenceElement) {
        return psiElementFactory.createType(psiJavaCodeReferenceElement);
    }

    @Override
    public @NotNull PsiClassType createType(@NotNull PsiClass psiClass, PsiType psiType) {
        return psiElementFactory.createType(psiClass, psiType);
    }

    @Override
    public @NotNull PsiClassType createType(@NotNull PsiClass psiClass, PsiType... psiTypes) {
        return psiElementFactory.createType(psiClass, psiTypes);
    }

    @Override
    public @NotNull PsiSubstitutor createRawSubstitutor(@NotNull PsiTypeParameterListOwner psiTypeParameterListOwner) {
        return psiElementFactory.createRawSubstitutor(psiTypeParameterListOwner);
    }

    @Override
    public @NotNull PsiSubstitutor createSubstitutor(@NotNull Map<PsiTypeParameter, PsiType> map) {
        return psiElementFactory.createSubstitutor(map);
    }

    @Override
    public @Nullable PsiPrimitiveType createPrimitiveType(@NotNull String s) {
        return psiElementFactory.createPrimitiveType(s);
    }

    @Override
    public @NotNull PsiClassType createTypeByFQClassName(@NotNull String s) {
        return psiElementFactory.createTypeByFQClassName(s);
    }

    @Override
    public @NotNull PsiClassType createTypeByFQClassName(@NotNull String s, @NotNull GlobalSearchScope globalSearchScope) {
        return psiElementFactory.createTypeByFQClassName(s, globalSearchScope);
    }

    @Override
    public boolean isValidClassName(@NotNull String s) {
        return psiElementFactory.isValidClassName(s);
    }

    @Override
    public boolean isValidMethodName(@NotNull String s) {
        return psiElementFactory.isValidMethodName(s);
    }

    @Override
    public boolean isValidParameterName(@NotNull String s) {
        return psiElementFactory.isValidParameterName(s);
    }

    @Override
    public boolean isValidFieldName(@NotNull String s) {
        return psiElementFactory.isValidFieldName(s);
    }

    @Override
    public boolean isValidLocalVariableName(@NotNull String s) {
        return psiElementFactory.isValidLocalVariableName(s);
    }

    @Override
    public @NotNull PsiTypeElement createTypeElement(@NotNull PsiType psiType) {
        return psiElementFactory.createTypeElement(psiType);
    }

    @Override
    public @NotNull PsiJavaCodeReferenceElement createReferenceElementByType(@NotNull PsiClassType psiClassType) {
        return psiElementFactory.createReferenceElementByType(psiClassType);
    }

    @Override
    public @NotNull PsiTypeParameterList createTypeParameterList() {
        return psiElementFactory.createTypeParameterList();
    }

    @Override
    public @NotNull PsiTypeParameter createTypeParameter(@NotNull String s, PsiClassType @NotNull [] psiClassTypes) {
        return psiElementFactory.createTypeParameter(s, psiClassTypes);
    }

    @Override
    public @NotNull PsiClassType createType(@NotNull PsiClass psiClass) {
        return psiElementFactory.createType(psiClass);
    }

    @Override
    public @NotNull PsiJavaCodeReferenceElement createClassReferenceElement(@NotNull PsiClass psiClass) {
        return psiElementFactory.createClassReferenceElement(psiClass);
    }

    @Override
    public @NotNull PsiJavaCodeReferenceElement createReferenceElementByFQClassName(@NotNull String s, @NotNull GlobalSearchScope globalSearchScope) {
        return psiElementFactory.createReferenceElementByFQClassName(s, globalSearchScope);
    }

    @Override
    public @NotNull PsiJavaCodeReferenceElement createFQClassNameReferenceElement(@NotNull String s, @NotNull GlobalSearchScope globalSearchScope) {
        return psiElementFactory.createFQClassNameReferenceElement(s, globalSearchScope);
    }

    @Override
    public @NotNull PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull PsiPackage psiPackage) throws IncorrectOperationException {
        return psiElementFactory.createPackageReferenceElement(psiPackage);
    }

    @Override
    public @NotNull PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createPackageReferenceElement(s);
    }

    @Override
    public @NotNull PsiReferenceExpression createReferenceExpression(@NotNull PsiClass psiClass) throws IncorrectOperationException {
        return psiElementFactory.createReferenceExpression(psiClass);
    }

    @Override
    public @NotNull PsiReferenceExpression createReferenceExpression(@NotNull PsiPackage psiPackage) throws IncorrectOperationException {
        return psiElementFactory.createReferenceExpression(psiPackage);
    }

    @Override
    public @NotNull PsiIdentifier createIdentifier(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createIdentifier(s);
    }

    @Override
    public @NotNull PsiKeyword createKeyword(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createKeyword(s);
    }

    @Override
    public @NotNull PsiKeyword createKeyword(@NotNull String s, PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createKeyword(s, psiElement);
    }

    @Override
    public @NotNull PsiImportStatement createImportStatement(@NotNull PsiClass psiClass) throws IncorrectOperationException {
        return psiElementFactory.createImportStatement(psiClass);
    }

    @Override
    public @NotNull PsiImportStatement createImportStatementOnDemand(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createImportStatementOnDemand(s);
    }

    @Override
    public @NotNull PsiDeclarationStatement createVariableDeclarationStatement(@NotNull String s, @NotNull PsiType psiType, @Nullable PsiExpression psiExpression) throws IncorrectOperationException {
        return psiElementFactory.createVariableDeclarationStatement(s, psiType, psiExpression);
    }

    @Override
    public @NotNull PsiDeclarationStatement createVariableDeclarationStatement(@NotNull String s, @NotNull PsiType psiType, @Nullable PsiExpression psiExpression, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createVariableDeclarationStatement(s, psiType, psiExpression, psiElement);
    }

    @Override
    public PsiResourceVariable createResourceVariable(@NotNull String s, @NotNull PsiType psiType, @Nullable PsiExpression psiExpression, @Nullable PsiElement psiElement) {
        return psiElementFactory.createResourceVariable(s, psiType, psiExpression, psiElement);
    }

    @Override
    public @NotNull PsiFragment createStringTemplateFragment(@NotNull String s, @NotNull IElementType iElementType, @Nullable PsiElement psiElement) {
        return psiElementFactory.createStringTemplateFragment(s, iElementType, psiElement);
    }

    @Override
    public @NotNull PsiDocTag createParamTag(@NotNull String s, String s1) throws IncorrectOperationException {
        return psiElementFactory.createParamTag(s, s1);
    }

    @Override
    public @NotNull PsiClass getArrayClass(@NotNull LanguageLevel languageLevel) {
        return psiElementFactory.getArrayClass(languageLevel);
    }

    @Override
    public @NotNull PsiClassType getArrayClassType(@NotNull PsiType psiType, @NotNull LanguageLevel languageLevel) {
        return psiElementFactory.getArrayClassType(psiType, languageLevel);
    }

    @Override
    public boolean isArrayClass(@NotNull PsiClass psiClass) {
        return psiElementFactory.isArrayClass(psiClass);
    }

    @Override
    public @NotNull PsiPackageStatement createPackageStatement(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createPackageStatement(s);
    }

    @Override
    public @NotNull PsiImportStaticStatement createImportStaticStatement(@NotNull PsiClass psiClass, @NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createImportStaticStatement(psiClass, s);
    }

    @Override
    public @NotNull PsiImportStaticStatement createImportStaticStatementFromText(@NotNull String s, @NotNull String s1) throws IncorrectOperationException {
        return psiElementFactory.createImportStaticStatementFromText(s, s1);
    }

    @Override
    public @NotNull PsiImportModuleStatement createImportModuleStatementFromText(@NotNull String s) throws IncorrectOperationException {
        return psiElementFactory.createImportModuleStatementFromText(s);
    }

    @Override
    public @NotNull PsiParameterList createParameterList(String @NotNull [] strings, PsiType @NotNull [] psiTypes) throws IncorrectOperationException {
        return psiElementFactory.createParameterList(strings, psiTypes);
    }

    @Override
    public @NotNull PsiReferenceList createReferenceList(PsiJavaCodeReferenceElement @NotNull [] psiJavaCodeReferenceElements) throws IncorrectOperationException {
        return psiElementFactory.createReferenceList(psiJavaCodeReferenceElements);
    }

    @Override
    public @NotNull PsiSubstitutor createRawSubstitutor(@NotNull PsiSubstitutor psiSubstitutor, PsiTypeParameter @NotNull [] psiTypeParameters) {
        return psiElementFactory.createRawSubstitutor(psiSubstitutor, psiTypeParameters);
    }

    @Override
    public @NotNull PsiElement createDummyHolder(@NotNull String s, @NotNull IElementType iElementType, @Nullable PsiElement psiElement) {
        return psiElementFactory.createDummyHolder(s, iElementType, psiElement);
    }

    @Override
    public @NotNull PsiCatchSection createCatchSection(@NotNull PsiType psiType, @NotNull String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createCatchSection(psiType, s, psiElement);
    }

    @Override
    public @NotNull PsiDocTag createDocTagFromText(@NotNull @NonNls String s) throws IncorrectOperationException {
        return psiElementFactory.createDocTagFromText(s);
    }

    @Override
    public @NotNull PsiDocComment createDocCommentFromText(@NotNull @NonNls String s) {
        return psiElementFactory.createDocCommentFromText(s);
    }

    @Override
    public @NotNull PsiDocComment createDocCommentFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createDocCommentFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiClass createClassFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createClassFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiField createFieldFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createFieldFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiMethod createMethodFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement, LanguageLevel languageLevel) throws IncorrectOperationException {
        return psiElementFactory.createMethodFromText(s, psiElement, languageLevel);
    }

    @Override
    public @NotNull PsiMethod createMethodFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) {
        return psiElementFactory.createMethodFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiParameter createParameterFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createParameterFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiImplicitClass createImplicitClassFromText(@NotNull String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createImplicitClassFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiRecordHeader createRecordHeaderFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createRecordHeaderFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiResourceVariable createResourceFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createResourceFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiType createTypeFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createTypeFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiTypeElement createTypeElementFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createTypeElementFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiJavaCodeReferenceElement createReferenceFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createReferenceFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiCodeBlock createCodeBlockFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createCodeBlockFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiStatement createStatementFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createStatementFromText(s, psiElement);
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public @NotNull PsiExpression createExpressionFromText(@NotNull String text, @Nullable PsiElement context) throws IncorrectOperationException {
        //todo
        final JavaParserUtil.ParserWrapper expression = (builder, languageLevel) ->
        {
            final ExpressionParser expressionParser = (new JavaParser(languageLevel)).getExpressionParser();
            final PrattExpressionParser myNewExpressionParser = expressionParser.reflectionFieldValue("myNewExpressionParser").cast(PrattExpressionParser.class);
            final Map<com.intellij.platform.syntax.SyntaxElementType, Object> ourInfixParsers = myNewExpressionParser.reflectionFieldValue("ourInfixParsers");
            try {
                final Class<?> aClass = PrattExpressionParser.class.getDeclaredClasses().find(a -> a.getName().contains("InfixParser"));
                Object proxyInstance = Proxy.newProxyInstance(
                        aClass.getClassLoader(),
                        new Class<?>[]{aClass},
                        (proxy, method, args) -> {
                            if (method.getName().equals("parse")) {
                                com.intellij.platform.syntax.parser.SyntaxTreeBuilder syntaxTreeBuilder = (com.intellij.platform.syntax.parser.SyntaxTreeBuilder) args[1];
                                PrattExpressionParser parser = (PrattExpressionParser) args[0];
                                com.intellij.platform.syntax.parser.SyntaxTreeBuilder.Marker beforeLhs = (SyntaxTreeBuilder.Marker) args[2];
                                com.intellij.platform.syntax.SyntaxElementType binOpType = (com.intellij.platform.syntax.SyntaxElementType) args[3];
                                int currentPrecedence = (int) args[4];
                                int mode = (int) args[5];

                                syntaxTreeBuilder.advanceLexer(); // skipping ?:

                                val falsePart = parser.tryParseWithPrecedenceAtMost(builder, CONDITIONAL_EXPR_PRECEDENCE, mode);
                                if (falsePart == null) {
//                                    error(builder, message("expected.expression"))
                                }
                                beforeLhs.done(ZrJavaSyntaxTokenType.ELVIS);
                                return null;
                            }
                            return method.invoke(proxy, args);
                        });
                final Constructor<?> constructor = PrattExpressionParser.class.getDeclaredClasses().find(a -> a.getName().contains("ParserData")).getConstructors()[0];
                constructor.setAccessible(true);
                ourInfixParsers.put(ZrJavaSyntaxTokenType.ELVIS, constructor.newInstance(CONDITIONAL_EXPR_PRECEDENCE, proxyInstance));

            } catch (Exception e) {
                e.printStackTrace();
            }
            expressionParser.parse(builder);
        };
        DummyHolder holder = DummyHolderFactory.createHolder(psiElementFactory.reflectionFieldValue("myManager"), new JavaDummyElement(text, expression, level(context)), context);
        PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
        if (!(element instanceof PsiExpression)) {
            throw new RuntimeException("Incorrect expression '" + text + "' " + holder);
        } else {
            return (PsiExpression) element;
        }
    }

    protected static LanguageLevel level(@Nullable PsiElement context) {
        return context != null && context.isValid() ? PsiUtil.getLanguageLevel(context) : LanguageLevel.HIGHEST;
    }

    @Override
    public @NotNull PsiComment createCommentFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createCommentFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiTypeParameter createTypeParameterFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createTypeParameterFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiAnnotation createAnnotationFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createAnnotationFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiEnumConstant createEnumConstantFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createEnumConstantFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiType createPrimitiveTypeFromText(@NotNull @NonNls String s) throws IncorrectOperationException {
        return psiElementFactory.createPrimitiveTypeFromText(s);
    }

    @Override
    public @NotNull PsiJavaModule createModuleFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createModuleFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiStatement createModuleStatementFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createModuleStatementFromText(s, psiElement);
    }

    @Override
    public @NotNull PsiJavaModuleReferenceElement createModuleReferenceFromText(@NotNull @NonNls String s, @Nullable PsiElement psiElement) throws IncorrectOperationException {
        return psiElementFactory.createModuleReferenceFromText(s, psiElement);
    }
}
