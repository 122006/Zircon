package com.by122006.zircon.ijplugin;

import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Binary-expression PSI node retained for the parser adapters used by IDEA
 * versions before the public Syntax API.
 */
public class ZrPsiBinaryExpressionImpl extends PsiBinaryExpressionImpl {
    private Boolean forcePhysical;

    public ZrPsiBinaryExpressionImpl() {
        this(JavaElementType.BINARY_EXPRESSION);
    }

    public ZrPsiBinaryExpressionImpl(@NotNull IElementType elementType) {
        super(elementType);
    }

    public void setForcePhysical(boolean forcePhysical) {
        this.forcePhysical = forcePhysical;
    }

    @Override
    public boolean isPhysical() {
        return forcePhysical != null ? forcePhysical : super.isPhysical();
    }
}
