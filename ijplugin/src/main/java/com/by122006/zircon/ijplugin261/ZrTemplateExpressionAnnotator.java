package com.by122006.zircon.ijplugin261;

import com.by122006.zircon.ijplugin.util.ZrPluginUtil;
import com.by122006.zircon.ijplugin.util.ZrUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.StringRange;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Performs safe, PSI-based checks for Java expressions embedded in Zircon strings.
 *
 * <p>This intentionally does not instantiate or proxy IDEA's internal
 * {@code HighlightVisitorImpl}. That visitor owns per-pass state and cannot be
 * reused for detached expressions without corrupting highlighting/indexing.</p>
 */
public final class ZrTemplateExpressionAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiLiteralExpression)) return;

        PsiLiteralExpression literal = (PsiLiteralExpression) element;
        Formatter formatter = ZrUtil.checkPsiLiteralExpression(literal);
        if (formatter == null || !ZrPluginUtil.hasZrPlugin(literal)) return;

        final ZrStringModel model;
        try {
            model = formatter.build(literal.getText());
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "[ZrString]: invalid template string: " + safeMessage(e))
                    .range(literal)
                    .create();
            return;
        }

        for (StringRange range : model.getList()) {
            if (range.codeStyle != 1) continue;
            validateExpression(literal, range, holder);
        }
    }

    private static void validateExpression(PsiLiteralExpression host,
                                           StringRange range,
                                           AnnotationHolder holder) {
        String hostText = host.getText();
        if (range.startIndex < 0
                || range.endIndex < range.startIndex
                || range.endIndex > hostText.length()) {
            return;
        }

        Formatter formatter = ZrUtil.checkPsiLiteralExpression(host);
        if (formatter == null) return;
        Formatter.CodeTransferResult transfer = formatter.codeTransferWithOffsets(
                hostText.substring(range.startIndex, range.endIndex));
        String source = transfer.getText();
        String expressionText = source.trim();
        int leadingWhitespace = source.indexOf(expressionText);
        if (leadingWhitespace < 0) leadingWhitespace = 0;

        if (expressionText.isEmpty()) {
            return;
        }

        final PsiExpression expression;
        try {
            expression = JavaPsiFacade.getElementFactory(host.getProject())
                    .createExpressionFromText(expressionText, host.getParent());
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            addProblem(host, range.startIndex, range.endIndex,
                    "[ZrString]: invalid Java expression: " + safeMessage(e), holder);
            return;
        }

        int decodedExpressionStart = leadingWhitespace;
        int detachedExpressionStart = expression.getTextRange().getStartOffset();
        int detachedExpressionEnd = expression.getTextRange().getEndOffset();
        Set<String> reported = new HashSet<>();
        JavaErrorCollector collector = new JavaErrorCollector(
                host.getContainingFile(),
                error -> reportCompilationError(
                        host,
                        range,
                        transfer,
                        decodedExpressionStart,
                        detachedExpressionStart,
                        detachedExpressionEnd,
                        error,
                        reported,
                        holder));
        expression.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                collector.processElement(element);
                super.visitElement(element);
            }
        });
    }

    private static void reportCompilationError(
            PsiLiteralExpression host,
            StringRange range,
            Formatter.CodeTransferResult transfer,
            int decodedExpressionStart,
            int detachedExpressionStart,
            int detachedExpressionEnd,
            JavaCompilationError<?, ?> error,
            Set<String> reported,
            AnnotationHolder holder) {
        if (ZrJavaErrorFilter.isElvisConditionError(error)) return;
        TextRange errorRange = error.range();
        if (errorRange.getStartOffset() < detachedExpressionStart
                || errorRange.getEndOffset() > detachedExpressionEnd) {
            return;
        }
        TextRange local = errorRange.shiftLeft(detachedExpressionStart);
        reportDecodedProblem(
                host,
                range,
                transfer,
                decodedExpressionStart + local.getStartOffset(),
                decodedExpressionStart + local.getEndOffset(),
                error.description(),
                reported,
                holder);
    }

    private static void reportDecodedProblem(
            PsiLiteralExpression host,
            StringRange range,
            Formatter.CodeTransferResult transfer,
            int decodedStart,
            int decodedEnd,
            String message,
            Set<String> reported,
            AnnotationHolder holder) {
        int transferredLength = transfer.getText().length();
        int safeStart = Math.max(0, Math.min(transferredLength, decodedStart));
        int safeEnd = Math.max(safeStart, Math.min(transferredLength, decodedEnd));
        int rawStart;
        int rawEnd;
        if (safeStart == safeEnd) {
            rawStart = transfer.rawBoundaryOffset(safeStart);
            rawEnd = rawStart;
        } else {
            rawStart = transfer.rawStartOffset(safeStart);
            rawEnd = transfer.rawEndOffset(safeEnd);
        }
        String safeMessage = message == null || message.isBlank()
                ? "invalid Java expression"
                : message;
        String key = rawStart + ":" + rawEnd + "\u0000" + safeMessage;
        if (!reported.add(key)) return;
        addProblem(
                host,
                range.startIndex + rawStart,
                range.startIndex + rawEnd,
                "[ZrString]: " + safeMessage,
                holder);
    }

    private static void addProblem(PsiLiteralExpression host,
                                   int relativeStart,
                                   int relativeEnd,
                                   String message,
                                   AnnotationHolder holder) {
        TextRange hostRange = host.getTextRange();
        int start = Math.max(hostRange.getStartOffset(),
                Math.min(hostRange.getEndOffset(), hostRange.getStartOffset() + relativeStart));
        int end = Math.max(start,
                Math.min(hostRange.getEndOffset(), hostRange.getStartOffset() + relativeEnd));
        if (start == end && end < hostRange.getEndOffset()) {
            end++;
        }
        holder.newAnnotation(HighlightSeverity.ERROR, message)
                .range(TextRange.create(start, end))
                .create();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }
}
