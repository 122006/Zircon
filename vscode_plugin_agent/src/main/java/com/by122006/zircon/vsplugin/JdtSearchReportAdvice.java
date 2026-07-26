package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

/**
 * JDT's MethodLocator uses MessageSend.sourceEnd as a method-reference match
 * length. Zircon's repaired extension search node can retain a wider sourceEnd,
 * so narrow it to nameSourcePosition while the match is being reported.
 */
public class JdtSearchReportAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long enter(
            @Advice.This Object locator,
            @Advice.Argument(0) Object messageSend
    ) {
        return ZirconCore.prepareJdtSearchReportRange(locator, messageSend);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
            @Advice.Argument(0) Object messageSend,
            @Advice.Enter long originalRange
    ) {
        ZirconCore.restoreJdtSearchReportRange(messageSend, originalRange);
    }
}
