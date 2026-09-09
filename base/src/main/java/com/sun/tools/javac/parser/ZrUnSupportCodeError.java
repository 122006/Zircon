package com.sun.tools.javac.parser;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.Log;

public class ZrUnSupportCodeError extends RuntimeException {
    public ZrUnSupportCodeError(String message) {
        super(describe(message, null, null));
    }
    public ZrUnSupportCodeError(String message, Context context, JCTree tree) {
        super(describe(message, context, tree));
    }

    public ZrUnSupportCodeError(String message, JCTree tree, String detail) {
        super(describe(message + (detail == null ? "" : "\n" + detail), null, tree));
    }

    public ZrUnSupportCodeError(String message, Context context, JCTree tree, Throwable cause) {
        super(describe(message, context, tree), cause);
    }

    private static String describe(String message, Context context, JCTree tree) {
        StringBuilder result = new StringBuilder(message)
                .append("\nJDK=").append(System.getProperty("java.version"));
        if (context != null && tree != null) {
            DiagnosticSource source = Log.instance(context).currentSource();
            int position = tree.getStartPosition();
            if (source != null && source.getFile() != null && position >= 0) {
                result.append("，位置=").append(source.getFile().getName())
                        .append(':').append(source.getLineNumber(position))
                        .append(':').append(source.getColumnNumber(position, false));
            }
        }
        if (tree != null) result.append("\n表达式：").append(tree);
        return result.toString();
    }
}
