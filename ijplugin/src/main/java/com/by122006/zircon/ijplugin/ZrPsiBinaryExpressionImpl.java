package com.by122006.zircon.ijplugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @ClassName: ZrPsiBinaryExpressionImpl
 * @Author: 122006
 * @Date: 2025/7/2 9:09
 * @Description:
 */
public class ZrPsiBinaryExpressionImpl extends PsiBinaryExpressionImpl {
    private static final Logger LOG = Logger.getInstance(ZrPsiBinaryExpressionImpl.class);

    public ZrPsiBinaryExpressionImpl() {
        this(JavaElementType.BINARY_EXPRESSION);
    }

    public ZrPsiBinaryExpressionImpl(@NotNull IElementType elementType) {
        super(elementType);
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
        super.accept(visitor);
    }

    Boolean forcePhysical = null;

    public void setForcePhysical(boolean b) {
        forcePhysical = b;
    }

    @Override
    public boolean isPhysical() {
        return forcePhysical != null ? forcePhysical : super.isPhysical();
    }
//
//    @Override
//    public @NotNull PsiJavaToken getOperationSign() {
//        if (getText().endsWith("?:")) {
//            return new PsiKeywordImpl(JavaTokenType.NE, "!=") {
//                @Override
//                public PsiElement getPrevSibling() {
//                    return ZrPsiBinaryExpressionImpl.super.getLOperand();
//                }
//
//                @Override
//                public PsiElement getNextSibling() {
//                    return ZrPsiBinaryExpressionImpl.super.getROperand();
//                }
//            };
//        }
//        return super.getOperationSign();
//    }
//
//    @Override
//    public PsiExpression getROperand() {
//        if (getText().endsWith("?:")) {
//            final PsiLiteralExpressionImpl psiLiteralExpression = new PsiLiteralExpressionImpl(super.getROperand().getNode()) {
//                @Override
//                public IElementType getLiteralElementType() {
//                    return JavaTokenType.NULL_KEYWORD;
//                }
//            };
//            return psiLiteralExpression;
//        }
//
//        return super.getROperand();
//    }
}
