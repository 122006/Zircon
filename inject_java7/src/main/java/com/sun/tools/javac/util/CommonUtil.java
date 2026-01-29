package com.sun.tools.javac.util;


import com.sun.tools.javac.code.Symbol;

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

    public static boolean isPrimitiveValue(Symbol.MethodSymbol msym) {
        if (!msym.getQualifiedName().toString().endsWith("Value")) return false;
        final Name qualifiedName = msym.getEnclosingElement().getQualifiedName();
        if (!msym.getParameters().isEmpty()) {
            return false;
        }
        if (qualifiedName.contentEquals("java.lang.Integer") || qualifiedName.contentEquals("java.lang.Long")
                || qualifiedName.contentEquals("java.lang.Short") || qualifiedName.contentEquals("java.lang.Byte")
                || qualifiedName.contentEquals("java.lang.Character") || qualifiedName.contentEquals("java.lang.Boolean")
                || qualifiedName.contentEquals("java.lang.Float") || qualifiedName.contentEquals("java.lang.Double")) {
            return true;
        }
        return false;

    }
}
