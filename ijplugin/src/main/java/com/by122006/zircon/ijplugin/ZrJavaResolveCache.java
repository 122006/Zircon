package com.by122006.zircon.ijplugin;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
//        if (expr instanceof PsiBinaryExpression) {
//            final PsiJavaToken operationSign = ((PsiBinaryExpressionImpl) expr).getOperationSign();
//            if (operationSign.getTokenType() == JavaTokenType.OROR && operationSign.getText().equals("?:")) {
//                f = (e) -> {
//                    try {
//                        PsiExpression expr1 = ((PsiBinaryExpressionImpl) expr).getLOperand();
//                        PsiExpression expr2 = ((PsiBinaryExpressionImpl) expr).getROperand();
//                        if (expr2 == null) return expr1.getType();
////                        final PsiType type1 = expr1.getType();
////                        final PsiType type2 = expr2.getType();
//                        final String replacement = "true?" + expr1.getText() + ":" + expr2.getText();
//                        PsiExpression psiExpression = JavaPsiFacade
//                                .getElementFactory(expr.getProject())
//                                .createExpressionFromText(replacement, expr.getParent());
//                        expr.replace(psiExpression);
//                        return psiExpression.getType();
////                        PsiConditionalExpression[] conditionalExpression = new PsiConditionalExpression[1];
////                        PsiElementVisitor visitor = new PsiElementVisitor() {
////                            @Override
////                            public void visitElement(@NotNull PsiElement element) {
////                                if (element instanceof PsiConditionalExpression && element.getText().replace(" ", "").equals(replacement.replace(" ", ""))) {
////                                    conditionalExpression[0] = (PsiConditionalExpression) element;
////                                }
////                                super.visitElement(element);
////                            }
////                        };
////                        visitor.visitElement(parentExpression);
////                        return conditionalExpression[0] == null ? expr1.getType() : conditionalExpression[0].getType();
//                    } catch (ProcessCanceledException ex) {
//                        throw ex;
//                    } catch (Throwable ex) {
//                        ex.printStackTrace();
//                        return ((PsiBinaryExpression) e).getLOperand().getType();
//                    }
//                };
//            }
//        }
        return super.getType(expr, f);
    }


}
