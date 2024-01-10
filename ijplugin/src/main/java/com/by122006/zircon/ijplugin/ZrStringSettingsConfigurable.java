package com.by122006.zircon.ijplugin;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import javax.swing.JComponent;
import javax.swing.JPanel;

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
        return modified;
    }

    @Override
    public void apply() {
        ZirconSettings settings = ZirconSettings.getInstance();
        settings.ZrStringFoldCharCount = mySettingsComponent.autoFoldJavaCodeLength.getNumber();
        settings.ZrStringFoldEnable = mySettingsComponent.autoFoldJavaCode.isSelected();
    }

    @Override
    public void reset() {
        ZirconSettings settings = ZirconSettings.getInstance();
        mySettingsComponent.autoFoldJavaCodeLength.setNumber(settings.ZrStringFoldCharCount);
        mySettingsComponent.autoFoldJavaCode.setSelected(settings.ZrStringFoldEnable);
        mySettingsComponent.autoFoldJavaCodeLength.setEnabled(settings.ZrStringFoldEnable);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

    public static class AppSettingsComponent {

        private final JPanel myMainPanel;
        private final JBIntSpinner autoFoldJavaCodeLength = new JBIntSpinner(10, 0, 999);
        private final JBCheckBox autoFoldJavaCode = new JBCheckBox("应用自动收缩java代码? ");

        public AppSettingsComponent() {
            myMainPanel = FormBuilder
                    .createFormBuilder()
                    .addComponent(autoFoldJavaCode, 1)
                    .addLabeledComponent(new JBLabel("代码收缩长度:"), autoFoldJavaCodeLength, 3)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();
            autoFoldJavaCode.addActionListener(actionEvent -> autoFoldJavaCodeLength.setEnabled(autoFoldJavaCode.isSelected()));
        }

        public JPanel getPanel() {
            return myMainPanel;
        }

        public JComponent getPreferredFocusedComponent() {
            return autoFoldJavaCode;
        }


    }

}
