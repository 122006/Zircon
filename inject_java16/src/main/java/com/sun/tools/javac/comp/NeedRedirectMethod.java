package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;

/**
 * @ClassName: NeedRedirectMethod
 * @Author: 122006
 * @Date: 2025/4/16 9:52
 * @Description:
 */
public class NeedRedirectMethod extends RuntimeException {
    public NeedRedirectMethod(Symbol bestSoFar, ExMethodInfo methodInfo, Type site) {
        this.bestSoFar = bestSoFar;
        this.exMethodInfo = methodInfo;
        this.site = site;
    }

    Symbol bestSoFar;
    ExMethodInfo exMethodInfo;
    Type site;
}
