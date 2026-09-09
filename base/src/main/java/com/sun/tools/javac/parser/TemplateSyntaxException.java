package com.sun.tools.javac.parser;

/** Only known template syntax failures use this exception; internal causes remain visible. */
public final class TemplateSyntaxException extends RuntimeException {
    public final TemplateStringSplitter.Diagnostic diagnostic;

    public TemplateSyntaxException(TemplateStringSplitter.Diagnostic diagnostic) {
        super(diagnostic.displayMessage());
        this.diagnostic = diagnostic;
    }
}
