package com.by122006.zircon.ijplugin.v252;

import com.intellij.java.syntax.parser.ExpressionParser;
import com.intellij.java.syntax.parser.JavaParser;
import com.intellij.java.syntax.parser.PrattExpressionParser;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @ClassName: ZrJavaParser252
 * @Author: 122006
 * @Date: 2025/10/10 16:06
 * @Description:
 */
public class ZrJavaParser252 extends JavaParser {
    public ZrJavaParser252(@NotNull LanguageLevel languageLevel) {
        super(languageLevel);
        final ExpressionParser expressionParser1 = getExpressionParser();
        final PrattExpressionParser myNewExpressionParser = expressionParser1.getStaticFieldValue("myNewExpressionParser");
        final Map<SyntaxElementType, ?> ourInfixParsers = myNewExpressionParser.getStaticFieldValue("ourInfixParsers");
//        ourInfixParsers.put(ZrJavaSyntaxTokenType.ELVIS,);
    }

}
