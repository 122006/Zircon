package com.by122006.zircon.ijplugin;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExObject;

/**
 * @ClassName: ZrJavaResolveCache
 * @Author: 122006
 * @Date: 2025/5/19 20:16
 * @Description:
 */
public class ZrJavaResolveCache extends JavaResolveCache {

    public ZrJavaResolveCache(@NotNull Project project) {
        super(project);
    }

    @Override
    public @Nullable <T extends PsiExpression> PsiType getType(@NotNull T expr, @NotNull Function<? super T, ? extends PsiType> f) {
        if (expr instanceof PsiBinaryExpression) {
            final PsiJavaToken operationSign = ((PsiBinaryExpressionImpl) expr).getOperationSign();
            if (operationSign.getTokenType() == JavaTokenType.OROR && operationSign.getText().equals("?:")) {
                f = (e) -> ((PsiBinaryExpression) e).getLOperand().getType();
            }
        }
        return super.getType(expr, f);
    }


}
