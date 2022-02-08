package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class ZrHighlightErrorFilter implements HighlightInfoFilter {
    private static final Pattern TOOLTIP_REGEXP = Pattern.compile(".*'.*' expected.*");

    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
        return !isZrHighlight(highlightInfo) || (file!=null&&file.getLanguage().isKindOf(JavaLanguage.INSTANCE));
    }

    private boolean isZrHighlight(HighlightInfo highlightInfo) {
        return highlightInfo.getToolTip() != null && TOOLTIP_REGEXP.matcher(highlightInfo.getToolTip()).matches();
    }
}
