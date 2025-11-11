package com.by122006.zircon.ijplugin;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import zircon.example.ExArray;
import zircon.example.ExObject;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Objects;
import java.util.function.Function;

import javax.swing.*;

public class ZrStringSettingsConfigurable implements Configurable {
    private AppSettingsComponent mySettingsComponent;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Template String";
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return mySettingsComponent.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mySettingsComponent = new AppSettingsComponent();
        return mySettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        ZirconSettings settings = ZirconSettings.getInstance();
        boolean modified = !Objects.equals(mySettingsComponent.autoFoldJavaCodeLength.getNumber(), settings.ZrStringFoldCharCount);
        modified |= !Objects.equals(mySettingsComponent.autoFoldJavaCode.isSelected(), settings.ZrStringFoldEnable);

        // Check all 4 types
        for (int i = 0; i < StringRangeHighlightSetting.getAllCount(); i++) {
            Color selectedForegroundColorLight = mySettingsComponent.foregroundColorLightFields[i].getSelectedColor();
            Color selectedForegroundColorDark = mySettingsComponent.foregroundColorDarkFields[i].getSelectedColor();
            Color selectedBackgroundColorLight = mySettingsComponent.backgroundColorLightFields[i].getSelectedColor();
            Color selectedBackgroundColorDark = mySettingsComponent.backgroundColorDarkFields[i].getSelectedColor();
            Color selectedEffectColorLight = mySettingsComponent.effectColorLightFields[i].getSelectedColor();
            Color selectedEffectColorDark = mySettingsComponent.effectColorDarkFields[i].getSelectedColor();
            int selectedEffectTypeIndex = mySettingsComponent.effectTypeFields[i].getSelectedIndex();

            modified |= !Objects.equals(
                    selectedForegroundColorLight != null ? selectedForegroundColorLight.getRGB() : null,
                    settings.stringRangeHighlightSettings[i].foregroundColorLight
            );

            modified |= !Objects.equals(
                    selectedForegroundColorDark != null ? selectedForegroundColorDark.getRGB() : null,
                    settings.stringRangeHighlightSettings[i].foregroundColorDark
            );

            modified |= !Objects.equals(
                    selectedBackgroundColorLight != null ? selectedBackgroundColorLight.getRGB() : null,
                    settings.stringRangeHighlightSettings[i].backgroundColorLight
            );

            modified |= !Objects.equals(
                    selectedBackgroundColorDark != null ? selectedBackgroundColorDark.getRGB() : null,
                    settings.stringRangeHighlightSettings[i].backgroundColorDark
            );

            modified |= !Objects.equals(
                    selectedEffectColorLight != null ? selectedEffectColorLight.getRGB() : null,
                    settings.stringRangeHighlightSettings[i].effectColorLight
            );

            modified |= !Objects.equals(
                    selectedEffectColorDark != null ? selectedEffectColorDark.getRGB() : null,
                    settings.stringRangeHighlightSettings[i].effectColorDark
            );
            modified |= mySettingsComponent.fontTypeFields[i].getSelectedIndex() != settings.stringRangeHighlightSettings[i].fontType;
            modified |= selectedEffectTypeIndex != (settings.stringRangeHighlightSettings[i].effectType != null ? settings.stringRangeHighlightSettings[i].effectType : (EFFECT_TYPES.length - 1));
        }

