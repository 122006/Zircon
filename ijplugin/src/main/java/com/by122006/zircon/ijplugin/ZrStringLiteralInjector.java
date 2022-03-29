package com.by122006.zircon.ijplugin;

import com.intellij.core.CoreJavaCodeStyleManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.StringRange;
import com.sun.tools.javac.parser.ZrStringModel;
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
        final ZrStringModel model = formatter.build(text);
        List<StringRange> build = model.getList();
        String printOut = formatter.printOut(build, text);
        if (printOut == null) return;
        build.stream().filter(a -> a.codeStyle == 1)
                .filter(a -> a.startIndex != a.endIndex)
                .forEach(a -> {
                    TextRange textRange = new TextRange(a.startIndex, a.endIndex);
                    places.addPlace(JavaLanguage.INSTANCE, textRange,
                            "@SuppressWarnings(\"unused\")  class __ZRStringObj {\n  // " + printOut + "\n public Object _zr_obj_str = ", ";\n}" );
                });

    }

}