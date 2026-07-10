package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class LambdaExpressionAnalyseCodeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
            @Advice.This Object lambdaExpression,
            @Advice.Argument(0) Object currentScope
    ) {
        ZirconCore.prepareLambdaAnalyseCode(lambdaExpression, currentScope);
    }
}
