package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class CompletionEngineFindMethodsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
            @Advice.This Object completionEngine,
            @Advice.AllArguments Object[] arguments
    ) {
        ZirconCore.contributeExtensionMethodCompletions(completionEngine, arguments);
    }
}
