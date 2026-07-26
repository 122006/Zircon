package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

/**
 * A repaired extension method reference can still reach MethodLocator's report
 * phase with SearchMatch.A_INACCURATE even though its binding has already been
 * normalized to the focused @ExMethod declaration. JDT LS intentionally drops
 * inaccurate matches, so promote only that exact declaration match.
 */
public class JdtSearchReferenceAccuracyAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
            @Advice.This Object locator,
            @Advice.Argument(0) Object node,
            @Advice.Argument(value = 5, readOnly = false) int accuracy
    ) {
        accuracy = ZirconCore.repairJdtSearchReportAccuracy(locator, node, accuracy);
    }
}
