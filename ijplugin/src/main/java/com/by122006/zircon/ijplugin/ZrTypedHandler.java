package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.JavaTypedHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExObject;

/**
 * @ClassName: ZrTypedHandler
 * @Author: 122006
 * @Date: 2026/1/13 8:45
 * @Description:
 */
public class ZrTypedHandler extends JavaTypedHandlerBase {
    @Override
    public @NotNull Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (!(file instanceof PsiJavaFile)) return Result.CONTINUE;
        int offset = editor.getCaretModel().getOffset();
        if (charTyped == '.' && editor.getDocument().getImmutableCharSequence().charAt(offset - 1) == '?') {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor, f -> true);
            return Result.CONTINUE;
        }
        return super.checkAutoPopup(charTyped, project, editor, file);
    }

    @Override
    protected void autoPopupMemberLookup(@NotNull Project project, @NotNull Editor editor) {
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor, (file) -> {
            if (!(file instanceof PsiJavaFile)) {
                return false;
            }
            int offset = editor.getCaretModel().getOffset();
            PsiElement lastElement = file.findElementAt(offset - 1);
            if (lastElement != null) {
                if (lastElement instanceof PsiJavaToken && ((PsiJavaToken) lastElement).getTokenType() == JavaTokenType.DOT
                        && lastElement.getText().equals("?.")) {
                    return true;
                }
            }
            return false;
        });

    }
}
