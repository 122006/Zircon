package com.by122006.zircon.ijplugin252;

import com.intellij.java.syntax.JavaSyntaxDefinition;
import com.intellij.java.syntax.element.JavaDocSyntaxElementType;
import com.intellij.java.syntax.element.JavaSyntaxTokenType;
import com.intellij.java.syntax.element.JavaWhitespaceOrCommentBindingPolicy;
import com.intellij.platform.syntax.LanguageSyntaxDefinition;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.SyntaxElementTypeSet;
import com.intellij.platform.syntax.SyntaxElementTypeSetKt;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.parser.OpaqueElementPolicy;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
import com.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName: ZrJavaSyntaxDefinitionExtension
 * @Author: 122006
 * @Date: 2025/8/25 16:46
 * @Description:
 */
public class ZrJavaSyntaxDefinitionExtension implements LanguageSyntaxDefinition {
    private static final SyntaxElementTypeSet COMMENTS = createComments();

    @Override
    public @Nullable WhitespaceOrCommentBindingPolicy getWhitespaceOrCommentBindingPolicy() {
        return JavaWhitespaceOrCommentBindingPolicy.INSTANCE;
    }

    @Override
    public @Nullable OpaqueElementPolicy getOpaqueElementPolicy() {
        return null;
    }

    @Override
    public @NotNull SyntaxElementTypeSet getComments() {
        return COMMENTS;
    }

    @Override
    public void parse(@NotNull SyntaxTreeBuilder syntaxTreeBuilder) {
        JavaSyntaxDefinition.parse(LanguageLevel.HIGHEST, syntaxTreeBuilder);
    }

    @Override
    public @NotNull Lexer createLexer() {
        // Lexers are stateful. Sharing one instance corrupts concurrent indexing/parsing.
        return new ZrJavaLexer252(LanguageLevel.HIGHEST);
    }

    private static @NotNull SyntaxElementTypeSet createComments() {
        List<SyntaxElementType> comments = new ArrayList<>();
        comments.add(JavaSyntaxTokenType.END_OF_LINE_COMMENT);
        comments.add(JavaSyntaxTokenType.C_STYLE_COMMENT);
        comments.add(JavaDocSyntaxElementType.DOC_COMMENT);

        // Markdown Javadoc became a separate token in 261. Resolve the public
        // constant lazily so the same plugin binary still loads on 253.
        try {
            Field field = JavaDocSyntaxElementType.class.getField(
                    "DOC_MARKDOWN_COMMENT");
            Object token = field.get(null);
            if (token instanceof SyntaxElementType) {
                comments.add((SyntaxElementType) token);
            }
        } catch (ReflectiveOperationException ignored) {
            // 253 has no dedicated Markdown Javadoc token.
        }

        return SyntaxElementTypeSetKt.syntaxElementTypeSetOf(
                comments.toArray(new SyntaxElementType[0]));
    }
}
