package com.by122006.zircon.ijplugin223;

import com.by122006.zircon.ijplugin.ZrJavaTokenType;
import com.by122006.zircon.ijplugin.ZrPsiBinaryExpressionImpl;
import com.by122006.zircon.ijplugin.ZrPsiConditionalExpressionImpl;
import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Supplier;

public class ZrExpressionParser extends ExpressionParser {
    IElementType zrConditionalExpressionType;

    {
        try {
            Constructor<?> constructor = JavaElementType.JavaCompositeElementType.class.getDeclaredConstructor(String.class, Supplier.class);
            constructor.setAccessible(true);
            zrConditionalExpressionType = (IElementType) constructor.newInstance("ZR_CONDITIONAL_EXPRESSION", (Supplier<? extends ASTNode>) () -> {
                return new ZrPsiConditionalExpressionImpl();
            });
            final JavaElementType.JavaCompositeElementType binaryExpression = (JavaElementType.JavaCompositeElementType) JavaElementType.BINARY_EXPRESSION;
            Field myConstructor = JavaElementType.JavaCompositeElementType.class.getDeclaredField("myConstructor");
            myConstructor.setAccessible(true);
            myConstructor.set(binaryExpression, (Supplier<? extends ASTNode>) () -> {
                return new ZrPsiBinaryExpressionImpl();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ZrExpressionParser(@NotNull JavaParser javaParser) {
        super(javaParser);
    }

    @Override
    public PsiBuilder.Marker parseConditional(PsiBuilder builder, int mode) {
        final PsiBuilder.Marker condition = super.parseConditional(builder, mode);
        if (condition != null && builder.getTokenType() == ZrJavaTokenType.ELVIS) {
            PsiBuilder.Marker ternary = condition.precede();
            builder.advanceLexer();
            PsiBuilder.Marker falsePart = super.parseConditional(builder, mode);
            if (falsePart == null) {
                JavaParserUtil.error(builder, JavaPsiBundle.message("expected.expression", new Object[0]));
                ternary.done(zrConditionalExpressionType);
                return ternary;
            } else {
                ternary.done(zrConditionalExpressionType);
                return ternary;
            }
        }
        return condition;
    }
}

