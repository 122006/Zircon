package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class JdtSearchBindingAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
            @Advice.Argument(value = 0, readOnly = false, typing = Assigner.Typing.DYNAMIC) Object binding
    ) {
        Object normalized = ZirconCore.normalizeJdtSearchBinding(binding);
        if (normalized != null) {
            binding = normalized;
        }
    }
}
