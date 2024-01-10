package com.by122006.zircon.ijplugin;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import javax.swing.JComponent;
import javax.swing.JPanel;

public class ZrRootSettingsConfigurable implements Configurable {

    private AppSettingsComponent mySettingsComponent;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Zircon";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
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
        boolean modified = !Objects.equals(mySettingsComponent.enableAll.isSelected(), settings.enableAll);
        return modified;
    }

    @Override
    public void apply() {
        ZirconSettings settings = ZirconSettings.getInstance();
        settings.enableAll = mySettingsComponent.enableAll.isSelected();
    }

    @Override
    public void reset() {
        ZirconSettings settings = ZirconSettings.getInstance();
        mySettingsComponent.enableAll.setSelected(settings.enableAll);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

    public static class AppSettingsComponent {

        private final JPanel myMainPanel;

        private final JBCheckBox enableAll = new JBCheckBox("插件启用 ");

        public AppSettingsComponent() {
            myMainPanel = FormBuilder.createFormBuilder()
                                     .addTooltip("已支持的语法特性：")
                                     .addTooltip("1. 全局拓展方法\n" + "自由拓展已有代码的实现方法。可以实现诸如顶级方法、方法替换等功能")
                                     .addTooltip("2. 内插模板字符串\n" + "字符串插值功能构建在复合格式设置功能的基础之上，提供更具有可读性、更方便的语法，用于将表达式结果包括到结果字符串。")
                                     .addComponent(enableAll)
                                     .addComponentFillVertically(new JPanel(), 0)
                                     .getPanel();
        }

        public JPanel getPanel() {
            return myMainPanel;
        }

        public JComponent getPreferredFocusedComponent() {
            return myMainPanel;
        }


    }

}
