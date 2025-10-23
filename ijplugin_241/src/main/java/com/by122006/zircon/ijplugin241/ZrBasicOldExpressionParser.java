package com.by122006.zircon.ijplugin241;

import com.by122006.zircon.ijplugin.ZrJavaTokenType;
import com.by122006.zircon.ijplugin.ZrPsiConditionalExpressionImpl;
import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.BasicJavaParser;
import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.lang.java.parser.BasicOldExpressionParser;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @ClassName: ZrBasicOldExpressionParser
 * @Author: 122006
 * @Date: 2025/7/2 22:45
 * @Description:
 */
public class ZrBasicOldExpressionParser extends BasicOldExpressionParser {
    static {
        ZrExpressionParser.replaceJavaDummyElementType();
    }
    final IElementType zrConditionalExpressionType = new JavaElementType.JavaCompositeElementType("ZR_CONDITIONAL_EXPRESSION", () -> {
        return new ZrPsiConditionalExpressionImpl();
    }, BasicJavaElementType.BASIC_CONDITIONAL_EXPRESSION);

    public ZrBasicOldExpressionParser(@NotNull BasicJavaParser javaParser) {
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
                BasicJavaParserUtil.error(builder, JavaPsiBundle.message("expected.expression", new Object[0]));
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
