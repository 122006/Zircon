package com.sun.tools.javac.parser;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

public class ZrUnSupportCodeError extends RuntimeException {
    public ZrUnSupportCodeError(String message) {
        super(message );
        setStackTrace(new StackTraceElement[0]);
    }
    public ZrUnSupportCodeError(String message, Context context, JCTree tree) {
        super(message + ":\n" + tree.toString());
        setStackTrace(new StackTraceElement[0]);
    }

    public ZrUnSupportCodeError(String message, JCTree tree, String detail) {
        super(message + ":\n" + tree.toString() + (detail == null ? "" : detail));
        setStackTrace(new StackTraceElement[0]);
    }
}
