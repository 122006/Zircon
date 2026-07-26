package org.eclipse.jdt.core.search;

import com.by122006.zircon.vsplugin.ZirconCore;
import org.eclipse.core.runtime.CoreException;

/**
 * Loaded into JDT Core's bundle class loader at runtime. ZirconCore itself is
 * bootstrap-visible, but cannot directly extend an OSGi-owned JDT class.
 */
public final class ZirconExMethodSearchRequestor extends SearchRequestor {
    private final long requestId;

    public ZirconExMethodSearchRequestor(long requestId) {
        this.requestId = requestId;
    }

    @Override
    public void acceptSearchMatch(SearchMatch match) throws CoreException {
        ZirconCore.acceptJdtExMethodSearchMatch(requestId, match);
    }
}
