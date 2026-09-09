package com.sun.tools.javac.util;

import com.sun.tools.javac.parser.TemplateStringSplitter;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;

/** Uses the diagnostic factory ABI shared by javac 8, 11 and 17. */
public final class ZrDiagnosticReporter {
    private ZrDiagnosticReporter() {}

    public static void report(Log log, int templateStart, TemplateStringSplitter.Diagnostic diagnostic) {
        log.report(log.diags.create(JCDiagnostic.DiagnosticType.ERROR, log.currentSource(),
                new Span(templateStart + diagnostic.start, templateStart + diagnostic.end),
                "proc.messager", diagnostic.displayMessage()));
    }

    public static RuntimeException internalFailure(Log log, int position, String template, String phase, Throwable cause) {
        DiagnosticSource source = log.currentSource();
        String location = source == null || source.getFile() == null ? "unknown source"
                : source.getFile().getName() + ":" + source.getLineNumber(position)
                    + ":" + source.getColumnNumber(position, false);
        String excerpt = template == null ? "" : template.replace('\n', ' ').replace('\r', ' ');
        if (excerpt.length() > 200) excerpt = excerpt.substring(0, 200) + "...";
        return new IllegalStateException("[ZR9001] Zircon 内部错误，阶段=" + phase + "，位置=" + location
                + "，JDK=" + System.getProperty("java.version") + "\n原始表达式：" + excerpt, cause);
    }

    public static final class Span implements JCDiagnostic.DiagnosticPosition {
        private final int start;
        private final int end;

        public Span(int start, int end) { this.start = start; this.end = end; }
        @Override public JCTree getTree() { return null; }
        @Override public int getStartPosition() { return start; }
        @Override public int getPreferredPosition() { return start; }
        @Override public int getEndPosition(EndPosTable endPosTable) { return end; }
    }
}
