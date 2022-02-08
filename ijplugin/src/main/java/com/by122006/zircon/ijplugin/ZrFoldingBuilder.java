package com.by122006.zircon.ijplugin;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ZrFoldingBuilder extends FoldingBuilderEx {
    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        if (root.getLanguage() != JavaLanguage.INSTANCE || quick) {
            return FoldingDescriptor.EMPTY;
        }
        final List<FoldingDescriptor> result = new ArrayList<>();
        root.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitLiteralExpression(PsiLiteralExpression expression) {
                if (expression instanceof PsiLiteralExpressionImpl
                        && ((PsiLiteralExpressionImpl) expression).getLiteralElementType() == JavaTokenType.STRING_LITERAL) {

                    if (expression.getText().startsWith("\"")) return;
                    String text = expression.getText();
                    Formatter formatter = Formatter.getAllFormatters().stream()
                            .filter(a -> text.charAt(a.prefix().length()) == '"' && text.startsWith(a.prefix()))
                            .findFirst().orElse(null);
                    if (formatter != null) {
                        List<StringRange> build = formatter.build(text);
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
        String oText = node.getText().substring(range.getStartOffset() - node.getStartOffset(), range.getEndOffset() - node.getStartOffset());
        String text = oText;
        if (oText.startsWith("${")) text = oText.substring(2, oText.length() - 1);
        else if (oText.startsWith("$")) text = oText.substring(1);
        if (text.length() > 10) {
//            return oText.substring(0,6)+"..."+oText.substring(oText.length()-10);
            return "${...}";
        }
        return "${" + text + "}";
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
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
