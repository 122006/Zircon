package com.by122006.zircon.ijplugin;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.java.PsiConditionalExpressionImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;

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
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(this.getManager().getProject());
        if (getThenExpression() == null) {
            return elementFactory.createExpressionFromText("null", getParent());
        }
        return CachedValuesManager.getCachedValue(this, () -> {
            final String s = "\"\".hashCode()>100";
            final PsiExpression expressionFromText = elementFactory.createExpressionFromText(s, getParent());
            if (expressionFromText instanceof ZrPsiBinaryExpressionImpl) {
                ((ZrPsiBinaryExpressionImpl) expressionFromText).setForcePhysical(true);
            }
            return new CachedValueProvider.Result<>(expressionFromText, MODIFICATION_COUNT);
        });
//        return super.getThenExpression();
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
        LOG.assertTrue(ChildRole.isUnique(role));
        switch (role) {
            case 32:
                return null;
            case 87:
                return this.findChildByType(ZrJavaTokenType.ELVIS);
            case 112:
                return this.getFirstChildNode();
            case 113:
                ASTNode colon = this.findChildByType(ZrJavaTokenType.ELVIS);
                if (colon == null) {
                    return null;
                }
                return this.getLastChildNode();
            case 114:
                return this.findChildByType(ZrJavaTokenType.ELVIS);
            default:
                return null;
        }
    }

    public int getChildRole(@NotNull ASTNode child) {
        final boolean b = child.getElementType() == ZrJavaTokenType.ELVIS;
        if (b) {
            return 87;
        }
        return super.getChildRole(child);
    }
}
