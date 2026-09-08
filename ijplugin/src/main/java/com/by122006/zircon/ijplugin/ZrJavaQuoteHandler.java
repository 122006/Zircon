package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.editorActions.JavaQuoteHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.ZrStringModel;

public class ZrJavaQuoteHandler extends JavaQuoteHandler {

    @Override
    public boolean isInsideLiteral(HighlighterIterator iterator) {
        boolean insideLiteral = super.isInsideLiteral(iterator);
        if (!insideLiteral) return false;
        // Preserve the historical behavior: the stock quote handler must not
        // insert/skip quotes inside a Zircon literal. The Enter handler uses
        // isInsideZrLiteral() directly when it needs the opposite answer.
        return !isInsideZrLiteral(iterator);
    }

    public boolean isInsideZrLiteral(HighlighterIterator iterator) {
        Document document = iterator.getDocument();
        if (document == null || iterator.atEnd()) return false;

        CharSequence chars = document.getImmutableCharSequence();
        int start = iterator.getStart();
        int end = iterator.getEnd();
        if (start < 0 || end <= start || end > chars.length()) return false;

        String tokenText = chars.subSequence(start, end).toString();
        Formatter formatter = Formatter.getAllFormatters().stream()
                .filter(candidate -> tokenText.startsWith(candidate.prefix() + "\""))
                .findFirst()
                .orElse(null);
        if (formatter == null) return false;

        try {
            ZrStringModel model = formatter.build(tokenText);
            int closingQuote = model.getEndQuoteIndex();
            return closingQuote >= formatter.prefix().length()
                    && closingQuote < tokenText.length();
        } catch (RuntimeException ignored) {
            // Incomplete text is expected while the user is typing.
            return true;
        }
    }
}
