package com.by122006.zircon.vsplugin;

import net.bytebuddy.asm.Advice;

public class TemplateScannerResetAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.This Object scanner) {
        TemplateStringSupport.clearState(scanner);
    }
}
