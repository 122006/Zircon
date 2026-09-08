package com.by122006.zircon.ijplugin;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLambdaParameterType;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.java.PsiConditionalExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Objects;

import static com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT;

/**
 * PSI representation of Zircon's Elvis expression.
 */
public class ZrPsiConditionalExpressionImpl extends PsiConditionalExpressionImpl {

    @Override
    public @NotNull PsiExpression getCondition() {
        if (!isElvisExpression()) {
            return super.getCondition();
        }

        // Do not expose the physical left operand as both the boolean
        // condition and the value-producing then branch.  Inference would
        // otherwise constrain a generic call to `boolean` and to the
        // surrounding target type at the same time.  This detached expression
        // mirrors javac's synthetic null check while the original left PSI is
        // exposed exactly once through getThenExpression().
        return CachedValuesManager.getCachedValue(this, () -> {
            PsiExpression left = getElvisLeftExpression();
            PsiExpression condition = left;
            if (left != null) {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(
                        getManager().getProject());
                try {
                    condition = factory.createExpressionFromText(
                            "(" + left.getText() + ") != null", getParent());
                } catch (IncorrectOperationException ignored) {
                    // Keep the physical expression available while the user is
                    // typing an incomplete left operand.
                }
            }
            return CachedValueProvider.Result.create(condition,
                    MODIFICATION_COUNT);
        });
    }

    @Override
    public @Nullable PsiExpression getThenExpression() {
        if (isElvisExpression()) {
            // The parser keeps a zero-width `null` node so that the physical
            // tree remains a valid Java conditional.  Semantically, however,
            // the value used when the left operand is non-null is the left
            // operand itself.  IDEA's poly-expression inference reads this
            // public accessor, so exposing the placeholder here loses all
            // constraints contributed by the left operand.
            return getElvisLeftExpression();
        }
        return getRawThenExpression();
    }

    private @Nullable PsiExpression getRawThenExpression() {
        PsiExpression expression = super.getThenExpression();
        if (expression != null && expression.getTextLength() == 0) {
            // Incremental reparsing may reuse the literal PSI node while
            // replacing its zero-width child. Restore the child before any
            // caller asks the platform literal implementation for its type.
            ZrJavaParserDefinition.ensureSyntheticElvisLiteral(
                    expression.getNode());
        }
        return expression;
    }

    @Override
    public @Nullable PsiType getType() {
        if (!isElvisExpression()) {
            return super.getType();
        }
        return getElvisType(getElvisLeftExpression(), getElvisElseExpression());
    }

    public boolean isElvisExpression() {
        return getElvisToken() != null;
    }

    private @Nullable ASTNode getElvisToken() {
        ASTNode token = super.findChildByType(ZrJavaTokenType.ELVIS);
        if (token != null) {
            return token;
        }
        ASTNode question = super.findChildByType(JavaTokenType.QUEST);
        if (question == null) {
            return null;
        }
        if ("?:".contentEquals(question.getChars())) {
            return question;
        }

        // The Syntax API lexer used by 253+ represents `?:` as three valid
        // Java tokens: `?`, a zero-length synthetic `null`, and `:`. Detect
        // that form by the two physical punctuation offsets; a regular
        // `? null :` expression cannot satisfy this adjacency check.
        ASTNode colon = getElvisColonToken();
        if (colon == null
                || question.getStartOffset() + question.getTextLength() != colon.getStartOffset()) {
            return null;
        }
        PsiExpression thenExpression = getRawThenExpression();
        if (!(thenExpression instanceof PsiLiteralExpression)
                || thenExpression.getTextLength() != 0) {
            return null;
        }
        ASTNode literalToken = thenExpression.getNode().getFirstChildNode();
        return (literalToken == null
                || literalToken.getElementType() == JavaTokenType.NULL_KEYWORD)
                ? question
                : null;
    }

    private @Nullable ASTNode getElvisColonToken() {
        return super.findChildByType(JavaTokenType.COLON);
    }

    private @Nullable PsiExpression getElvisLeftExpression() {
        return super.getCondition();
    }

    private @Nullable PsiExpression getElvisElseExpression() {
        PsiExpression expression = super.getElseExpression();
        if (expression != null) {
            return expression;
        }
        // Compatibility with the legacy parser form: left ELVIS right.
        ASTNode token = super.findChildByType(ZrJavaTokenType.ELVIS);
        ASTNode child = token == null ? null : token.getTreeNext();
        while (child != null) {
            if (child.getPsi() instanceof PsiExpression) {
                expression = (PsiExpression) child.getPsi();
            }
            child = child.getTreeNext();
        }
        return expression;
    }

