package com.by122006.zircon;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.util.Context;

public class ZirconExMethodPlugin extends ZirconPlugin {


    @Override
    public String getName() {
        return "ZrExMethod";
    }

    @Override
    public String getCName() {
        return "拓展方法";
    }


    @Override
    public void startTask(Context context, JavaCompiler compiler, ClassLoader pcl, ClassLoader classLoader) throws Exception {
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$NeedRedirectMethod", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ExMethodInfo", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ZrMethodReferenceLookupHelper", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ZrLookupHelper", pcl, classLoader);
        final Class<?> OOZrAttrClass = reloadClassJavacVersion("com.sun.tools.javac.comp.ZrAttr", pcl, classLoader);
        set(compiler, "attr", getInstance(OOZrAttrClass, context));
        final Class<?> ZrResolve = reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve", pcl, classLoader);
        getInstance(ZrResolve, context);
    }
}
