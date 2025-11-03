package com.by122006.zircon.ijplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExArray;

@State(
        name = "com.by122006.zircon",
        storages = @Storage("ZirconSettingsPlugin.xml")
)
public class ZirconSettings implements PersistentStateComponent<ZirconSettings> {

    public boolean ZrStringFoldEnable = true;

    public int ZrStringFoldCharCount = 10;
    public boolean ZrMethodAllowUseStaticOnNoStaticMethod = true;

    public boolean enableAll = true;

    public StringRangeHighlightSetting[] stringRangeHighlightSettings = StringRangeHighlightSetting.createDefaults();
    public static TextAttributesKey[] stringRangeHighlightKeys = new TextAttributesKey[StringRangeHighlightSetting.getAllCount()];

    public static ZirconSettings getInstance() {
        return ApplicationManager.getApplication().getService(ZirconSettings.class);
    }

    public TextAttributesKey getStringRangeHighlightKey(int index) {
        if (stringRangeHighlightKeys == null) {
            stringRangeHighlightKeys = new TextAttributesKey[stringRangeHighlightSettings.length];
        }

        if (stringRangeHighlightKeys[index] != null) {
            return stringRangeHighlightKeys[index];
        }
        return stringRangeHighlightKeys[index] = stringRangeHighlightSettings[index].toTextAttributesKey(index);
    }

    @Nullable
    @Override
    public ZirconSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ZirconSettings state) {
        XmlSerializerUtil.copyBean(state, this);
        if (stringRangeHighlightSettings.length != StringRangeHighlightSetting.getAllCount()) {
            StringRangeHighlightSetting[] _stringRangeHighlightSettings = StringRangeHighlightSetting.createDefaults();
            for (int i = 0; i < Math.min(stringRangeHighlightSettings.length, _stringRangeHighlightSettings.length); i++) {
                _stringRangeHighlightSettings[i] = stringRangeHighlightSettings[i];
            }
            stringRangeHighlightSettings = _stringRangeHighlightSettings;
        }
    }

}