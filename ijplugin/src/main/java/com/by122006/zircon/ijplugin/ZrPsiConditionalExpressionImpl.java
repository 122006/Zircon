package com.by122006.zircon.ijplugin;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.java.PsiConditionalExpressionImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExObject;

import static com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT;

/**
 * @ClassName: ZrPsiConditionalExpressionImpl
 * @Author: 122006
 * @Date: 2025/7/2 10:18
 * @Description:
 */
public class ZrPsiConditionalExpressionImpl extends PsiConditionalExpressionImpl {
    private static final Logger LOG = Logger.getInstance(ZrPsiConditionalExpressionImpl.class);

    @Override
    public @NotNull PsiExpression getCondition() {
        if (isElvisExpressionLower253()) {
            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(this.getManager().getProject());
            return CachedValuesManager.getCachedValue(this, () -> {
                final String s = "java.util.Objects.nonNull(" + super.getThenExpression().getText() + ")";
                PsiExpression expressionFromText;
                try {
                    expressionFromText = elementFactory.createExpressionFromText(s, getParent());
                    if (expressionFromText instanceof ZrPsiBinaryExpressionImpl) {
                        ((ZrPsiBinaryExpressionImpl) expressionFromText).setForcePhysical(true);
                    }
                } catch (IncorrectOperationException e) {
                    LOG.error(e);
                    expressionFromText=null;
                }
                return new CachedValueProvider.Result<>(expressionFromText, MODIFICATION_COUNT);
            });
        }
        if (isElvisExpressionUpper253()) {
            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(this.getManager().getProject());
            if (super.getFirstChild().getText().contains("?:")) {
                return elementFactory.createExpressionFromText("null", getParent());
            }
            return CachedValuesManager.getCachedValue(this, () -> {
                final String s = "java.util.Objects.nonNull(" + super.getFirstChild().getText() + ")";
                PsiExpression expressionFromText;
                try {
                    expressionFromText = elementFactory.createExpressionFromText(s, getParent());
                    if (expressionFromText instanceof ZrPsiBinaryExpressionImpl) {
                        ((ZrPsiBinaryExpressionImpl) expressionFromText).setForcePhysical(true);
                    }
                } catch (IncorrectOperationException e) {
                    LOG.error(e);
                    expressionFromText=null;
                }
                return new CachedValueProvider.Result<>(expressionFromText, MODIFICATION_COUNT);
            });
        }
        return super.getCondition();
    }

    @Override
    public PsiExpression getThenExpression() {
        return super.getThenExpression();
    }

    @Override
    public PsiExpression getElseExpression() {
        return super.getElseExpression();
    }

    public ASTNode findChildByRole(int role) {
        if (isElvisExpressionLower253()) {
            LOG.assertTrue(ChildRole.isUnique(role));
            switch (role) {
                case ChildRole.CONDITION:
                    return null;
                case ChildRole.COLON:
                    return this.findChildByType(ZrJavaTokenType.ELVIS);
                case ChildRole.THEN_EXPRESSION:
                    return ElementType.EXPRESSION_BIT_SET.contains(this.getFirstChildNode().getElementType()) ? this.getFirstChildNode() : null;
                case ChildRole.ELSE_EXPRESSION:
                    ASTNode colon = this.findChildByType(ZrJavaTokenType.ELVIS);
                    if (colon == null) {
                        return null;
                    }
                    return ElementType.EXPRESSION_BIT_SET.contains(this.getLastChildNode().getElementType()) ? this.getLastChildNode() : null;
                case ChildRole.QUEST:
                    return this.findChildByType(ZrJavaTokenType.ELVIS);
                default:
                    return null;
            }
        } else if (isElvisExpressionUpper253()) {
            switch (role) {
                case ChildRole.CONDITION:
                    return getCondition().getNode();

                case ChildRole.QUEST:
                    return findChildByType(JavaTokenType.QUEST);

                case ChildRole.THEN_EXPRESSION:
                    return getFirstChildNode();

                case ChildRole.COLON:
                    return findChildByType(JavaTokenType.QUEST);

                case ChildRole.ELSE_EXPRESSION:
                    ASTNode colon = super.findChildByRole(ChildRole.QUEST);
                    if (colon == null) return null;
                    return ElementType.EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;

                default:
                    return null;
            }
        } else return super.findChildByRole(role);

    }

    private boolean isElvisExpressionLower253() {
        return super.findChildByType(ZrJavaTokenType.ELVIS) != null;
    }

    private boolean isElvisExpressionUpper253() {
        return super.findChildByType(JavaTokenType.QUEST) != null
                && super.findChildByType(JavaTokenType.QUEST).getText().equals("?:");
    }

    public int getChildRole(@NotNull ASTNode child) {
        final boolean b = child.getElementType() == ZrJavaTokenType.ELVIS;
        if (b) {
            return 87;
        }
        return super.getChildRole(child);
    }
}
