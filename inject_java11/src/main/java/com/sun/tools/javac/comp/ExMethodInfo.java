package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.List;

/**
 * @ClassName: ExMethodInfo
 * @Author: zwh
 * @Date: 2025/4/16 9:52
 * @Description:
 */
public class ExMethodInfo {
    Symbol.MethodSymbol methodSymbol;
    boolean isStatic;
    boolean cover;
    List<Type.ClassType> targetClass;
    List<Type.ClassType> filterAnnotation;
    boolean siteCopyByClassHeadArgMethod = false;

    @Override
    public String toString() {
        return "ExMethodInfo{" +
                "methodSymbol=" + methodSymbol +
                ", isStatic=" + isStatic +
                ", cover=" + cover +
                ", targetClass=" + targetClass +
                ", filterAnnotation=" + filterAnnotation +
                ", siteCopyByClassHeadArgMethod=" + siteCopyByClassHeadArgMethod +
                '}';
    }

    public ExMethodInfo(Symbol.MethodSymbol methodSymbol, boolean isStatic, boolean cover, List<Type.ClassType> targetClass, List<Type.ClassType> filterAnnotation) {
        this.methodSymbol = methodSymbol;
        this.isStatic = isStatic;
        this.cover = cover;
        this.targetClass = targetClass;
        this.filterAnnotation = filterAnnotation;
    }
}
