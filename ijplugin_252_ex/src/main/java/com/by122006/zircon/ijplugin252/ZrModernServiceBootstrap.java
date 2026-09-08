package com.by122006.zircon.ijplugin252;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

import java.lang.reflect.Method;

/**
 * Installs the IDEA 253+ Syntax API service adapter.
 *
 * <p>The content-module dependency normally keeps this class out of IDEA
 * 212-252. The explicit baseline guard is intentional defense in depth: if an
 * older IDE exposes similarly named Syntax API modules, it must still keep the
 * historical {@code ZrClassLoaderHelper} parser as its only transformation
 * path.</p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class ZrModernServiceBootstrap
        implements ApplicationInitializedListener {
    private static final Object INSTALL_LOCK = new Object();
    private static volatile boolean installed;

    public ZrModernServiceBootstrap() {
    }

    @Override
    public Object execute(Continuation<? super Unit> continuation) {
        install();
        return Unit.INSTANCE;
    }

    public static void install() {
        if (ApplicationInfo.getInstance().getBuild().getBaselineVersion() < 253) {
            return;
        }

        if (installed) {
            return;
        }

        synchronized (INSTALL_LOCK) {
            if (installed) {
                return;
            }

            PsiSyntaxBuilderFactory current =
                    PsiSyntaxBuilderFactory.getInstance();
            if (!(current instanceof ZrPsiSyntaxBuilderFactory)) {
                Application application =
                        ApplicationManager.getApplication();
                replaceRegularServiceInstance(
                        application,
                        PsiSyntaxBuilderFactory.class,
                        new ZrPsiSyntaxBuilderFactory());
            }

            PsiSyntaxBuilderFactory installedFactory =
                    PsiSyntaxBuilderFactory.getInstance();
            if (!(installedFactory instanceof ZrPsiSyntaxBuilderFactory)) {
                throw new IllegalStateException(
                        "Zircon PsiSyntaxBuilderFactory was not installed");
            }
            installed = true;
        }
    }

    /**
     * IDEA 253 introduced this service replacement API. Resolve it only after
     * the baseline guard so the one shipping JAR has no hard method reference
     * that would make Plugin Verifier reject IDEA 212-252.
     */
    private static void replaceRegularServiceInstance(
            Application application,
            Class<?> serviceClass,
            Object serviceInstance) {
        try {
            Class<?> componentManagerEx = Class.forName(
                    "com.intellij.openapi.components.ComponentManagerEx",
                    false,
                    ZrModernServiceBootstrap.class.getClassLoader());
            if (!componentManagerEx.isInstance(application)) {
                throw new IllegalStateException(
                        "IDE application does not expose ComponentManagerEx");
            }
            Method replace = componentManagerEx.getMethod(
                    "replaceRegularServiceInstance",
                    Class.class,
                    Object.class);
            replace.invoke(application, serviceClass, serviceInstance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot install Zircon PsiSyntaxBuilderFactory", e);
        }
    }
}
