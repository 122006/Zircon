package com.by122006.zircon.ijplugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

public class ZrPsiPolyadicExpressionImpl extends PsiPolyadicExpressionImpl {
    private static final Logger LOG = Logger.getInstance(ZrPsiPolyadicExpressionImpl.class.getName());
    public static final PsiType NoType = TypeConversionUtil.NULL_TYPE;


//    public PsiType getType() {
//        return JavaResolveCache.getInstance(getProject()).getType(this, OO_TYPE_EVALUATOR);
//    }

//    private static final Function<PsiPolyadicExpressionImpl,PsiType> OO_TYPE_EVALUATOR = new NullableFunction<PsiPolyadicExpressionImpl, PsiType>() {
//        @Override
//        public PsiType fun(PsiPolyadicExpressionImpl param) {
//            // copied from com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl.doGetType
//            PsiExpression[] operands = param.getOperands();
//            PsiType lType = null;
//
//            IElementType sign = param.getOperationTokenType();
//            for (int i=1; i<operands.length;i++) {
//                PsiType rType = operands[i].getType();
//                // optimization: if we can calculate type based on right type only
//                PsiType type = TypeConversionUtil.calcTypeForBinaryExpression(null, rType, sign, false);
//                if (type != TypeConversionUtil.NULL_TYPE) return type;
//                if (lType == null) lType = operands[0].getType();
//                PsiType oldlType = lType;
//                lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, sign, true);
//
//                // try OO if something wrong
//                if (!TypeConversionUtil.isBinaryOperatorApplicable(sign, oldlType, rType, false))
//                    lType = OOResolver.getOOType(oldlType, rType, param.getTokenBeforeOperand(operands[i]));
//            }
//            return lType;
//        }
//    };
}
