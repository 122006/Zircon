package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

/**
 * @ClassName: ZrTypedHandler
 * @Author: 122006
 * @Date: 2026/1/13 8:45
 * @Description:
 */
public class ZrTypedHandler extends TypedHandlerDelegate {
    @Override
    public @NotNull Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (!(file instanceof PsiJavaFile)) return Result.CONTINUE;
        if (charTyped == '.' && isOptionalChainDot(editor)) {
            AutoPopupController.getInstance(project).autoPopupMemberLookup(
                    editor,
                    psiFile -> psiFile instanceof PsiJavaFile
                            && isOptionalChainDot(editor));
            return Result.CONTINUE;
        }
        return super.checkAutoPopup(charTyped, project, editor, file);
    }

    private static boolean isOptionalChainDot(@NotNull Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        CharSequence chars =
                editor.getDocument().getImmutableCharSequence();
        if (offset <= 0 || offset > chars.length()) {
            return false;
        }
        // checkAutoPopup has run both before and after insertion in different
        // IDEA generations. Accept either "...?<caret>" with '.' pending or
        // "...?.<caret>" after the document has already been updated.
        if (chars.charAt(offset - 1) == '?') {
            return true;
        }
        return offset > 1
                && chars.charAt(offset - 2) == '?'
                && chars.charAt(offset - 1) == '.';
    }
}
