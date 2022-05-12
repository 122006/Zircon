package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.editorActions.JavaQuoteHandler;
import com.intellij.codeInsight.editorActions.QuoteHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.TextRange;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.ZrStringModel;

import java.util.Objects;

public class ZrJavaQuoteHandler extends JavaQuoteHandler {
    @Override
    public boolean isInsideLiteral(HighlighterIterator iterator) {
        final boolean insideLiteral = super.isInsideLiteral(iterator);
        if (insideLiteral) {
            if (iterator.getDocument() == null) return true;
            final String text = iterator.getDocument().getText();
            int checkStart = iterator.getStart();
            int checkEndIndex = iterator.getStart();
            final CharSequence charsSequence = iterator.getDocument().getCharsSequence();
            while (charsSequence.charAt(checkStart) != '\n' && checkStart < charsSequence.length()) checkStart--;
            if (checkStart >= charsSequence.length()) return true;
            checkEndIndex = checkStart+1;
            while (charsSequence.charAt(checkEndIndex) != '\n' && checkEndIndex < charsSequence.length())
                checkEndIndex++;
            while (checkStart < checkEndIndex) {
                while (charsSequence.charAt(checkStart) != '"' && checkStart < checkEndIndex) checkStart++;
                if (checkStart == checkEndIndex) return true;
                int finalCheckStart = checkStart;
                final Formatter formatter = Formatter.getAllFormatters().stream()
                        .filter(a -> Objects.equals(text.substring(finalCheckStart - a.prefix().length(), finalCheckStart), a.prefix()))
                        .findFirst().orElse(null);
                if (formatter == null) return true;
                final ZrStringModel build = formatter.build(charsSequence.subSequence(checkStart, checkEndIndex).toString());
                if (build.getEndQuoteIndex()+1+checkStart >= iterator.getEnd()) return false;
                checkStart=build.getEndQuoteIndex()+1+checkStart;
            }
            return true;
        }
        return insideLiteral;
    }

}
