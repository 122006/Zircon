package com.by122006.zircon.ijplugin;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.List;

public class ZrSyntaxCompatibilityTest
        extends LightJavaCodeInsightFixtureTestCase {

    public void testElvisUsesZirconPsiAndSyntheticNullLiteral() {
        PsiFile file = myFixture.configureByText(
                JavaFileType.INSTANCE,
                "class Test { java.lang.String value; "
                        + "java.lang.String result = value ?: \"fallback\"; }");

        PsiConditionalExpression expression =
                PsiTreeUtil.findChildOfType(file, PsiConditionalExpression.class);
        assertNotNull(expression);
        assertInstanceOf(expression, ZrPsiConditionalExpressionImpl.class);

        ZrPsiConditionalExpressionImpl zirconExpression =
                (ZrPsiConditionalExpressionImpl) expression;
        assertTrue(zirconExpression.isElvisExpression());

        PsiExpression thenExpression = expression.getThenExpression();
        assertEquals("value", thenExpression.getText());
        PsiLiteralExpression syntheticLiteral =
                findSyntheticElvisLiteral(expression);
        assertNotNull(syntheticLiteral);
        assertNull(syntheticLiteral.getValue());
        assertNotNull(expression.getType());
        assertEquals("java.lang.String",
                expression.getType().getCanonicalText());
    }

    public void testOrdinaryConditionalKeepsJavaSemantics() {
        PsiFile file = myFixture.configureByText(
                JavaFileType.INSTANCE,
                "class Test { String result = true ? \"left\" : \"right\"; }");

        PsiConditionalExpression expression =
                PsiTreeUtil.findChildOfType(file, PsiConditionalExpression.class);
        assertNotNull(expression);
        assertInstanceOf(expression, ZrPsiConditionalExpressionImpl.class);
        assertFalse(((ZrPsiConditionalExpressionImpl) expression).isElvisExpression());
        assertEquals("\"left\"", expression.getThenExpression().getText());
        assertEquals("java.lang.String",
                expression.getType().getCanonicalText());
    }

    public void testFormatterDoesNotSplitElvisOperator() {
        PsiFile file = myFixture.configureByText(
                JavaFileType.INSTANCE,
                "class Test { String value; String fallback; String result=value?:fallback; }");

        WriteCommandAction.runWriteCommandAction(
                getProject(),
                (Runnable) () ->
                        CodeStyleManager.getInstance(getProject()).reformat(file));

        assertTrue(file.getText().contains("value ?: fallback"));
        assertFalse(file.getText().contains("value ? : fallback"));
    }

    public void testElvisSurvivesIncrementalReparse() {
        PsiFile file = myFixture.configureByText(
                JavaFileType.INSTANCE,
                "class Test { java.lang.String value; "
                        + "java.lang.String result = value ?: \"fallback\"; }");
        assertValidElvis(file);

        Document document = PsiDocumentManager.getInstance(getProject())
                .getDocument(file);
        assertNotNull(document);
        int classEnd = document.getText().lastIndexOf('}');
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> document.insertString(classEnd, " int unrelated = 1;"));
        PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        assertValidElvis(file);

        int fallbackStart = document.getText().indexOf("\"fallback\"");
        WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> document.replaceString(
                        fallbackStart,
                        fallbackStart + "\"fallback\"".length(),
                        "\"next\""));
        PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        assertValidElvis(file);
    }

    public void testElvisPreservesAssignmentTargetForDiamond() {
        enableZirconErrorFilter();
        PsiFile file = myFixture.configureByText(
                JavaFileType.INSTANCE,
                "class Test { "
                        + "static class Root {} "
                        + "static class Holder<T> {} "
                        + "static class HolderImpl<T> extends Holder<T> {} "
                        + "Holder<Root> value; "
                        + "Holder<Root> result = "
                        + "value ?: new HolderImpl<>(); }");

        PsiConditionalExpression expression =
                PsiTreeUtil.findChildOfType(file, PsiConditionalExpression.class);
        assertNotNull(expression);
        assertNotNull(expression.getType());
        PsiField result =
                PsiTreeUtil.getParentOfType(expression, PsiField.class);
        assertNotNull(result);
        assertEquals(result.getType().getCanonicalText(),
                expression.getType().getCanonicalText());
        myFixture.checkHighlighting(false, false, false);
    }

    public void testElvisContributesBothOperandsToLambdaInference() {
        enableZirconErrorFilter();
        myFixture.configureByText(
                JavaFileType.INSTANCE,
                "class Test { "
                        + "static class Root {} "
                        + "static class Child extends Root {} "
                        + "interface Factory<T> { T get(); } "
                        + "Root value; "
                        + "static <T> void check(Factory<T> first, "
                        + "Factory<T> second) {} "
                        + "void run() { "
                        + "check(() -> (value ?: new Child()) ?: new Child(), "
                        + "() -> new Child()); "
                        + "check(() -> (value ?: new Child()) ?: "
                        + "(value ?: new Child()), () -> new Child()); "
                        + "} }");

        myFixture.checkHighlighting(false, false, false);
    }

    public void testElvisPropagatesInvocationTargetToGenericOperands() {
        enableZirconErrorFilter();
        PsiFile file = myFixture.configureByText(
                JavaFileType.INSTANCE,
                "class Test { "
                        + "static class Base {} "
                        + "static <T> T pick() { return null; } "
                        + "static void accept(Base value) {} "
                        + "void run() { accept(pick() ?: pick()); } }");

        PsiConditionalExpression expression =
                PsiTreeUtil.findChildOfType(file, PsiConditionalExpression.class);
        assertNotNull(expression);
        assertNotNull(expression.getType());
        assertEquals("Test.Base", expression.getType().getCanonicalText());
        myFixture.checkHighlighting(false, false, false);
    }

    public void testPrimitiveArrayDoesNotReceiveGenericArrayExtension() {
        enableZirconErrorFilter();
        myFixture.addClass(
                "package extensions; "
                        + "public class Arrays { "
                        + "@zircon.ExMethod "
                        + "public static <T> T[] copy(T[] value) { "
                        + "return value; } "
                        + "@zircon.ExMethod "
                        + "public static int[] copy(int[] value) { "
                        + "return value; } "
                        + "}");
        PsiFile file = myFixture.configureByText(
                JavaFileType.INSTANCE,
                "import extensions.Arrays; "
                        + "class Test { "
                        + "int[] primitive; "
                        + "int[] primitiveCopy() { return primitive.copy(); } "
                        + "static class Item {} "
                        + "Item[] reference; "
                        + "Item[] referenceCopy() { return reference.copy(); } "
                        + "}");

        List<PsiMethodCallExpression> calls = new ArrayList<>(
                PsiTreeUtil.findChildrenOfType(
                        file, PsiMethodCallExpression.class));
        assertEquals(2, calls.size());
        assertExtensionReceiver(calls.get(0), "int[]");
        assertExtensionReceiver(calls.get(1), "T[]");
        myFixture.checkHighlighting(false, false, false);
    }

    private static void assertExtensionReceiver(
            PsiMethodCallExpression call, String expectedReceiver) {
        PsiMethod resolved = call.resolveMethod();
        assertInstanceOf(resolved, PsiExtensionMethod.class);
        PsiMethod target =
                ((PsiExtensionMethod) resolved).getTargetMethod();
        assertEquals(expectedReceiver,
                target.getParameterList()
                        .getParameter(0)
                        .getType()
                        .getCanonicalText());
    }

    private void enableZirconErrorFilter() {
        myFixture.addClass(
                "package zircon; public @interface ExMethod {}");
    }

    private static void assertValidElvis(PsiFile file) {
        PsiConditionalExpression expression =
                PsiTreeUtil.findChildOfType(file, PsiConditionalExpression.class);
        assertNotNull(expression);
        assertInstanceOf(expression, ZrPsiConditionalExpressionImpl.class);
        assertTrue(((ZrPsiConditionalExpressionImpl) expression)
                .isElvisExpression());
        assertEquals("java.lang.String",
                expression.getType().getCanonicalText());

        PsiExpression thenExpression = expression.getThenExpression();
        assertEquals("value", thenExpression.getText());
        PsiLiteralExpression syntheticLiteral =
                findSyntheticElvisLiteral(expression);
        assertNotNull(syntheticLiteral);
        assertNotNull(syntheticLiteral.getNode().getFirstChildNode());
        assertNull(syntheticLiteral.getNode().getFirstChildNode().getTreeNext());
    }

    private static PsiLiteralExpression findSyntheticElvisLiteral(
            PsiConditionalExpression expression) {
        return PsiTreeUtil.findChildrenOfType(
                        expression, PsiLiteralExpression.class)
                .stream()
                .filter(literal -> literal.getTextLength() == 0)
                .findFirst()
                .orElse(null);
    }
}
