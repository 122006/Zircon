package com.by122006.zircon;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ZirconOptionalChainPlugin extends ZirconPlugin {


    @Override
    public String getName() {
        return "ZrOptionalChain";
    }

    @Override
    public String getCName() {
        return "OptionalChain";
    }


    @Override
    public void startTask(Context context, JavaCompiler compiler, ClassLoader pcl, ClassLoader classLoader) throws Exception {
        try {
            reloadClassJavacVersion("com.sun.tools.javac.jvm.ZrGenApplyDepthInfo", pcl, classLoader);
            if (!javaVersionUpper(11)) try {
                reloadClassJavacVersion("com.sun.tools.javac.jvm.ZrGenEx", pcl, classLoader);
            } catch (Exception e) {
                e.printStackTrace();
            }
            final Class<?> ZrGen = reloadClassJavacVersion("com.sun.tools.javac.jvm.ZrGen", pcl, classLoader);
            getInstance(ZrGen, context);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public boolean autoStart() {
        return true;
    }
}
