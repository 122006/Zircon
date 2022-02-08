package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.editorActions.JavaQuoteHandler;
import com.intellij.codeInsight.editorActions.QuoteHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.TextRange;
import com.sun.tools.javac.parser.Formatter;

import java.util.Objects;

public class ZrJavaQuoteHandler extends JavaQuoteHandler {
    @Override
    public boolean isInsideLiteral(HighlighterIterator iterator) {
        final boolean insideLiteral = super.isInsideLiteral(iterator);
        if (insideLiteral){
            if (iterator.getDocument()==null) return false;
            final String text = iterator.getDocument().getText();
            if (text.charAt(iterator.getStart())=='"'){
                final boolean anyMatch = Formatter.getAllFormatters().stream()
                        .anyMatch(a -> Objects.equals(text.substring(iterator.getStart()-a.prefix().length(),iterator.getStart()),a.prefix()));
                if (anyMatch) return false;
            }
        }
        return insideLiteral;
    }
    public boolean isInsideLiteral2(HighlighterIterator iterator) {
        final boolean insideLiteral = super.isInsideLiteral(iterator);
        if (insideLiteral){
            if (iterator.getDocument()==null) return false;
            final String text = iterator.getDocument().getText();
            if (text.charAt(iterator.getStart())=='"'){
                final boolean anyMatch = Formatter.getAllFormatters().stream()
                        .anyMatch(a -> Objects.equals(text.substring(iterator.getStart()-a.prefix().length(),iterator.getStart()),a.prefix()));
                if (anyMatch) return true;
            }
        }
        return false;
    }
}
