package com.by122006.zircon.ijplugin252;

import com.by122006.zircon.ijplugin.ZrPsiConditionalExpressionImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.parser.BasicExpressionParser;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.sun.tools.javac.parser.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExReflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @ClassName: ZrExpressionParser
 * @Author: 122006
 * @Date: 2025/7/2 22:45
 * @Description:
 */
@SuppressWarnings("UnstableApiUsage")
public class ZrExpressionParser extends ExpressionParser {
    {
        try {
            final JavaElementType.JavaCompositeElementType expression = (JavaElementType.JavaCompositeElementType) JavaElementType.CONDITIONAL_EXPRESSION;
            Field myConstructor;
            myConstructor = BasicJavaElementType.JavaCompositeElementType.class.getDeclaredField("myConstructor");
            myConstructor.setAccessible(true);
            myConstructor.set(expression, (Supplier<? extends ASTNode>) () -> {
                return new ZrPsiConditionalExpressionImpl();
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            final Class<?> registryClass = Class.forName(
                    "com.intellij.psi.impl.java.stubs.JavaStubElementTypePsiElementMappingRegistry");
            final Method getInstance = registryClass.getDeclaredMethod("getInstance");
            final Object registry = getInstance.invoke(null);
            final Map<IElementType, Function<ASTNode, PsiElement>> factories =
                    registry.reflectionFieldValue("myFactories");
            factories.put(JavaStubElementTypes.LITERAL_EXPRESSION, node -> {
                ASTNode previous = node.getTreePrev();
                ASTNode next = node.getTreeNext();
                if (node instanceof CompositeElement
                        && node.getTextLength() == 0
                        && ((CompositeElement) node).getFirstChildNode() == null
                        && previous != null
                        && previous.getElementType() == JavaTokenType.QUEST
                        && next != null
                        && next.getElementType() == JavaTokenType.COLON
                        && previous.getStartOffset() + previous.getTextLength()
                        == next.getStartOffset()) {
                    // Preserve a truthful zero-width NULL token. The AST text,
                    // PSI text and all reported lengths remain empty.
                    ((CompositeElement) node).rawAddChildrenWithoutNotifications(
                            new PsiJavaTokenImpl(JavaTokenType.NULL_KEYWORD, ""));
                }
                return new PsiLiteralExpressionImpl(node);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ZrExpressionParser(@NotNull JavaParser javaParser) {
        super(javaParser);
        ReflectionUtil.setDeclaredField(this, BasicExpressionParser.class, "myOldExpressionParser", new ZrBasicOldExpressionParser(javaParser));

    }


}
