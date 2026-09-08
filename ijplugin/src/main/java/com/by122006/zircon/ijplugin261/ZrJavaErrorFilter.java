package com.by122006.zircon.ijplugin261;

import com.by122006.zircon.ijplugin.ZrPsiConditionalExpressionImpl;
import com.by122006.zircon.ijplugin.util.ZrPluginUtil;
import com.intellij.java.codeserver.highlighting.JavaErrorFilter;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Suppresses the single Java error introduced by the standard-AST encoding of
 * Zircon's {@code left ?: right}: Java sees {@code left ? null : right} and
 * therefore expects {@code left} to be boolean.
 */
public final class ZrJavaErrorFilter implements JavaErrorFilter {

    @Override
    public boolean shouldSuppressError(@NotNull PsiFile file,
                                       @NotNull JavaCompilationError<?, ?> error) {
        return ZrPluginUtil.hasZrPlugin(file) && isElvisConditionError(error);
    }

    static boolean isElvisConditionError(@NotNull JavaCompilationError<?, ?> error) {
        if (error.kind() != JavaErrorKinds.TYPE_INCOMPATIBLE
                || !(error.context() instanceof JavaIncompatibleTypeErrorContext)) {
            return false;
        }

        JavaIncompatibleTypeErrorContext context =
                (JavaIncompatibleTypeErrorContext) error.context();
        if (!PsiTypes.booleanType().equals(context.lType())) {
            return false;
        }

        PsiElement problem = error.psi();
        for (PsiElement current = problem; current != null; current = current.getParent()) {
            if (current instanceof ZrPsiConditionalExpressionImpl) {
                ZrPsiConditionalExpressionImpl expression =
                        (ZrPsiConditionalExpressionImpl) current;
                if (expression.isElvisExpression()
                        && PsiTreeUtil.isAncestor(
                        expression.getThenExpression(), problem, false)) {
                    return true;
                }
            }
        }
        return false;
    }
}
