package com.by122006.zircon.ijplugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class ZrStartupActivity implements StartupActivity {
    private static final Logger LOG = Logger.getInstance("#" + ZrStartupActivity.class.getName());

    @Override
    public void runActivity(@NotNull Project project) {
        LOG.info("runActivity");
//        Util.setJavaElementConstructor(JavaElementType.POLYADIC_EXPRESSION, ZrPsiPolyadicExpressionImpl::new);
    }
}