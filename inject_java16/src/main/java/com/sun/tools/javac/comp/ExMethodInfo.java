package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.List;

import java.util.Objects;

/**
 * @ClassName: ExMethodInfo
 * @Author: 122006
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExMethodInfo that = (ExMethodInfo) o;

        if (isStatic != that.isStatic) return false;
        if (cover != that.cover) return false;
        if (siteCopyByClassHeadArgMethod != that.siteCopyByClassHeadArgMethod) return false;
        if (!Objects.equals(methodSymbol, that.methodSymbol)) return false;
        if (!Objects.equals(targetClass, that.targetClass)) return false;
        return Objects.equals(filterAnnotation, that.filterAnnotation);
    }

    @Override
    public int hashCode() {
        int result = methodSymbol != null ? methodSymbol.hashCode() : 0;
        result = 31 * result + (isStatic ? 1 : 0);
        result = 31 * result + (cover ? 1 : 0);
        result = 31 * result + (targetClass != null ? targetClass.hashCode() : 0);
        result = 31 * result + (filterAnnotation != null ? filterAnnotation.hashCode() : 0);
        result = 31 * result + (siteCopyByClassHeadArgMethod ? 1 : 0);
        return result;
    }
}
