package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.StringRange;
import com.sun.tools.javac.parser.ZrStringModel;
import org.jetbrains.annotations.NotNull;
import zircon.example.ExObject;

import java.util.List;

public class ZrStringLiteralInjector implements LanguageInjector {
    private static final Logger LOG = Logger.getInstance(ZrStringLiteralInjector.class.getName());

    @Override
    public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces places) {
        if (!ZrPluginUtil.hasZrPlugin(host)) return;
        if (!(host instanceof PsiLiteralExpressionImpl)) return;
        if (!(host.getLanguage().isKindOf(JavaLanguage.INSTANCE))) return;
        if (InjectedLanguageManager.getInstance(host.getProject()).isInjectedFragment(host.getContainingFile())) return;
        PsiLiteralExpressionImpl impl = (PsiLiteralExpressionImpl) host;
        if (!(impl.getLiteralElementType() == JavaTokenType.STRING_LITERAL)) return;
        String text = impl.getCanonicalText();
        if (text.startsWith("\"")) return;
        List<Formatter> allFormatters = Formatter.getAllFormatters();
        int endIndex = text.indexOf("\"");
        if (endIndex == -1) {
            LOG.error("字符串前缀无法识别");
            return;
        }
        String prefix = text.substring(0, endIndex);
        Formatter formatter = allFormatters.stream()
                .filter(a -> a.prefix().equals(prefix)).findFirst().orElse(null);
        if (formatter == null) {
            LOG.error("未识别的字符串前缀");
            return;
        }
        final ZrStringModel model = formatter.build(text);
        List<StringRange> build = model.getList();
        String printOut = formatter.printOut(build, text);
        if (printOut == null) return;
        final PsiJavaFile containingFile = (PsiJavaFile) impl.getContainingFile();
        final PsiImportList importList = containingFile.getImportList();
        String addText = "";
        addText += "package " + containingFile.getPackageName() + ";\n";
        if (importList != null) {
            addText += importList.getText();
            addText += "\n";
        }
        addText+="@SuppressWarnings(\"unused\")  class __ZRStringObj {\n  // " + printOut + "\n public Object _zr_obj_str = ";
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
