package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

/** Adds the extension-holder import before JDT LS turns the proposal into LSP edits. */
public class JdtCompletionProposalRequestorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Argument(0) Object proposal) {
        ZirconCore.attachJdtExtensionCompletionImport(proposal);
    }
}
