package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.impl.CheckLevelHighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZrCheckLevelHighlightInfoHolder extends CheckLevelHighlightInfoHolder {
    HighlightInfoHolder holder;
    int startIndex;

    public ZrCheckLevelHighlightInfoHolder(@NotNull PsiFile file, @NotNull HighlightInfoHolder holder, int startIndex) {
        super(file, holder);
        this.holder = holder;
        this.startIndex = startIndex;
    }

    @Override
    public boolean add(@Nullable HighlightInfo info) {
        if (info == null) return false;
        final HighlightInfo.Builder severity = HighlightInfo
                .newHighlightInfo(info.type)
                .range(TextRange.create(startIndex + info.getStartOffset(), startIndex + info.getEndOffset()))
                .description(info.getDescription() == null ? "" : info.getDescription())
                .severity(info.getSeverity());
        if (info.getDescription()!=null) severity.escapedToolTip(info.getDescription());
        final HighlightInfo newInfo = severity
                .create();
        holder.add(newInfo);
        return true;
    }
}
