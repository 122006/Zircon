package com.by122006.zircon.ijplugin241;

import com.by122006.zircon.ijplugin.ZrPsiBinaryExpressionImpl;
import com.by122006.zircon.ijplugin.ZrPsiConditionalExpressionImpl;
import com.esotericsoftware.kryo.kryo5.minlog.Log;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.parser.BasicExpressionParser;
import com.intellij.lang.java.parser.BasicPrattExpressionParser;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.openapi.util.registry.Registry;
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
    private static final boolean useNewImplementation = Registry.is("pratt.java.expression.parser", true);

    {
        if (useNewImplementation)
            Log.warn("Zircon does not support pratt.java.expression.parser. " +
                    "If you are using IntelliJ IDEA version 2024 or below, please modify the IDE registry (search for 'Registry' in IDEA) and keep the corresponding item disabled (disabled by default); " +
                    "if you are using versions 2025.1-2025.2, some features may not be available, please update to 2025.3.");
        try {
            final JavaElementType.JavaCompositeElementType binaryExpression = (JavaElementType.JavaCompositeElementType) JavaElementType.BINARY_EXPRESSION;
            Field myConstructor = BasicJavaElementType.JavaCompositeElementType.class.getDeclaredField("myConstructor");
            myConstructor.setAccessible(true);
            myConstructor.set(binaryExpression, (Supplier<? extends ASTNode>) () -> {
                return new ZrPsiBinaryExpressionImpl();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            final JavaElementType.JavaCompositeElementType expression = (JavaElementType.JavaCompositeElementType) JavaElementType.CONDITIONAL_EXPRESSION;
            Field myConstructor = BasicJavaElementType.JavaCompositeElementType.class.getDeclaredField("myConstructor");
            myConstructor.setAccessible(true);
            myConstructor.set(expression, (Supplier<? extends ASTNode>) () -> {
                return new ZrPsiConditionalExpressionImpl();
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
