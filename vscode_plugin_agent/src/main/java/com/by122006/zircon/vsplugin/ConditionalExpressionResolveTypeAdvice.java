package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class ConditionalExpressionResolveTypeAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static Object onEnter(
            @Advice.This Object conditionalExpression,
            @Advice.Argument(0) Object currentScope
    ) {
        return ZirconCore.tryResolveConditionalExpressionType(conditionalExpression, currentScope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.This Object conditionalExpression,
            @Advice.Argument(0) Object currentScope,
            @Advice.Enter Object override,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result,
            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable thrown
    ) {
        if (override != null) {
            result = override;
            thrown = null;
            return;
        }
        if (thrown == null) {
            return;
        }
        Object recovered = ZirconCore.tryRecoverConditionalExpressionTypeOnFailure(conditionalExpression, currentScope, thrown);
        if (recovered != null) {
            result = recovered;
            thrown = null;
        }
    }
}
