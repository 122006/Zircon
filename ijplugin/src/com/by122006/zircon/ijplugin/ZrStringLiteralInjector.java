package com.by122006.zircon.ijplugin;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;

public class ZrStringLiteralInjector implements LanguageInjector {
    private static final Logger LOG = Logger.getInstance(ZrStringLiteralInjector.class.getName());

    @Override
    public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces places) {
        if (!(host instanceof PsiLiteralExpressionImpl)) return;
        if (!(host.getLanguage().isKindOf(JavaLanguage.INSTANCE))) return;
        PsiLiteralExpressionImpl impl = (PsiLiteralExpressionImpl) host;
        if (!(impl.getLiteralElementType() == JavaTokenType.STRING_LITERAL)) return;
        String text = impl.getCanonicalText();
        if (!(text.startsWith("f") || text.startsWith("$"))) return;
        GroupStringRange.build(text).stream().filter(a -> a.isJavaCode)
                .filter(a->a.startIndex!=a.endIndex)
                .forEach(a -> {
//                    LOG.warn(text.substring(a.startIndex, a.endIndex));
                    TextRange textRange = new TextRange(a.startIndex, a.endIndex);
                    places.addPlace(JavaLanguage.INSTANCE, textRange, "class __ZRStringObj {\n  Object _str = ", ";\n}\n");
                });
    }

}
