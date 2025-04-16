package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;

/**
 * @ClassName: NeedRedirectMethod
 * @Author: zwh
 * @Date: 2025/4/16 9:52
 * @Description:
 */
public class NeedRedirectMethod extends RuntimeException {
    public NeedRedirectMethod(Symbol bestSoFar, ExMethodInfo methodInfo) {
        this.bestSoFar = bestSoFar;
        this.exMethodInfo = methodInfo;
    }

    Symbol bestSoFar;
    ExMethodInfo exMethodInfo;
}
