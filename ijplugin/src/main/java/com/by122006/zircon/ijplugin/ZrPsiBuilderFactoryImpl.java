package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrClassLoaderHelper;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderFactoryImpl;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.HolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;
import com.sun.tools.javac.parser.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExReflection;

import java.lang.reflect.Field;
import java.util.function.Supplier;

public class ZrPsiBuilderFactoryImpl extends PsiBuilderFactoryImpl {
    static {
        try {
            final Field instance = JavaParser.class.getDeclaredField("myExpressionParser");
            instance.setAccessible(true);
            Object value = null;
//            ReflectionUtil.setDeclaredField(null, PsiJavaParserFacadeImpl.class, "EXPRESSION", (JavaParserUtil.ParserWrapper)
//                    (syntaxTreeBuilder, languageLevel) ->
//                            new ZrJavaParser252(languageLevel).getExpressionParser().parse(syntaxTreeBuilder));
            final HolderFactory dummyHolderFactory = ReflectionUtil.getDeclaredField(null, DummyHolderFactory.class, "INSTANCE");
            ReflectionUtil.setDeclaredField(null, DummyHolderFactory.class, "INSTANCE", new HolderFactory() {
                @Override
                @SuppressWarnings("UnstableApiUsage")
                public @NotNull DummyHolder createHolder(@NotNull PsiManager psiManager, @NotNull TreeElement treeElement, @Nullable PsiElement psiElement) {
                    if (treeElement instanceof JavaDummyElement) {
                        final Object myParser = treeElement.reflectionFieldValue("myParser");
                        final Supplier<CharSequence> myText = treeElement.reflectionFieldValue("myText");
                        if (myParser == PsiJavaParserFacadeImpl.getStaticFieldValue("EXPRESSION")) {
                            BasicJavaParserUtil.ParserWrapper parserWrapper = (builder, level) -> {
                                new com.intellij.java.syntax.parser.JavaParser(level).getExpressionParser().parse(builder);
                            };
                            final LanguageLevel level = level(psiElement);
                            return dummyHolderFactory.createHolder(psiManager, new JavaDummyElement(myText.get(), parserWrapper, level), psiElement);
                        }
                    }
                    return dummyHolderFactory.createHolder(psiManager, treeElement, psiElement);
                }

                protected LanguageLevel level(@Nullable PsiElement context) {
                    return context != null && context.isValid() ? PsiUtil.getLanguageLevel(context) : LanguageLevel.HIGHEST;
                }

                @Override
                public @NotNull DummyHolder createHolder(@NotNull PsiManager psiManager, @Nullable CharTable charTable, boolean b) {
                    return dummyHolderFactory.createHolder(psiManager, charTable, b);
                }

                @Override
                public @NotNull DummyHolder createHolder(@NotNull PsiManager psiManager, @Nullable PsiElement psiElement) {
                    return dummyHolderFactory.createHolder(psiManager, psiElement);
                }

                @Override
                public @NotNull DummyHolder createHolder(@NotNull PsiManager psiManager, @NotNull Language language, @Nullable PsiElement psiElement) {
                    return dummyHolderFactory.createHolder(psiManager, language, psiElement);
                }

                @Override
                public @NotNull DummyHolder createHolder(@NotNull PsiManager psiManager, @Nullable TreeElement treeElement, @Nullable PsiElement psiElement, @Nullable CharTable charTable) {
                    return dummyHolderFactory.createHolder(psiManager, treeElement, psiElement, charTable);

                }

                @Override
                public @NotNull DummyHolder createHolder(@NotNull PsiManager psiManager, @Nullable PsiElement psiElement, @Nullable CharTable charTable) {
                    return dummyHolderFactory.createHolder(psiManager, psiElement, charTable);
                }

                @Override
                public @NotNull DummyHolder createHolder(@NotNull PsiManager psiManager, @Nullable CharTable charTable, @NotNull Language language) {
                    return dummyHolderFactory.createHolder(psiManager, charTable, language);

                }
            });
            if (!ZrClassLoaderHelper.hasClass("com.intellij.lang.java.parser.BasicOldExpressionParser")) {
                final Class<?> clazz = ZrClassLoaderHelper.loadClass(
                        new String[]{}
                        , "com.by122006.zircon.ijplugin223.ZrExpressionParser"
                        , "ijplugin_223");
                if (clazz != null)
                    value = clazz.getDeclaredConstructors()[0].newInstance(JavaParser.INSTANCE);
            } else {
                final Class<?> clazz = ZrClassLoaderHelper.loadClass(
                        new String[]{"com.by122006.zircon.ijplugin241.ZrBasicOldExpressionParser"
                                , "com.by122006.zircon.ijplugin241.ZrExpressionParser"
                                , "com.by122006.zircon.ijplugin241.ZrExpressionParser$1"
                                , "com.by122006.zircon.ijplugin241.ZrExpressionParser$2"}
                        , "com.by122006.zircon.ijplugin241.ZrExpressionParser"
                        , "ijplugin_241");
                if (clazz != null)
                    value = clazz.getDeclaredConstructors()[0].newInstance(JavaParser.INSTANCE);
            }
            instance.set(JavaParser.INSTANCE, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NotNull
    public PsiBuilder createBuilder(@NotNull Project project, @NotNull ASTNode chameleon, @Nullable Lexer lexer, @NotNull Language lang, @NotNull CharSequence seq) {
        ParserDefinition parserDefinition = reflectionInvokeMethod("getParserDefinition", lang, chameleon.getElementType());
        if (lexer instanceof JavaLexer) {
            LanguageLevel level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
            lexer = new ZrJavaLexer(level);
        } else if (lexer == null) {
            try {
                lexer = parserDefinition.createLexer(project);
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("createLexer error:" + e.getMessage());
            }
        }

        PsiBuilderImpl psiBuilder = new PsiBuilderImpl(project, parserDefinition, lexer, chameleon, seq);
        return psiBuilder;
    }
}
