package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.reflect.Method;

public class TemplateScannerAccessorAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Object enter(
            @Advice.This Object scanner,
            @Advice.Origin Method origin
    ) {
        return TemplateStringSupport.beforeAccessor(scanner, origin.getName());
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
