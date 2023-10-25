package com.by122006.zircon.ijplugin;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiExtensionMethod;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.AvailableSince("203")
class ZrPsiExtensionMethodUpper203 extends ZrPsiAugmentProvider.ZrPsiExtensionMethod implements PsiExtensionMethod {
    public ZrPsiExtensionMethodUpper203(boolean isStatic, PsiClass targetClass, PsiMethod targetMethod, PsiManager manager, Language language, @NotNull String name, PsiParameterList parameterList, PsiModifierList modifierList, PsiReferenceList throwsList, PsiTypeParameterList typeParameterList) {
        super(isStatic, targetClass, targetMethod, manager, language, name, parameterList, modifierList, throwsList, typeParameterList);
    }

    @Override
    public @NotNull PsiMethod getTargetMethod() {
        return targetMethod;
    }

    @Override
    public @Nullable PsiParameter getTargetReceiverParameter() {
        return isStatic ? null : targetMethod.getParameterList().getParameter(0);
    }

    @Override
    public @Nullable PsiParameter getTargetParameter(int index) {
        return targetMethod.getParameterList().getParameter(isStatic ? index : (index + 1));
    }
}
