package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class ProblemReporterStaticAccessAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static boolean onEnter(@Advice.Argument(1) Object methodBinding) {
        return ZirconCore.shouldSuppressExMethodStaticAccessWarning(methodBinding);
    }
}
