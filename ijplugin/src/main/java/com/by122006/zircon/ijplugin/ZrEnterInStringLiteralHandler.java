package com.by122006.zircon.ijplugin;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.codeInsight.editorActions.JavaLikeQuoteHandler;
import com.intellij.codeInsight.editorActions.QuoteHandler;
import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.codeInsight.editorActions.enter.EnterInStringLiteralHandler;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.sun.tools.javac.parser.Formatter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ZrEnterInStringLiteralHandler extends EnterInStringLiteralHandler {
    public ZrEnterInStringLiteralHandler() {
    }

    public Result preprocessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull Ref<Integer> caretOffsetRef, @NotNull Ref<Integer> caretAdvanceRef, @NotNull DataContext dataContext, EditorActionHandler originalHandler) {

        Language language = EnterHandler.getLanguage(dataContext);
        if (language == null) {
            return Result.Continue;
        } else {
            int caretOffset = (Integer) caretOffsetRef.get();
            JavaLikeQuoteHandler quoteHandler = this.getJavaLikeQuoteHandler(editor, file);
            if (!isInStringLiteral(editor, quoteHandler, caretOffset)) {
                return Result.Continue;
            } else {
                PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
                PsiElement psiAtOffset = file.findElementAt(caretOffset);
                if (psiAtOffset != null && psiAtOffset.getTextOffset() < caretOffset) {
                    Document document = editor.getDocument();
                    if (quoteHandler.canBeConcatenated(psiAtOffset)) {
                        ASTNode token = psiAtOffset.getNode();
                        final Formatter formatter = Formatter.getAllFormatters().stream()
                                .filter(a -> token.getText().startsWith(a.prefix()))
                                .findFirst().orElse(null);
                        if (formatter==null) return Result.Continue;
                        if (quoteHandler.needParenthesesAroundConcatenation(psiAtOffset)) {
                            document.insertString(psiAtOffset.getTextRange().getEndOffset(), ")");
                            document.insertString(psiAtOffset.getTextRange().getStartOffset(), "(");
                            caretOffset++;
                        }
                        String insertedFragment = "\" " + quoteHandler.getStringConcatenationOperatorRepresentation();
                        document.insertString(caretOffset, insertedFragment + " " + formatter.prefix() + "\"" );
                        caretOffset += insertedFragment.length();
                        int caretAdvance = 1;
                        if (CodeStyle.getLanguageSettings(file).BINARY_OPERATION_SIGN_ON_NEXT_LINE) {
                            --caretOffset;
                            caretAdvance = 3;
                        }
                        caretOffsetRef.set(caretOffset);
                        caretAdvanceRef.set(caretAdvance);
                        return Result.DefaultForceIndent;
                    }
                }

                return Result.Continue;
            }
        }
    }

    @Contract( "_,null,_->false" )
    private static boolean isInStringLiteral(@NotNull Editor editor, @Nullable JavaLikeQuoteHandler quoteHandler, int offset) {
        if (offset > 0 && quoteHandler != null) {
            EditorHighlighter highlighter = ((EditorEx) editor).getHighlighter();
            HighlighterIterator iterator = highlighter.createIterator(offset - 1);
            IElementType type = iterator.getTokenType();
            if (quoteHandler instanceof ZrJavaQuoteHandler) {
                return true;
            }
        }
        return false;
    }

}