package com.by122006.zircon.ijplugin;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
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
        boolean modified = !Objects.equals(mySettingsComponent.allowUseStaticOnNoStaticMethod.isSelected(), settings.allowUseStaticOnNoStaticMethod);
        return modified;
    }

    @Override
    public void apply() {
        ZirconSettings settings = ZirconSettings.getInstance();
        settings.allowUseStaticOnNoStaticMethod = mySettingsComponent.allowUseStaticOnNoStaticMethod.isSelected();
    }

    @Override
    public void reset() {
        ZirconSettings settings = ZirconSettings.getInstance();
        mySettingsComponent.allowUseStaticOnNoStaticMethod.setSelected(settings.allowUseStaticOnNoStaticMethod);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

    public static class AppSettingsComponent {

        private final JPanel myMainPanel;
        private final JBCheckBox allowUseStaticOnNoStaticMethod = new JBCheckBox("是否允许在实例上调用静态方法");


        public AppSettingsComponent() {
            myMainPanel = FormBuilder
                    .createFormBuilder()
                    .addComponent(allowUseStaticOnNoStaticMethod, 1)
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
