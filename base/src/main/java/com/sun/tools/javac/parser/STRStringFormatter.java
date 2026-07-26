package com.sun.tools.javac.parser;

public class STRStringFormatter extends SStringFormatter {
    @Override
    public String prefix() {
        return "STR." ;
    }

    @Override
    public ZrStringModel build(String text) {
        return Formatter.buildFromSharedSplitter(this, text, TemplateStringSplitter.Syntax.STR);
    }

    @Override
    public String stringTransfer(String str) {
        return str;
    }
}
