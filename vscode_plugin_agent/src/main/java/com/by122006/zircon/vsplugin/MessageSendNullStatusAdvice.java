package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class MessageSendNullStatusAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static Integer onEnter(@Advice.This Object messageSend) {
        return ZirconCore.tryShortCircuitMessageSendNullStatus(messageSend);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Enter Integer earlyReturn,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned,
            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable thrown
    ) {
        if (earlyReturn != null) {
            returned = earlyReturn;
            thrown = null;
        }
    }
}
