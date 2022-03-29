package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInsight.daemon.impl.CheckLevelHighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.quickfix.AdjustFunctionContextFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.internal.StringUtil;

import java.util.Collection;
import java.util.stream.Collectors;

public class ZrCheckLevelHighlightInfoHolder extends CheckLevelHighlightInfoHolder {
    HighlightInfoHolder holder;
    int startIndex;

    public ZrCheckLevelHighlightInfoHolder(@NotNull PsiFile file, @NotNull HighlightInfoHolder holder, int startIndex) {
        super(file, holder);
        this.holder = holder;
        this.startIndex = startIndex;
    }

    PsiElement psiElement;

    public void setPsiElement(PsiElement psiElement) {
        this.psiElement = psiElement;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends HighlightInfo> highlightInfos) {
        highlightInfos.forEach(this::add);
        return true;
    }

    @Override
    public boolean add(@Nullable HighlightInfo info) {
        if (info == null) return false;
        if (info.type == HighlightInfoType.UNHANDLED_EXCEPTION) {
            final ExceptionUtil.HandlePlace handlePlace = ExceptionUtil.getHandlePlace(psiElement.getParent().getContext(), ExceptionUtil.getOwnUnhandledExceptions(psiElement).get(0), null);
            final boolean b = handlePlace != ExceptionUtil.HandlePlace.UNHANDLED;
            if (b) {
                if (handlePlace instanceof ExceptionUtil.HandlePlace.TryCatch) {
//                    ((ExceptionUtil.HandlePlace.TryCatch) handlePlace).getTryStatement().;
                }
                return false;
            }
        }

        final TextRange rangeInElement = TextRange.create(startIndex + info.getStartOffset(), startIndex + info.getEndOffset());
        final HighlightInfo.Builder severity = HighlightInfo
                .newHighlightInfo(info.type)
                .range(psiElement, rangeInElement)
                .description(info.getDescription() == null ? "" : info.getDescription())
                .severity(info.getSeverity());
        if (info.getDescription() != null) severity.escapedToolTip(info.getDescription());
        final HighlightInfo newInfo = severity.create();
        if (newInfo == null) return false;
        if (info.quickFixActionRanges != null)
            info.quickFixActionRanges.forEach(markerPair -> {
                final HighlightInfo.IntentionActionDescriptor first = markerPair.getFirst();
                final IntentionAction action = first.getAction();
                if (action.getFamilyName().length() == 0) return;
                IntentionAction actionShow = new IntentionAction() {
                    PsiElement element;

                    public IntentionAction setElement(PsiElement element) {
                        this.element = element;
                        return this;
                    }

                    @Override
                    public @IntentionName @NotNull String getText() {
                        try {
                            return action.getText();
                        } catch (Exception e) {
                            return "";
                        }
                    }

                    @Override
                    public @NotNull @IntentionFamilyName String getFamilyName() {
                        return action.getFamilyName();
                    }

                    @Override
                    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
                        return getText().length() != 0;
                    }

                    @Override
                    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
                        action.invoke(project, editor, element.getContainingFile());
                    }

                    @Override
                    public boolean startInWriteAction() {
                        return false;
                    }
                }.setElement(psiElement);
                newInfo.registerFix(actionShow, first.getOptions(psiElement, null), first.getAction().getFamilyName(), markerPair.getSecond().shiftRight(startIndex), null);

            });
        holder.add(newInfo);
        return true;
    }
}
