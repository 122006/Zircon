package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class MessageSendAnalyseCodeAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static Object onEnter(
            @Advice.This Object messageSend,
            @Advice.Argument(0) Object currentScope,
            @Advice.Argument(1) Object flowContext,
            @Advice.Argument(2) Object flowInfo
    ) {
        return ZirconCore.tryShortCircuitMessageSendAnalyseCode(messageSend, currentScope, flowContext, flowInfo);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Enter Object earlyReturn,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned,
            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable thrown
    ) {
        if (earlyReturn != null) {
            returned = earlyReturn;
            thrown = null;
        }
    }
}
