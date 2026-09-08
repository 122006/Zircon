package com.by122006.zircon.ijplugin;

import com.by122006.zircon.ijplugin.util.ZrPluginUtil;
import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.BlockEx;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.CustomFormattingModelBuilder;
import com.intellij.formatting.DelegatingFormattingModel;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaFormattingModelBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegates Java formatting to IDEA and only protects the two token gaps that
 * make up Zircon's {@code ?:} operator.
 *
 * <p>The 253+ parser represents Elvis as {@code ?}, a zero-width synthetic
 * {@code null}, and {@code :}. Without this wrapper the Java formatter applies
 * ordinary ternary spacing and rewrites valid Zircon source to {@code ? :}.</p>
 */
public final class ZrJavaFormattingModelBuilder
        implements CustomFormattingModelBuilder {
    private final JavaFormattingModelBuilder delegate =
            new JavaFormattingModelBuilder();

    @Override
    public boolean isEngagedToFormat(@NotNull PsiElement context) {
        PsiFile file = context.getContainingFile();
        return file != null && ZrPluginUtil.hasZrPlugin(file);
    }

    @Override
    public @NotNull FormattingModel createModel(
            @NotNull FormattingContext formattingContext) {
        FormattingModel model = delegate.createModel(formattingContext);
        return new DelegatingFormattingModel(
                model, new ElvisSpacingBlock(model.getRootBlock()));
    }

    @Override
    public @Nullable TextRange getRangeAffectingIndent(
            @NotNull PsiFile file,
            int offset,
            @NotNull ASTNode elementAtOffset) {
        return delegate.getRangeAffectingIndent(file, offset, elementAtOffset);
    }

    private static final class ElvisSpacingBlock implements ASTBlock, BlockEx {
        private static final Spacing NO_SPACING =
                Spacing.createSpacing(0, 0, 0, false, 0);

        private final Block delegate;
        private final boolean elvisExpression;
        private List<Block> subBlocks;

        private ElvisSpacingBlock(Block delegate) {
            this.delegate = delegate;
            PsiElement psi = ASTBlock.getPsiElement(delegate);
            this.elvisExpression =
                    psi instanceof ZrPsiConditionalExpressionImpl
                            && ((ZrPsiConditionalExpressionImpl) psi)
                            .isElvisExpression();
        }

        @Override
        public @Nullable ASTNode getNode() {
            return ASTBlock.getNode(delegate);
        }

        @Override
        public @NotNull Language getLanguage() {
            if (delegate instanceof BlockEx) {
                return ((BlockEx) delegate).getLanguage();
            }
            ASTNode node = getNode();
            return node == null ? Language.ANY : node.getPsi().getLanguage();
        }

        @Override
        public @NotNull TextRange getTextRange() {
            return delegate.getTextRange();
        }

        @Override
        public @NotNull List<Block> getSubBlocks() {
            if (subBlocks == null) {
                List<Block> children = delegate.getSubBlocks();
                subBlocks = new ArrayList<>(children.size());
                for (Block child : children) {
                    subBlocks.add(new ElvisSpacingBlock(child));
                }
            }
            return subBlocks;
        }

        @Override
        public @Nullable Wrap getWrap() {
            return delegate.getWrap();
        }

        @Override
        public @Nullable Indent getIndent() {
            return delegate.getIndent();
        }

        @Override
        public @Nullable Alignment getAlignment() {
            return delegate.getAlignment();
        }

        @Override
        public @Nullable Spacing getSpacing(
                @Nullable Block child1,
                @NotNull Block child2) {
            Block rawChild1 = unwrap(child1);
            Block rawChild2 = unwrap(child2);
            if (elvisExpression && isElvisTokenGap(rawChild1, rawChild2)) {
                return NO_SPACING;
            }
            return delegate.getSpacing(rawChild1, rawChild2);
        }

        @Override
        public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
            return delegate.getChildAttributes(newChildIndex);
        }

        @Override
        public boolean isIncomplete() {
            return delegate.isIncomplete();
        }

        @Override
        public boolean isLeaf() {
            return delegate.isLeaf();
        }

        private static @Nullable Block unwrap(@Nullable Block block) {
            return block instanceof ElvisSpacingBlock
                    ? ((ElvisSpacingBlock) block).delegate
                    : block;
        }

        private static boolean isElvisTokenGap(@Nullable Block left,
                                               @NotNull Block right) {
            if (left == null) return false;
            IElementType leftType = ASTBlock.getElementType(left);
            IElementType rightType = ASTBlock.getElementType(right);
            boolean leftQuestion = leftType == JavaTokenType.QUEST;
            boolean rightColon = rightType == JavaTokenType.COLON;
            boolean leftEmpty = left.getTextRange().isEmpty();
            boolean rightEmpty = right.getTextRange().isEmpty();
            return leftQuestion && (rightEmpty || rightColon)
                    || rightColon && (leftEmpty || leftQuestion);
        }
    }
}
