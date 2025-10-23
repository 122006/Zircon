package com.by122006.zircon.ijplugin252;

import com.by122006.zircon.ijplugin.ZrPsiBinaryExpressionImpl;
import com.by122006.zircon.ijplugin.ZrPsiConditionalExpressionImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.parser.BasicExpressionParser;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.sun.tools.javac.parser.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExReflection;
import zircon.example.ExString;

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
            final JavaElementType.JavaCompositeElementType binaryExpression = (JavaElementType.JavaCompositeElementType) JavaElementType.BINARY_EXPRESSION;
            Field myConstructor;
            myConstructor = BasicJavaElementType.JavaCompositeElementType.class.getDeclaredField("myConstructor");
            myConstructor.setAccessible(true);
            myConstructor.set(binaryExpression, (Supplier<? extends ASTNode>) () -> {
                return new ZrPsiBinaryExpressionImpl();
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

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
            final Class<?> aClass = Class.forName("com.intellij.psi.impl.java.stubs.JavaStubElementTypePsiElementMappingRegistry");
            final Method getInstance = aClass.getDeclaredMethod("getInstance");
            final Object invoke = getInstance.invoke(null);
            final Map<IElementType, Function<ASTNode, PsiElement>> myFactories = invoke.reflectionFieldValue("myFactories");
            myFactories.put(JavaStubElementTypes.LITERAL_EXPRESSION, node -> {
                if (node.getText().isEmpty()) {
                    if (node instanceof CompositeElement) {

                        final PsiJavaTokenImpl first = new PsiJavaTokenImpl(JavaStubElementTypes.LITERAL_EXPRESSION, "null") {
                            @Override
                            public int copyTo(char @Nullable [] buffer, int start) {
                                return start;
                            }

                            @Override
                            public PsiFile getContainingFile() {
                                return ((CompositeElement) node).getPsi().getContainingFile();
                            }

                            @Override
                            public @NotNull String getText() {
                                return "null";
                            }

                            @Override
                            public boolean isPhysical() {
                                return true;
                            }

                            @Override
                            public int getTextLength() {
                                return 0;
                            }

                            @Override
                            public int getCachedLength() {
                                return 0;
                            }

                            @Override
                            public char @NotNull [] textToCharArray() {
                                return "null".toCharArray();
                            }
                        };
                        if (((CompositeElement) node).getFirstChildNode() == null)
                            ((CompositeElement) node).rawAddChildrenWithoutNotifications(first);
                        return new PsiLiteralExpressionImpl(node) {

                            @Override
                            public boolean isPhysical() {
                                return true;
                            }

                            @Override
                            public @NotNull ASTNode getNode() {
                                final ASTNode node1 = super.getNode();
                                if (((CompositeElement) node1).getFirstChildNode() == null)
                                    ((CompositeElement) node1).rawAddChildrenWithoutNotifications(first);
                                return node1;
                            }
                        };
                    }

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
