package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiClassUtil;
import formatter.Formatter;
import formatter.StringRange;
import org.codehaus.groovy.antlr.parser.GroovyLexer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
                            "@SuppressWarnings(\"unused\")  class __ZRStringObj {\n  // " + printOut + "\n public Object _zr_obj_str = ", ";\n}" );
//                    places.addPlace(JavaLanguage.INSTANCE, textRange,
//                            null,null );
                });
//        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(host.getProject());
//        @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(printOut, host.getContext());
//        host.add(codeBlockFromText);
//        PsiSearchHelper.getInstance(host.getProject()).getUseScope()
//        @NotNull ExtensionPoint<LanguageInjector> ep = host.getProject().getExtensionArea().getExtensionPoint(LanguageInjector.EXTENSION_POINT_NAME);
//        PsiReference[] referencesFromProviders = ReferenceProvidersRegistry.getReferencesFromProviders(host);
//        Arrays.stream(referencesFromProviders).forEach(a->{
//            a.getElement();
//        });
    }
}