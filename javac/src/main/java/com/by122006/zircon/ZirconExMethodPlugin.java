package com.by122006.zircon;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ZirconExMethodPlugin extends ZirconPlugin {


    @Override
    public String getName() {
        return "ZrExMethod";
    }

    @Override
    public String getCName() {
        return "ExMethod";
    }


    @Override
    public void startTask(Context context, JavaCompiler compiler, ClassLoader pcl, ClassLoader classLoader) throws Exception {
        if (!javaVersionUpper(11)) {
            reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolveEx", pcl, classLoader);
        }
        reloadClass("com.sun.tools.javac.parser.CompareSameMethod", pcl, classLoader);
        reloadClass("com.sun.tools.javac.parser.CompareSameMethod$MethodInfo", pcl, classLoader);
        reloadClass("com.sun.tools.javac.parser.CompareSameMethod$CompareEnv", pcl, classLoader);

        reloadClassJavacVersion("com.sun.tools.javac.comp.NeedRedirectMethod", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.NeedReplaceLambda", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ExMethodInfo", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrMethodReferenceLookupHelper", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrLookupHelper", pcl, classLoader);
        reloadClassJavacVersion("com.sun.tools.javac.comp.ZrLookupHelper2", pcl, classLoader);


        final Class<?> OOZrAttrClass = reloadClassJavacVersion("com.sun.tools.javac.comp.ZrAttr", pcl, classLoader);
        final Object myAttr = getInstance(OOZrAttrClass, context);
        set(compiler, "attr", myAttr);


        final Class<?> ZrResolve = reloadClassJavacVersion("com.sun.tools.javac.comp.ZrResolve", pcl, classLoader);
        getInstance(ZrResolve, context);


    }

    public boolean autoStart() {
        return true;
    }
}
