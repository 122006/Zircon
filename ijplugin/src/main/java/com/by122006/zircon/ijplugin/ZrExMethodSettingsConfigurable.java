package com.by122006.zircon.ijplugin;

import com.intellij.openapi.options.Configurable;
import com.intellij.util.ui.FormBuilder;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;

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
        boolean modified = false;
        return modified;
    }

    @Override
    public void apply() {
        ZirconSettings settings = ZirconSettings.getInstance();
    }

    @Override
    public void reset() {
        ZirconSettings settings = ZirconSettings.getInstance();
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

    public static class AppSettingsComponent {

        private final JPanel myMainPanel;

        public AppSettingsComponent() {
            myMainPanel = FormBuilder
                    .createFormBuilder()
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
