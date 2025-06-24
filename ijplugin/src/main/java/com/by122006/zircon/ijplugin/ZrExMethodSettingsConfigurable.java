package com.by122006.zircon.ijplugin;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class ZrExMethodSettingsConfigurable implements Configurable {

    private AppSettingsComponent mySettingsComponent;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Extension Method";
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
        boolean modified = !Objects.equals(mySettingsComponent.allowUseStaticOnNoStaticMethod.isSelected(), settings.ZrMethodAllowUseStaticOnNoStaticMethod);
        modified |= !Objects.equals(mySettingsComponent.allowZrMethodAllowAutoFind.isSelected(), settings.ZrMethodAllowAutoFind);
        return modified;
    }

    @Override
    public void apply() {
        ZirconSettings settings = ZirconSettings.getInstance();
        settings.ZrMethodAllowUseStaticOnNoStaticMethod = mySettingsComponent.allowUseStaticOnNoStaticMethod.isSelected();
        settings.ZrMethodAllowAutoFind = mySettingsComponent.allowZrMethodAllowAutoFind.isSelected();

    }

    @Override
    public void reset() {
        ZirconSettings settings = ZirconSettings.getInstance();
        mySettingsComponent.allowUseStaticOnNoStaticMethod.setSelected(settings.ZrMethodAllowUseStaticOnNoStaticMethod);
        mySettingsComponent.allowZrMethodAllowAutoFind.setSelected(settings.ZrMethodAllowAutoFind);

    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

    public static class AppSettingsComponent {

        private final JPanel myMainPanel;
        private final JBCheckBox allowUseStaticOnNoStaticMethod = new JBCheckBox("是否允许在实例上调用静态方法");
        private final JBCheckBox allowZrMethodAllowAutoFind = new JBCheckBox("快速自动注册拓展方法");

        public AppSettingsComponent() {
            myMainPanel = FormBuilder
                    .createFormBuilder()
                    .addComponent(allowUseStaticOnNoStaticMethod, 1)
                    .addComponent(allowZrMethodAllowAutoFind, 1)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();
            allowZrMethodAllowAutoFind.setToolTipText("启用后，会跟随每次修改，快速自动注册拓展方法。如果禁用，仅在项目文件变动时重新搜寻");
        }

        public JPanel getPanel() {
            return myMainPanel;
        }

        public JComponent getPreferredFocusedComponent() {
            return myMainPanel;
        }


    }

}
