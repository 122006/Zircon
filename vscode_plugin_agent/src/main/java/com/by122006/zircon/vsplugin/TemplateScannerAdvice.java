package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class TemplateScannerAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Object enter(@Advice.This Object scanner) {
        return TemplateStringSupport.beforeGetNextToken(scanner);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(
            @Advice.Enter Object override,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result,
            @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable thrown
    ) {
        if (override == null) {
            return;
        }
        result = override;
        thrown = null;
    }
}
