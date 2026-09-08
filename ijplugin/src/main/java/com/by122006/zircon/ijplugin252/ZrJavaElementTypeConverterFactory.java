package com.by122006.zircon.ijplugin252;

import com.by122006.zircon.ijplugin.ZrPsiConditionalExpressionImpl;
import com.intellij.java.syntax.element.JavaSyntaxElementType;
import com.intellij.lang.ASTNode;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.psi.ElementTypeConverter;
import com.intellij.platform.syntax.psi.ElementTypeConverterFactory;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maps Java conditional syntax nodes to Zircon's PSI implementation through
 * the public Syntax API converter extension point.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ZrJavaElementTypeConverterFactory
        implements ElementTypeConverterFactory {
    private static final IElementType ZR_CONDITIONAL_EXPRESSION =
            new ZrConditionalElementType();

    private static final ElementTypeConverter CONVERTER =
            new ElementTypeConverter() {
                @Override
                public @Nullable SyntaxElementType convert(
                        @NotNull IElementType elementType) {
                    return elementType == ZR_CONDITIONAL_EXPRESSION
                            ? JavaSyntaxElementType.CONDITIONAL_EXPRESSION
                            : null;
                }

                @Override
                public @Nullable IElementType convert(
                        @NotNull SyntaxElementType elementType) {
                    return elementType == JavaSyntaxElementType.CONDITIONAL_EXPRESSION
                            ? ZR_CONDITIONAL_EXPRESSION
                            : null;
                }

                @Override
                public IElementType @NotNull [] convert(
                        SyntaxElementType @NotNull [] elementTypes) {
                    IElementType[] result = new IElementType[elementTypes.length];
                    for (int index = 0; index < elementTypes.length; index++) {
                        result[index] = convert(elementTypes[index]);
                    }
                    return result;
                }
            };

    @Override
    public @NotNull ElementTypeConverter getElementTypeConverter() {
        return CONVERTER;
    }

    private static final class ZrConditionalElementType
            extends IJavaElementType implements ICompositeElementType {
        private ZrConditionalElementType() {
            super("ZR_CONDITIONAL_EXPRESSION_BRIDGE");
        }

        @Override
        public @NotNull ASTNode createCompositeNode() {
            return new ZrPsiConditionalExpressionImpl();
        }
    }
}
