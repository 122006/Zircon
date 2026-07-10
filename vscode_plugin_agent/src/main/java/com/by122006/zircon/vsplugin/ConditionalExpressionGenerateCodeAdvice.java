package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class ConditionalExpressionGenerateCodeAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static boolean onEnter(
            @Advice.This Object conditionalExpression,
            @Advice.Argument(0) Object currentScope,
            @Advice.Argument(1) Object codeStream,
            @Advice.Argument(2) boolean valueRequired
    ) {
        ZirconCore.prepareConditionalExpression(conditionalExpression, currentScope);
        return ZirconCore.tryGenerateConditionalExpressionCode(conditionalExpression, currentScope, codeStream, valueRequired);
    }
}
