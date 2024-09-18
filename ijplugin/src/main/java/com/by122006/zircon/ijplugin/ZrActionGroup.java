// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.by122006.zircon.ijplugin;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ZrActionGroup extends ActionGroup implements DumbAware {
    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        List<AnAction> actions = new ArrayList<>();

        if (e != null) {
            if (e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES) != null) {
                actions.add(new Separator(IdeBundle.messagePointer("action.Anonymous.text.highlight")));
                actions.add(new ZrDeReplaceAllAction());
                actions.add(new ZrReplaceAllAction());

            }
        }

        return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
