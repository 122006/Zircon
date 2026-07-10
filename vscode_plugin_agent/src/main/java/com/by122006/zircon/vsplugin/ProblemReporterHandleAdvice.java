package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class ProblemReporterHandleAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static boolean onEnter(
            @Advice.This Object problemReporter,
            @Advice.AllArguments Object[] arguments
    ) {
        ZirconCore.logProblemReport(problemReporter, arguments);
        return ZirconCore.shouldSuppressExplicitThisFunctionalDirectCallProblem(problemReporter, arguments)
                || ZirconCore.shouldSuppressOptionalFunctionalReportedProblem(problemReporter, arguments)
                || ZirconCore.shouldSuppressSuccessfulFunctionalLocalFieldReportedProblem(problemReporter, arguments)
                || ZirconCore.shouldSuppressResolvableLocalReferenceReportedProblem(problemReporter, arguments)
                || ZirconCore.shouldSuppressSuccessfulClassTargetReportedProblem(problemReporter, arguments)
                || ZirconCore.shouldSuppressSuccessfulFunctionalWrapperReportedProblem(problemReporter, arguments)
                || ZirconCore.shouldSuppressSuccessfulExtensionFunctionalReturnReportedProblem(problemReporter, arguments)
                || ZirconCore.shouldSuppressSuccessfulExtensionUndefinedReportedProblem(problemReporter, arguments);
    }
}
