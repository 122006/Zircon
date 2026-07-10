package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class MessageSendGenerateCodeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
            @Advice.This Object messageSend,
            @Advice.Argument(0) Object currentScope
    ) {
        ZirconCore.prepareMessageSendGenerateCode(messageSend, currentScope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.This Object messageSend,
            @Advice.Argument(2) boolean valueRequired,
            @Advice.Thrown Throwable throwable
    ) {
        if (throwable != null) {
            ZirconCore.logMessageSendGenerateCodeFailure(messageSend, valueRequired, throwable);
        }
    }
}
