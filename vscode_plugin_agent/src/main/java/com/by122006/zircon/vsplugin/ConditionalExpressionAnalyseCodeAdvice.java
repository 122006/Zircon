package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class ConditionalExpressionAnalyseCodeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
            @Advice.This Object conditionalExpression,
            @Advice.Argument(0) Object currentScope
    ) {
        ZirconCore.prepareConditionalExpression(conditionalExpression, currentScope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.This Object conditionalExpression,
            @Advice.Argument(0) Object currentScope,
            @Advice.Argument(1) Object flowContext,
            @Advice.Argument(2) Object flowInfo,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned,
            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable thrown
    ) {
        Object recovered = ZirconCore.tryRecoverConditionalExpressionAnalyseCode(
                conditionalExpression,
                currentScope,
                flowContext,
                flowInfo,
                thrown
        );
        if (recovered != null) {
            returned = recovered;
            thrown = null;
        }
    }
}
