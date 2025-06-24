package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.AutoPopupControllerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExObject;

/**
 * @ClassName: ZrAutoPopupController
 * @Author: 122006
 * @Date: 2025/6/20 17:48
 * @Description:
 */
public class ZrAutoPopupController extends AutoPopupControllerImpl {
    public ZrAutoPopupController(@NotNull Project project) {
        super(project);
    }

    @Override
    public void autoPopupMemberLookup(Editor editor, @Nullable Condition<? super PsiFile> condition) {
        //额外判断，支持?.前置的自动补全
        if (condition != null) {
            Condition<? super PsiFile> newCondition = file -> {
                int offset = editor.getCaretModel().getOffset();
                PsiElement lastElement = file.findElementAt(offset - 1);
                if (lastElement != null) {
                    if (lastElement instanceof PsiJavaToken && ((PsiJavaToken) lastElement).getTokenType() == JavaTokenType.DOT && lastElement.getText().equals("?.")) {
                        return true;
                    }
                }
                return condition.value(file);
            };
            super.autoPopupMemberLookup(editor, newCondition);
            return;

        }
        super.autoPopupMemberLookup(editor, condition);
    }

}
