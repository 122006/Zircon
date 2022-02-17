package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZrHighlightInfoFilter implements HighlightInfoFilter {
    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
        if (file == null || file.getLanguage() != JavaLanguage.INSTANCE) return true;
        if (highlightInfo.getSeverity().getName().equalsIgnoreCase("INJECTED_FRAGMENT"))
            return false;
        else return true;
    }
}
