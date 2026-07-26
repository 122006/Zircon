package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

/** Keeps ImportRewrite's import edit but removes Zircon's synthetic type edit. */
public class JdtCompletionReplacementAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
            @Advice.Argument(0) Object proposal,
            @Advice.Argument(1) Object completionItem
    ) {
        ZirconCore.sanitizeJdtExtensionCompletionEdits(proposal, completionItem);
    }
}
