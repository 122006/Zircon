package com.by122006.zircon.ijplugin;

import com.by122006.zircon.ijplugin.util.ZrClassLoaderHelper;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.formatter.java.JavaSpacePropertyProcessor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExReflection;

import java.lang.reflect.Field;
import java.util.Map;

public class ZrPsiBuilderFactoryImpl extends PsiBuilderFactoryImpl {
    private static final Logger LOG = Logger.getInstance(ZrPsiBuilderFactoryImpl.class);

    static {
        try {
            //强制设置)和.之间不含空格
            Map<Pair<IElementType, IElementType>, Boolean> ourTokenStickingMatrix = JavaSpacePropertyProcessor.class.getStaticFieldValue("ourTokenStickingMatrix");
            ourTokenStickingMatrix.put(Pair.pair(JavaTokenType.RPARENTH, JavaTokenType.DOT), true);
            ourTokenStickingMatrix.put(Pair.pair(JavaTokenType.LPARENTH, JavaTokenType.STRING_LITERAL), true);
            ourTokenStickingMatrix.put(Pair.pair(JavaTokenType.STRING_LITERAL, JavaTokenType.RPARENTH), true);
            ourTokenStickingMatrix.put(Pair.pair(JavaTokenType.STRING_LITERAL, JavaTokenType.COMMA), true);
            ourTokenStickingMatrix.put(Pair.pair(JavaTokenType.STRING_LITERAL, JavaTokenType.SEMICOLON), true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static {
        installLegacyExpressionParser();
    }

    /**
     * IDEA versions before 253 still use the mutable JavaParser implementation.
     * Keep the historical ABI-specific parser classes outside the normal plugin
     * class path and define only the implementation matching the running IDE.
     *
     * IDEA 253+ uses the public Syntax API extension registered by the plugin,
     * so loading the legacy parser there would apply the Zircon transformation
     * twice.
     */
    private static void installLegacyExpressionParser() {
        BuildNumber build = ApplicationInfo.getInstance().getBuild();
        int baselineVersion = build.getBaselineVersion();
        if (baselineVersion >= 253) {
            return;
        }

        try {
            Field expressionParserField =
                    JavaParser.class.getDeclaredField("myExpressionParser");
            expressionParserField.setAccessible(true);

            Class<?> parserClass;
            if (baselineVersion < 240) {
                parserClass = ZrClassLoaderHelper.loadClass(
                        new String[]{},
                        "com.by122006.zircon.ijplugin223.ZrExpressionParser",
                        "ijplugin_223");
            } else {
                parserClass = ZrClassLoaderHelper.loadClass(
                        new String[]{
                                "com.by122006.zircon.ijplugin241.ZrBasicOldExpressionParser",
                                "com.by122006.zircon.ijplugin241.ZrExpressionParser"
                        },
                        "com.by122006.zircon.ijplugin241.ZrExpressionParser",
                        "ijplugin_241");
            }

            if (parserClass != null) {
                Object parser = parserClass.getDeclaredConstructors()[0]
                        .newInstance(JavaParser.INSTANCE);
                expressionParserField.set(JavaParser.INSTANCE, parser);
            }
        } catch (Exception e) {
            LOG.error("Zircon does not support IDEA build "
                    + baselineVersion + " through the legacy parser adapter", e);
        }
    }

    @NotNull
    public PsiBuilder createBuilder(@NotNull Project project, @NotNull ASTNode chameleon, @Nullable Lexer lexer, @NotNull Language lang, @NotNull CharSequence seq) {
        if (lexer instanceof JavaLexer) {
            LanguageLevel level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
            lexer = new ZrJavaLexer(level);
        }
        return super.createBuilder(project, chameleon, lexer, lang, seq);
    }
}
