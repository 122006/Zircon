package com.by122006.zircon.ijplugin241;

import com.by122006.zircon.ijplugin.ZrPsiBinaryExpressionImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.parser.BasicExpressionParser;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.sun.tools.javac.parser.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.function.Supplier;

/**
 * @ClassName: ZrExpressionParser
 * @Author: 122006
 * @Date: 2025/7/2 22:45
 * @Description:
 */
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
    }

    public ZrExpressionParser(@NotNull JavaParser javaParser) {
        super(javaParser);
        ReflectionUtil.setDeclaredField(this, BasicExpressionParser.class, "myOldExpressionParser", new ZrBasicOldExpressionParser(javaParser));

    }
}
