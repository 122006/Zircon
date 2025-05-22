package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.compiler.JavacQuirksInspectionVisitor;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExObject;

/**
 * @ClassName: ZrJavaElementVisitor
 * @Author: 122006
 * @Date: 2025/5/16 14:58
 * @Description:
 */
public class ZrJavaElementVisitor extends JavacQuirksInspectionVisitor {

    public ZrJavaElementVisitor(ProblemsHolder holder) {
        super(holder);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
        final PsiType lType = assignment.getLExpression().getType();
        if (lType == null) return;
        final PsiExpression rExpression = assignment.getRExpression();
        if (rExpression == null) return;
        PsiJavaToken operationSign = assignment.getOperationSign();

        IElementType eqOpSign = operationSign.getTokenType();
        IElementType opSign = TypeConversionUtil.convertEQtoOperation(eqOpSign);
        if (opSign == null) return;
        if (JavaSdkVersion.JDK_1_6.equals(JavaVersionService.getInstance().getJavaSdkVersion(assignment)) &&
                PsiType.getJavaLangObject(assignment.getManager(), assignment.getResolveScope()).equals(lType)) {
            String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
            return;
        }
        super.visitAssignmentExpression(assignment);

    }

}
