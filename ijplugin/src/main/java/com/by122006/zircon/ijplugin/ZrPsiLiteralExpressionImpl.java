package com.by122006.zircon.ijplugin;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.java.stubs.impl.PsiLiteralStub;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @ClassName: ZrPsiLiteralExpressionImpl
 * @Author: 122006
 * @Date: 2025/10/11 16:14
 * @Description:
 */
public class ZrPsiLiteralExpressionImpl extends PsiLiteralExpressionImpl {
    public ZrPsiLiteralExpressionImpl(@NotNull PsiLiteralStub stub) {
        super(stub);
    }

    public ZrPsiLiteralExpressionImpl(@NotNull ASTNode node) {
        super(node);
//        if (this.getText().isEmpty()) {
//            node.getTreeParent().replaceChild(getNode(), JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText("null", getParent()).getNode());
////            final PsiLiteralExpressionImpl psiLiteralExpression = new PsiLiteralExpressionImpl(getNode()) {
////                @Override
////                public IElementType getLiteralElementType() {
////                    return JavaTokenType.NULL_KEYWORD;
////                }
////            };
////            add(psiLiteralExpression);
//        }
    }

    @Override
    public String getText() {
        if (this.getGreenStub() == null && this.getNode().getFirstChildNode() == null) {
            return "null";
        }
        return super.getText();
    }

//    @Override
//    public PsiElement @NotNull [] getChildren() {
//        if (this.getGreenStub() == null && this.getNode().getFirstChildNode() == null) {
//            return new PsiElement[]{new PsiLiteralExpressionImpl(getNode()) {
//                @Override
//                public IElementType getLiteralElementType() {
//                    return JavaTokenType.NULL_KEYWORD;
//                }
//            }};
//        }
//        return super.getChildren();
//    }

    @Override
    public PsiElement getFirstChild() {
        if (this.getGreenStub() == null && this.getNode().getFirstChildNode() == null) {
            return new PsiLiteralExpressionImpl(getNode()) {
                @Override
                public IElementType getLiteralElementType() {
                    return JavaTokenType.NULL_KEYWORD;
                }
            };
        }
        return super.getFirstChild();
    }

    @Override
    public IElementType getLiteralElementType() {
//        if (getParent() != null && getParent().getText().endsWith("?:") && getText().isEmpty()) {
//            return JavaTokenType.NULL_KEYWORD;
//        }
        if (this.getGreenStub() == null && this.getNode().getFirstChildNode() == null) {
            return JavaTokenType.NULL_KEYWORD;
        }
        return super.getLiteralElementType();
    }
}
