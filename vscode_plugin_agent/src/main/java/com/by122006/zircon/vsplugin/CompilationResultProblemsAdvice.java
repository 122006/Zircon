package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class CompilationResultProblemsAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
            @Advice.This Object compilationResult,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object problems
    ) {
        problems = ZirconCore.filterResolvedExtensionProblems(compilationResult, problems);
    }
}
