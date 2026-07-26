package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class JdtSearchNodeAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Argument(0) Object node) {
        ZirconCore.normalizeJdtSearchNodeBinding(node);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
            @Advice.This Object locator,
            @Advice.Argument(0) Object node,
            @Advice.Return(readOnly = false) int level
    ) {
        level = ZirconCore.repairJdtSearchResolution(locator, node, level);
        ZirconCore.rememberAccurateJdtSearchReference(locator, node, level);
        ZirconCore.traceJdtSearchResolution(node, level);
    }
}
