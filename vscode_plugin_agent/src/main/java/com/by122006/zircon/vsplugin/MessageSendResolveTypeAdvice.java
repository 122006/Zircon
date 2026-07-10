package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class MessageSendResolveTypeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object onEnter(
            @Advice.This Object messageSend
    ) {
        return ZirconCore.captureMessageSendResolveTypeEntryState(messageSend);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Enter Object entryState,
            @Advice.This Object messageSend,
            @Advice.Argument(0) Object currentScope,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned,
            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable throwable
    ) {
        if (throwable != null) {
            Object recovered = ZirconCore.tryRecoverMessageSendResolveTypeOnExit(
                    messageSend,
                    currentScope,
                    throwable,
                    entryState
            );
            if (recovered != null) {
                returned = recovered;
                throwable = null;
                return;
            }
            ZirconCore.logMessageSendResolveTypeFailure(messageSend, currentScope, throwable);
        }
    }
}
