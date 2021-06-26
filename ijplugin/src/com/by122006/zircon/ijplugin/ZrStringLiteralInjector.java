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
        if (!(text.startsWith( "f") || text.startsWith( "$"))) return;
        List<GroupStringRange.StringRange> build = GroupStringRange.build(text);
        StringBuilder stringBuilder = new StringBuilder();
        if (text.startsWith( "f")) {
            stringBuilder.append( "String.format(\"");
            stringBuilder.append(GroupStringRange.map2FormatString(text, build));
            stringBuilder.append( "\"");
            for (GroupStringRange.StringRange a : build) {
                if (a.codeStyle != 0 && a.codeStyle != 1) continue;
                String str = text.substring(a.startIndex, a.endIndex);
                if (a.codeStyle == 1) {
                    stringBuilder.append( ",");
                    String toStr = str.replaceAll( "(^|[^\\\\])'([^']+?[^\\\\'])?'" , "$1\"$2\"")
                            .replaceAll( "\\\\?([a-z0-9\"']{1})" , "$1")
                            .replace( "\\\\" , "\\");
                    stringBuilder.append(toStr);
                }
            }
            stringBuilder.append( ")");
        } else {
            if (build.size() > 0) {
                stringBuilder.append( "(");
                if (build.get(0).codeStyle == 1) {
                    stringBuilder.append( "String.valueOf(");
                    stringBuilder.append(text.substring(build.get(0).startIndex, build.get(0).endIndex));
                    stringBuilder.append( ")");
                } else if (build.get(0).codeStyle == 0) {
                    stringBuilder.append( "\"");
                    stringBuilder.append(text.substring(build.get(0).startIndex, build.get(0).endIndex));
                    stringBuilder.append( "\"");
                } else {
                    stringBuilder.append("[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]");
                }
                for (int i = 1; i < build.size(); i++) {
                    stringBuilder.append( "+");
                    GroupStringRange.StringRange stringRange = build.get(i);
                    if (stringRange.codeStyle == 1) {
                        stringBuilder.append( "(");
                        stringBuilder.append(text.substring(stringRange.startIndex, stringRange.endIndex));
                        stringBuilder.append( ")");
                    } else if (stringRange.codeStyle == 0) {
                        stringBuilder.append( "\"");
                        stringBuilder.append(text.substring(stringRange.startIndex, stringRange.endIndex));
                        stringBuilder.append( "\"");
                    }else {
                        stringBuilder.append("[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]");
                    }
                }
                stringBuilder.append( ")");
            }
        }

        build.stream().filter(a -> a.codeStyle == 1)
                .filter(a -> a.startIndex != a.endIndex)
                .forEach(a -> {
//                    LOG.warn(text.substring(a.startIndex, a.endIndex));
                    TextRange textRange = new TextRange(a.startIndex, a.endIndex);
                    places.addPlace(JavaLanguage.INSTANCE, textRange, "class __ZRStringObj {\n  // " + stringBuilder + "\n  Object _str = " , ";\n}\n");
                });
    }

}
