package com.by122006.zircon.ijplugin;

import com.by122006.zircon.ijplugin.util.ZrPluginUtil;
import com.by122006.zircon.ijplugin.util.ZrUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.sun.tools.javac.parser.FStringFormatter;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.StringRange;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ZrFoldingBuilder extends FoldingBuilderEx {
    ZirconSettings settings = ZirconSettings.getInstance();

    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        if (!ZrPluginUtil.hasZrPlugin(root)) return FoldingDescriptor.EMPTY;
        if (root.getLanguage() != JavaLanguage.INSTANCE || quick) {
            return FoldingDescriptor.EMPTY;
        }
        if (!settings.ZrStringFoldEnable) return FoldingDescriptor.EMPTY;
        final List<FoldingDescriptor> result = new ArrayList<>();
        root.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitLiteralExpression(PsiLiteralExpression expression) {
                if (expression instanceof PsiLiteralExpressionImpl
                        && expression.getNode().getFirstChildNode() != null
                        && ((PsiLiteralExpressionImpl) expression).getLiteralElementType() == JavaTokenType.STRING_LITERAL) {
                    if (expression.getText().startsWith("\"")) return;
                    String text = expression.getText();
                    Formatter formatter = ZrUtil.checkPsiLiteralExpression(expression);
                    if (formatter != null) {
                        final ZrStringModel model = formatter.build(text);
                        List<StringRange> build = model.getList();
                        List<FoldingDescriptor> collect = build.stream().filter(a -> a.codeStyle == 1)
                                .map(a -> {
                                    TextRange textRange;
                                    int textOffset = expression.getTextOffset();
                                    if (text.charAt(a.startIndex - 1) == '$') {
                                        textRange = new TextRange(a.startIndex - 1 + textOffset, a.endIndex + textOffset);
                                    } else if (text.charAt(a.startIndex - 1) == '{') {
                                        textRange = new TextRange(a.startIndex - 2 + textOffset, a.endIndex + 1 + textOffset);
                                    } else if (formatter instanceof FStringFormatter) {
                                        int start = text.lastIndexOf("${", a.startIndex);
                                        textRange = new TextRange(start + textOffset, a.endIndex + 1 + textOffset);
                                    } else
                                        textRange = new TextRange(a.startIndex + textOffset, a.endIndex + textOffset);
                                    if (textRange.getLength() < settings.ZrStringFoldCharCount) return null;
                                    if (text.matches("[0-9A-Za-z_\\u4e00-\\u9fa5$]+(?:\\(\\))?")) return null;
                                    return new FoldingDescriptor(expression, textRange);
                                }).filter(Objects::nonNull).collect(Collectors.toList());
                        result.addAll(collect);
                    }
                }
                super.visitLiteralExpression(expression);
            }
        });
        return result.toArray(FoldingDescriptor.EMPTY);
    }


    @Nullable
    public String getPlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
        return "${...}";
    }

    @Override
    public @Nullable
    String getPlaceholderText(@NotNull ASTNode node) {
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull FoldingDescriptor foldingDescriptor) {
        return true;
    }
}
