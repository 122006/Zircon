package com.sun.tools.javac.parser;

import com.sun.tools.javac.tree.JCTree;

public class ZrUnSupportCodeError extends RuntimeException {
    public ZrUnSupportCodeError(String message) {
        super(message );
        setStackTrace(new StackTraceElement[0]);
    }
    public ZrUnSupportCodeError(String message, JCTree tree) {
        super(message + ":\n" + tree.toString());
        setStackTrace(new StackTraceElement[0]);
    }

    public ZrUnSupportCodeError(String message, JCTree tree, String detail) {
        super(message + ":\n" + tree.toString() + (detail == null ? "" : detail));
        setStackTrace(new StackTraceElement[0]);
    }
}
