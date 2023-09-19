package com.by122006.zircon;

import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

import javax.annotation.processing.Processor;
import java.util.Iterator;

@SuppressWarnings({"unchecked", "rawtypes"})
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
        if (!javaVersionUpper(11))
            reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolveEx", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$NeedRedirectMethod", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ExMethodInfo", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ZrMethodReferenceLookupHelper", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ZrLookupHelper", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve$ZrLookupHelper2", pcl, classLoader);
        final Class<?> OOZrAttrClass = reloadClassJavacVersion("com.sun.tools.javac.comp.ZrAttr", pcl, classLoader);
        final Object myAttr = getInstance(OOZrAttrClass, context);
        set(compiler, "attr", myAttr);
        final Class<?> ZrResolve = reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve", pcl, classLoader);
        getInstance(ZrResolve, context);
    }
}
