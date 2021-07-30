package com.by122006.zircon.ijplugin;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiClassUtil;
import formatter.Formatter;
import formatter.StringRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ZrStringLiteralInjector implements LanguageInjector {
    private static final Logger LOG = Logger.getInstance(ZrStringLiteralInjector.class.getName());

    @Override
    public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces places) {
        if (!(host instanceof PsiLiteralExpressionImpl)) return;
        if (!(host.getLanguage().isKindOf(JavaLanguage.INSTANCE))) return;
        PsiLiteralExpressionImpl impl = (PsiLiteralExpressionImpl) host;
        if (!(impl.getLiteralElementType() == JavaTokenType.STRING_LITERAL)) return;
        String text = impl.getCanonicalText();
        if (text.startsWith( "\"" )) return;
        List<Formatter> allFormatters = Formatter.getAllFormatters();
        int endIndex = text.indexOf( "\"" );
        if (endIndex == -1) {
            LOG.error( "字符串前缀无法识别" );
            return;
        }
        String prefix = text.substring(0, endIndex);
        Formatter formatter = allFormatters.stream()
                .filter(a -> a.prefix().equals(prefix)).findFirst().orElse(null);
        if (formatter == null) {
            LOG.error( "未识别的字符串前缀" );
            return;
        }
        List<StringRange> build = formatter.build(text);
        String printOut = formatter.printOut(build, text);
        if (printOut == null) return;
        build.stream().filter(a -> a.codeStyle == 1)
                .filter(a -> a.startIndex != a.endIndex)
                .forEach(a -> {
//                    LOG.warn(text.substring(a.startIndex, a.endIndex));
                    TextRange textRange = new TextRange(a.startIndex, a.endIndex);
                    places.addPlace(JavaLanguage.INSTANCE, textRange,
                            "@SuppressWarnings(\"unused\")  class __ZRStringObj {\n  // " + printOut + "\n public Object _str = ", ";\n}" );
//                    places.addPlace(JavaLanguage.INSTANCE, textRange,
//                            null,null );
                });
//        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(host.getProject());
//        @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(printOut, host.getContext());
//        host.add(codeBlockFromText);

    }
}