package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class LambdaExpressionGenerateCodeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
            @Advice.This Object lambdaExpression,
            @Advice.Argument(0) Object currentScope
    ) {
        ZirconCore.prepareLambdaGenerateCode(lambdaExpression, currentScope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.This Object lambdaExpression,
            @Advice.Thrown Throwable throwable
    ) {
        if (throwable != null) {
            ZirconCore.logLambdaGenerateCodeFailure(lambdaExpression, throwable);
        }
    }
}
