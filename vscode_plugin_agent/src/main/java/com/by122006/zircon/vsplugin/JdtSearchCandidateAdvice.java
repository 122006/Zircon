package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

/**
 * Lets JDT resolve extension-style calls whose source argument count is one less
 * than the static {@code @ExMethod} declaration. The normal MethodLocator
 * resolution phase still decides whether the candidate is a real match.
 */
public class JdtSearchCandidateAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
            @Advice.This Object locator,
            @Advice.Argument(0) Object node,
            @Advice.Argument(1) Object nodeSet,
            @Advice.Return(readOnly = false) int result
    ) {
        result = ZirconCore.expandJdtSearchCandidate(locator, node, nodeSet, result);
    }
}