        return modified;
    }

    @Override
    public void apply() {
        ZirconSettings settings = ZirconSettings.getInstance();
        settings.ZrStringFoldCharCount = mySettingsComponent.autoFoldJavaCodeLength.getNumber();
        settings.ZrStringFoldEnable = mySettingsComponent.autoFoldJavaCode.isSelected();

        // Apply all 4 types
        for (int i = 0; i < StringRangeHighlightSetting.getAllCount(); i++) {
            Color selectedForegroundColorLight = mySettingsComponent.foregroundColorLightFields[i].getSelectedColor();
            Color selectedForegroundColorDark = mySettingsComponent.foregroundColorDarkFields[i].getSelectedColor();
            Color selectedBackgroundColorLight = mySettingsComponent.backgroundColorLightFields[i].getSelectedColor();
            Color selectedBackgroundColorDark = mySettingsComponent.backgroundColorDarkFields[i].getSelectedColor();
            Color selectedEffectColorLight = mySettingsComponent.effectColorLightFields[i].getSelectedColor();
            Color selectedEffectColorDark = mySettingsComponent.effectColorDarkFields[i].getSelectedColor();
            int selectedEffectTypeIndex = mySettingsComponent.effectTypeFields[i].getSelectedIndex();

            settings.stringRangeHighlightSettings[i].foregroundColorLight =
                    selectedForegroundColorLight != null ? selectedForegroundColorLight.getRGB() : null;
            settings.stringRangeHighlightSettings[i].foregroundColorDark =
                    selectedForegroundColorDark != null ? selectedForegroundColorDark.getRGB() : null;
            settings.stringRangeHighlightSettings[i].backgroundColorLight =
                    selectedBackgroundColorLight != null ? selectedBackgroundColorLight.getRGB() : null;
            settings.stringRangeHighlightSettings[i].backgroundColorDark =
                    selectedBackgroundColorDark != null ? selectedBackgroundColorDark.getRGB() : null;
            settings.stringRangeHighlightSettings[i].effectColorLight =
                    selectedEffectColorLight != null ? selectedEffectColorLight.getRGB() : null;
            settings.stringRangeHighlightSettings[i].effectColorDark =
                    selectedEffectColorDark != null ? selectedEffectColorDark.getRGB() : null;
            settings.stringRangeHighlightSettings[i].fontType = mySettingsComponent.fontTypeFields[i].getSelectedIndex();
            settings.stringRangeHighlightSettings[i].effectType = selectedEffectTypeIndex;
        }
        ZirconSettings.stringRangeHighlightKeys = null;
    }

    @Override
    public void reset() {
        ZirconSettings settings = ZirconSettings.getInstance();
        mySettingsComponent.autoFoldJavaCodeLength.setNumber(settings.ZrStringFoldCharCount);
        mySettingsComponent.autoFoldJavaCode.setSelected(settings.ZrStringFoldEnable);
        mySettingsComponent.autoFoldJavaCodeLength.setEnabled(settings.ZrStringFoldEnable);

        // Reset all 4 types
        for (int i = 0; i < StringRangeHighlightSetting.getAllCount(); i++) {
            mySettingsComponent.foregroundColorLightFields[i].setSelectedColor(
                    settings.stringRangeHighlightSettings[i].foregroundColorLight != null ?
                            new Color(settings.stringRangeHighlightSettings[i].foregroundColorLight) : null
            );
            mySettingsComponent.foregroundColorDarkFields[i].setSelectedColor(
                    settings.stringRangeHighlightSettings[i].foregroundColorDark != null ?
                            new Color(settings.stringRangeHighlightSettings[i].foregroundColorDark) : null
            );
            mySettingsComponent.backgroundColorLightFields[i].setSelectedColor(
                    settings.stringRangeHighlightSettings[i].backgroundColorLight != null ?
                            new Color(settings.stringRangeHighlightSettings[i].backgroundColorLight) : null
            );
            mySettingsComponent.backgroundColorDarkFields[i].setSelectedColor(
                    settings.stringRangeHighlightSettings[i].backgroundColorDark != null ?
                            new Color(settings.stringRangeHighlightSettings[i].backgroundColorDark) : null
            );
            mySettingsComponent.effectColorLightFields[i].setSelectedColor(
                    settings.stringRangeHighlightSettings[i].effectColorLight != null ?
                            new Color(settings.stringRangeHighlightSettings[i].effectColorLight) : null
            );
            mySettingsComponent.effectColorDarkFields[i].setSelectedColor(
                    settings.stringRangeHighlightSettings[i].effectColorDark != null ?
                            new Color(settings.stringRangeHighlightSettings[i].effectColorDark) : null
            );
            mySettingsComponent.fontTypeFields[i].setSelectedIndex(settings.stringRangeHighlightSettings[i].fontType);
            mySettingsComponent.effectTypeFields[i].setSelectedIndex(
                    settings.stringRangeHighlightSettings[i].effectType != null ?
                            settings.stringRangeHighlightSettings[i].effectType :
                            (EFFECT_TYPES.length - 1)
            );
        }
        ZirconSettings.stringRangeHighlightKeys = null;
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

    private static EffectType[] EFFECT_TYPES = {
            EffectType.LINE_UNDERSCORE,
            EffectType.BOLD_LINE_UNDERSCORE,
            EffectType.WAVE_UNDERSCORE,
            EffectType.STRIKEOUT,
            EffectType.BOXED,
            EffectType.ROUNDED_BOX,
            null
    };

    private static String[] EFFECT_TYPE_NAMES = {
            "LINE_UNDERSCORE",
            "BOLD_LINE_UNDERSCORE",
            "WAVE_UNDERSCORE",
            "STRIKEOUT",
            "BOXED",
            "ROUNDED_BOX",
            "NONE"
    };

    private static int getEffectTypeIndex(EffectType effectType) {
        for (int i = 0; i < EFFECT_TYPES.length; i++) {
            if (Objects.equals(EFFECT_TYPES[i], effectType)) {
                return i;
            }
        }
        return EFFECT_TYPES.length - 1; // 默认为NONE
    }

    private static EffectType getEffectTypeByIndex(int index) {
        if (index >= 0 && index < EFFECT_TYPES.length) {
            return EFFECT_TYPES[index];
        }
        return null;
    }

    private static int getEffectTypeIndex(Integer effectTypeIndex) {
        if (effectTypeIndex != null && effectTypeIndex >= 0 && effectTypeIndex < EFFECT_TYPES.length) {
            return effectTypeIndex;
        }
        return EFFECT_TYPES.length - 1; // 默认为NONE
    }

    public static class AppSettingsComponent {

        private final JPanel myMainPanel;
        private final JBIntSpinner autoFoldJavaCodeLength = new JBIntSpinner(10, 0, 999);
        private final JBCheckBox autoFoldJavaCode = new JBCheckBox("应用自动收缩java代码? ");
        private final JButton resetAllButton = new JButton("重置所有颜色"); // 添加重置所有颜色的按钮
        private final int colorCount = StringRangeHighlightSetting.getAllCount();
        private final com.intellij.ui.ColorPanel[] foregroundColorLightFields = new com.intellij.ui.ColorPanel[colorCount];
        private final com.intellij.ui.ColorPanel[] foregroundColorDarkFields = new com.intellij.ui.ColorPanel[colorCount];
        private final com.intellij.ui.ColorPanel[] backgroundColorLightFields = new com.intellij.ui.ColorPanel[colorCount];
        private final com.intellij.ui.ColorPanel[] backgroundColorDarkFields = new com.intellij.ui.ColorPanel[colorCount];
        private final com.intellij.ui.ColorPanel[] effectColorLightFields = new com.intellij.ui.ColorPanel[colorCount];
        private final com.intellij.ui.ColorPanel[] effectColorDarkFields = new com.intellij.ui.ColorPanel[colorCount];
        private final JComboBox<String>[] fontTypeFields = new JComboBox[colorCount];
        private final JComboBox<String>[] effectTypeFields = new JComboBox[colorCount];
        private final JButton[] clearBackgroundButtons = new JButton[colorCount];
        private final JButton[] clearForegroundButtons = new JButton[colorCount];

        public AppSettingsComponent() {
            // Initialize arrays
            for (int i = 0; i < colorCount; i++) {
                foregroundColorLightFields[i] = new com.intellij.ui.ColorPanel();
                foregroundColorDarkFields[i] = new com.intellij.ui.ColorPanel();
                backgroundColorLightFields[i] = new com.intellij.ui.ColorPanel();
                backgroundColorDarkFields[i] = new com.intellij.ui.ColorPanel();
                effectColorLightFields[i] = new com.intellij.ui.ColorPanel();
                effectColorDarkFields[i] = new com.intellij.ui.ColorPanel();
                fontTypeFields[i] = new JComboBox<>(new String[]{"PLAIN", "BOLD", "ITALIC", "BOLD+ITALIC"});
                effectTypeFields[i] = new JComboBox<>(EFFECT_TYPE_NAMES);
                clearBackgroundButtons[i] = new JButton("清除背景");
                clearForegroundButtons[i] = new JButton("清除前景");
            }

            // Set default colors
            StringRangeHighlightSetting[] defaults = StringRangeHighlightSetting.createDefaults();
            resetToDefaults(defaults);

            FormBuilder builder = FormBuilder.createFormBuilder()
                    .addComponent(autoFoldJavaCode, 1)
                    .addLabeledComponent(new JBLabel("代码收缩长度:"), autoFoldJavaCodeLength, 3)
                    .addSeparator(10)
                    .addComponent(resetAllButton) // 添加重置所有颜色按钮
                    .addSeparator();

            // Add components for each type with compact layout
            for (int i = 0; i < StringRangeHighlightSetting.getAllCount(); i++) {
                builder.addComponent(new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)).with(i, (it, fi) -> {
                    it.add(new JBLabel(StringRangeHighlightSetting.getColorName()[fi] + ":"));
                }));
                builder.addComponent(new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)).with(i, (it, fi) -> {
                    it.add(new JBLabel("前景[明]:"));
                    it.add(foregroundColorLightFields[fi]);
                    it.add(new JBLabel("前景[暗]:"));
                    it.add(foregroundColorDarkFields[fi]);
                    it.add(clearForegroundButtons[fi]);
                    clearForegroundButtons[fi].addActionListener(actionEvent -> {
                        foregroundColorLightFields[fi].setSelectedColor(null);
                        foregroundColorDarkFields[fi].setSelectedColor(null);
                    });
                    it.add(new JBLabel("背景[明]:"));
                    it.add(backgroundColorLightFields[fi]);
                    it.add(new JBLabel("背景[暗]:"));
                    it.add(backgroundColorDarkFields[fi]);
                    it.add(clearBackgroundButtons[fi]);
                    clearBackgroundButtons[fi].addActionListener(actionEvent -> {
                        backgroundColorLightFields[fi].setSelectedColor(null);
                        backgroundColorDarkFields[fi].setSelectedColor(null);
                    });
                }));
                builder.addComponent(new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)).with(i, (it, fi) -> {
                    it.add(new JBLabel("效果色[明]:"));
                    it.add(effectColorLightFields[fi]);
                    it.add(new JBLabel("效果色[暗]:"));
                    it.add(effectColorDarkFields[fi]);
                    it.add(new JBLabel("字体:"));
                    it.add(fontTypeFields[fi]);
                    it.add(new JBLabel("效果类型:"));
                    it.add(effectTypeFields[fi]);
                }));
                if (i < colorCount - 1) { // Add separator between types, but not after the last one
                    builder.addSeparator();
                }
            }

            myMainPanel = builder.addComponentFillVertically(new JPanel(), 0)
                    .getPanel();
            autoFoldJavaCode.addActionListener(actionEvent -> autoFoldJavaCodeLength.setEnabled(autoFoldJavaCode.isSelected()));
            // 为重置所有颜色按钮添加事件监听器
            resetAllButton.addActionListener(e -> {
                resetToDefaults(StringRangeHighlightSetting.createDefaults());
            });
        }

        private void resetToDefaults(StringRangeHighlightSetting[] defaults) {
            Function<Integer, Color> function = (color) -> color == null ? null : new Color(color);
            for (int i = 0; i < colorCount; i++) {
                foregroundColorLightFields[i].setSelectedColor(
                        function.apply(defaults[i].foregroundColorLight)
                );
                foregroundColorDarkFields[i].setSelectedColor(
                        function.apply(defaults[i].foregroundColorDark)
                );
                backgroundColorLightFields[i].setSelectedColor(
                        function.apply(defaults[i].backgroundColorLight)
                );
                backgroundColorDarkFields[i].setSelectedColor(
                        function.apply(defaults[i].backgroundColorDark)
                );
                effectColorLightFields[i].setSelectedColor(
                        function.apply(defaults[i].effectColorLight)
                );
                effectColorDarkFields[i].setSelectedColor(
                        function.apply(defaults[i].effectColorDark)
                );
                fontTypeFields[i].setSelectedIndex(defaults[i].fontType);
                effectTypeFields[i].setSelectedIndex(getEffectTypeIndex(defaults[i].effectType));
            }
        }

        public JPanel getPanel() {
            return myMainPanel;
        }

        public JComponent getPreferredFocusedComponent() {
            return autoFoldJavaCode;
        }
    }
}