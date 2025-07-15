package com.sun.tools.javac.util;


/**
 * @ClassName: CommonUtil
 * @Author: zwh
 * @Date: 2025/7/14 18:01
 * @Description:
 */
public class CommonUtil {
    public static void logError(Log log, JCDiagnostic.DiagnosticPosition pos, String str) {
        log.error(pos, "proc.messager",str);
    }
}
