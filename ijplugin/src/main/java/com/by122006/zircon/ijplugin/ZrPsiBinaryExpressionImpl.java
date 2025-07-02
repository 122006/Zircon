package com.by122006.zircon.ijplugin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;

/**
 * @ClassName: ZrPsiBinaryExpressionImpl
 * @Author: 122006
 * @Date: 2025/7/2 9:09
 * @Description:
 */
public class ZrPsiBinaryExpressionImpl extends PsiBinaryExpressionImpl {
    private static final Logger LOG = Logger.getInstance(ZrPsiBinaryExpressionImpl.class);

    Boolean forcePhysical = null;

    public void setForcePhysical(boolean b) {
        forcePhysical = b;
    }

    @Override
    public boolean isPhysical() {
        return forcePhysical != null ? forcePhysical : super.isPhysical();
    }
}
