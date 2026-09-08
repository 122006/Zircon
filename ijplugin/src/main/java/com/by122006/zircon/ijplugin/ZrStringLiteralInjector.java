package com.by122006.zircon.ijplugin;

import com.by122006.zircon.ijplugin.util.ZrPluginUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.StringRange;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExCollection;
import zircon.example.ExObject;

import java.util.List;

public class ZrStringLiteralInjector implements LanguageInjector {
    private static final Logger LOG = Logger.getInstance(ZrStringLiteralInjector.class.getName());

    @Override
    public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces places) {
        if (!ZrPluginUtil.hasZrPlugin(host)) return;
        if (!(host instanceof PsiLiteralExpression)) return;
        if (!(host.getLanguage().isKindOf(JavaLanguage.INSTANCE))) return;
        if (InjectedLanguageManager.getInstance(host.getProject()).isInjectedFragment(host.getContainingFile())) return;
        PsiLiteralExpression literal = (PsiLiteralExpression) host;
        ASTNode literalToken = literal.getNode().getFirstChildNode();
        if (literalToken == null || literalToken.getElementType() != JavaTokenType.STRING_LITERAL) return;
        String text = literal.getText();
        if (text.startsWith("\"")) return;
        List<Formatter> allFormatters = Formatter.getAllFormatters();
        int endIndex = text.indexOf("\"");
        if (endIndex == -1) {
            LOG.error("字符串前缀无法识别");
            return;
        }
        String prefix = text.substring(0, endIndex);
        Formatter formatter = allFormatters.find(a -> a.prefix().equals(prefix));
        if (formatter == null) {
            LOG.error("未识别的字符串前缀");
            return;
        }
        final ZrStringModel model = formatter.build(text);
        List<StringRange> build = model.getList();
        if (!(literal.getContainingFile() instanceof PsiJavaFile)) return;
        final PsiJavaFile containingFile = (PsiJavaFile) literal.getContainingFile();
        final PsiImportList importList = containingFile.getImportList();
        StringBuilder prefixBuilder = new StringBuilder();
        if (!containingFile.getPackageName().isEmpty()) {
            prefixBuilder.append("package ")
                    .append(containingFile.getPackageName())
                    .append(";\n");
        }
        if (importList != null) {
            prefixBuilder.append(importList.getText()).append('\n');
        }
        prefixBuilder.append(
                "@SuppressWarnings(\"unused\") class __ZRStringObj {\n"
                        + "  Object _zr_obj_str = ");
        String addText = prefixBuilder.toString();
        for (StringRange a : build) {
            if (a.codeStyle == 1) {
                if (a.startIndex != a.endIndex) {
                    TextRange textRange = new TextRange(a.startIndex, a.endIndex);
                    if (textRange.getLength() == 0) continue;
//                    LOG.info("addPlace "+a.stringVal);
                    places.addPlace(JavaLanguage.INSTANCE, textRange,
                            addText, ";\n}");

                }
            }
        }

    }

}