    private @Nullable PsiType getElvisType(@Nullable PsiExpression left,
                                           @Nullable PsiExpression right) {
        PsiType leftType = left == null ? null : left.getType();
        PsiType rightType = right == null ? null : right.getType();

        if (Objects.equals(leftType, rightType)) {
            return leftType == null
                    ? null
                    : NullabilityCompat.join(leftType, rightType);
        }

        // Keep the Java conditional expression's target-typing semantics.
        // This is required for diamond expressions, generic method calls and
        // lambdas whose target type comes from an assignment or invocation.
        if (PsiUtil.isLanguageLevel8OrHigher(this)
                && PsiPolyExpressionUtil.isPolyExpression(this)) {
            PsiType targetType = InferenceSession.getTargetType(this);
            if (MethodCandidateInfo.isOverloadCheck()) {
                if (targetType != null
                        && leftType != null
                        && rightType != null
                        && targetType.isAssignableFrom(leftType)
                        && targetType.isAssignableFrom(rightType)) {
                    return targetType;
                }
                return null;
            }
            if (targetType != null) {
                return targetType;
            }
        }

        // A poly expression (for example, a lambda on the right) can obtain its
        // target type from the surrounding normal conditional expression.
        if (leftType == null || rightType == null) {
            PsiType platformType = super.getType();
            if (platformType != null && !TypeConversionUtil.isNullType(platformType)) {
                return platformType;
            }
            return leftType == null ? rightType : leftType;
        }

        int leftRank = TypeConversionUtil.getTypeRank(leftType);
        int rightRank = TypeConversionUtil.getTypeRank(rightType);
        if (leftType instanceof com.intellij.psi.PsiClassType
                && Objects.equals(rightType, PsiPrimitiveType.getUnboxedType(leftType))) {
            return rightType;
        }
        if (rightType instanceof com.intellij.psi.PsiClassType
                && Objects.equals(leftType, PsiPrimitiveType.getUnboxedType(rightType))) {
            return leftType;
        }

        if (TypeConversionUtil.isNumericType(leftRank)
                && TypeConversionUtil.isNumericType(rightRank)) {
            if (leftRank == 1 && rightRank == 2) {
                return rightType instanceof PsiPrimitiveType
                        ? rightType
                        : PsiPrimitiveType.getUnboxedType(rightType);
            }
            if (leftRank == 2 && rightRank == 1) {
                return leftType instanceof PsiPrimitiveType
                        ? leftType
                        : PsiPrimitiveType.getUnboxedType(leftType);
            }
            if (rightRank == 4
                    && (leftRank == 1 || leftRank == 2 || leftRank == 3)
                    && TypeConversionUtil.areTypesAssignmentCompatible(leftType, right)) {
                return leftType;
            }
            if (leftRank == 4
                    && (rightRank == 1 || rightRank == 2 || rightRank == 3)
                    && TypeConversionUtil.areTypesAssignmentCompatible(rightType, left)) {
                return rightType;
            }
            return TypeConversionUtil.binaryNumericPromotion(leftType, rightType);
        }

        if (TypeConversionUtil.isNullType(leftType)
                && !(rightType instanceof PsiPrimitiveType)) {
            return NullabilityCompat.join(rightType, leftType);
        }
        if (TypeConversionUtil.isNullType(rightType)
                && !(leftType instanceof PsiPrimitiveType)) {
            return NullabilityCompat.join(leftType, rightType);
        }
        if (TypeConversionUtil.isAssignable(leftType, rightType, false)) {
            return leftType;
        }
        if (TypeConversionUtil.isAssignable(rightType, leftType, false)) {
            return rightType;
        }

        if (TypeConversionUtil.isPrimitiveAndNotNull(leftType)) {
            leftType = ((PsiPrimitiveType) leftType).getBoxedType(this);
            if (leftType == null) return null;
        }
        if (TypeConversionUtil.isPrimitiveAndNotNull(rightType)) {
            rightType = ((PsiPrimitiveType) rightType).getBoxedType(this);
            if (rightType == null) return null;
        }
        if (leftType instanceof PsiLambdaParameterType
                || rightType instanceof PsiLambdaParameterType) {
            return null;
        }

        PsiType leastUpperBound =
                GenericsUtil.getLeastUpperBound(leftType, rightType, getManager());
        return leastUpperBound == null
                ? null
                : PsiUtil.captureToplevelWildcards(leastUpperBound, this);
    }

    /**
     * IDEA 253 added nullability directly to {@link PsiType}. The legacy
     * parser adapters still instantiate this PSI implementation on IDEA
     * 212-252, where those methods do not exist. Keep the newer behavior when
     * the API is available without leaving a static TypeNullability method
     * reference in this class's bytecode.
     */
    private static final class NullabilityCompat {
        private static final Method GET_NULLABILITY;
        private static final Method JOIN_NULLABILITY;
        private static final Method WITH_NULLABILITY;

        static {
            Method getNullability = null;
            Method joinNullability = null;
            Method withNullability = null;
            try {
                ClassLoader loader = PsiType.class.getClassLoader();
                Class<?> nullabilityClass = Class.forName(
                        "com.intellij.codeInsight.TypeNullability",
                        false,
                        loader);
                getNullability = PsiType.class.getMethod("getNullability");
                joinNullability = nullabilityClass.getMethod(
                        "join", nullabilityClass);
                withNullability = PsiType.class.getMethod(
                        "withNullability", nullabilityClass);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // IDEA 212-252 has no PsiType nullability API.
            }
            GET_NULLABILITY = getNullability;
            JOIN_NULLABILITY = joinNullability;
            WITH_NULLABILITY = withNullability;
        }

        private static PsiType join(PsiType preferred, PsiType other) {
            if (GET_NULLABILITY == null
                    || JOIN_NULLABILITY == null
                    || WITH_NULLABILITY == null) {
                return preferred;
            }
            try {
                Object preferredNullability =
                        GET_NULLABILITY.invoke(preferred);
                Object otherNullability = GET_NULLABILITY.invoke(other);
                Object joinedNullability = JOIN_NULLABILITY.invoke(
                        preferredNullability, otherNullability);
                Object joinedType = WITH_NULLABILITY.invoke(
                        preferred, joinedNullability);
                return joinedType instanceof PsiType
                        ? (PsiType) joinedType
                        : preferred;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return preferred;
            }
        }
    }
}
