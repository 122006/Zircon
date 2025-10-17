package com.by122006.zircon.ijplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "com.by122006.zircon",
        storages = @Storage("ZirconSettingsPlugin.xml")
)
public class ZirconSettings implements PersistentStateComponent<ZirconSettings> {

    public boolean ZrStringFoldEnable = true;

    public int ZrStringFoldCharCount = 10;
    public boolean ZrMethodAllowUseStaticOnNoStaticMethod = true;

    public boolean enableAll = true;


    public static ZirconSettings getInstance() {
        return ApplicationManager.getApplication().getService(ZirconSettings.class);
    }

    @Nullable
    @Override
    public ZirconSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ZirconSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

}
