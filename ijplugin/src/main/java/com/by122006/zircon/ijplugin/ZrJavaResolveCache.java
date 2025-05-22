package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.formatter.java.JavaSpacePropertyProcessor;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExReflection;

import java.lang.reflect.Field;
import java.util.Map;

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
            if (((PsiBinaryExpression) expr).getOperationTokenType() == JavaTokenType.OROR && ZrPluginUtil.hasOptionalChaining(((PsiBinaryExpression) expr).getLOperand())) {
                f = (e) -> ((PsiBinaryExpression) e).getLOperand().getType();
            }
        }
        return super.getType(expr, f);
    }


}
