package com.by122006.zircon.ijplugin;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZrJavaParserDefinition extends JavaParserDefinition {
    private static final Logger LOG = Logger.getInstance(ZrJavaParserDefinition.class.getName());

    public ZrJavaParserDefinition() {
        LOG.info( "ZrJavaParserDefinition" );
    }

    @NotNull
    @Override
    public Lexer createLexer(@Nullable Project project) {
        LanguageLevel level = project != null ? LanguageLevelProjectExtension.getInstance(project).getLanguageLevel() : LanguageLevel.HIGHEST;
        return createLexer(level);
    }

    @NotNull
    public static Lexer createLexer(@NotNull LanguageLevel level) {
        return new ZrJavaLexer(level);
    }

    @Override
    public @NotNull PsiElement createElement(@NotNull ASTNode node) {
        ensureSyntheticElvisLiteral(node);
        return super.createElement(node);
    }

    static void ensureSyntheticElvisLiteral(@NotNull ASTNode node) {
        if (node.getElementType() != JavaStubElementTypes.LITERAL_EXPRESSION
                || node.getTextLength() != 0
                || node.getFirstChildNode() != null
                || !(node instanceof CompositeElement)) {
            return;
        }

        // PSI wrappers can be initialized concurrently by read actions. Keep
        // the zero-width token insertion idempotent and recheck under the node
        // lock so the literal never receives duplicate synthetic children.
        synchronized (node) {
            if (node.getFirstChildNode() != null) {
                return;
            }
            ASTNode previous = node.getTreePrev();
            ASTNode next = node.getTreeNext();
            if (previous == null
                    || previous.getElementType() != JavaTokenType.QUEST
                    || next == null
                    || next.getElementType() != JavaTokenType.COLON
                    || previous.getStartOffset() + previous.getTextLength()
                    != next.getStartOffset()) {
                return;
            }

            // Keep the physical source and every reported range unchanged
            // while giving Java PSI a truthful literal token to type as null.
            ((CompositeElement) node).rawAddChildrenWithoutNotifications(
                    new PsiJavaTokenImpl(JavaTokenType.NULL_KEYWORD, ""));
        }
    }

}
