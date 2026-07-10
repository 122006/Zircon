package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class CompilationResultRecordAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static boolean onEnter(
            @Advice.This Object compilationResult,
            @Advice.Argument(0) Object problem
    ) {
        ZirconCore.logRecordedProblem(compilationResult, problem);
        return ZirconCore.shouldSuppressExplicitThisFunctionalDirectCallRecordedProblem(compilationResult, problem)
                || ZirconCore.shouldSuppressOptionalFunctionalRecordedProblem(compilationResult, problem)
                || ZirconCore.shouldSuppressSuccessfulFunctionalLocalFieldRecordedProblem(compilationResult, problem)
                || ZirconCore.shouldSuppressResolvableLocalReferenceRecordedProblem(compilationResult, problem)
                || ZirconCore.shouldSuppressSuccessfulClassTargetRecordedProblem(compilationResult, problem)
                || ZirconCore.shouldSuppressSuccessfulExtensionUndefinedRecordedProblem(compilationResult, problem)
                || ZirconCore.shouldSuppressSuccessfulExtensionFunctionalReturnRecordedProblem(compilationResult, problem)
                || ZirconCore.shouldSuppressSuccessfulFunctionalWrapperRecordedProblem(compilationResult, problem)
                || ZirconCore.shouldSuppressFalseReturnRecordedProblem(compilationResult, problem);
    }
}
