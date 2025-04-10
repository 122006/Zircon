package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.CompareSameMethod;
import com.sun.tools.javac.util.List;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class ZrResolveEx {

    public static boolean equalsIgnoreMetadata(Type t1, Type t2) {
        return t1.baseType().equals(t2.baseType());
    }
    static Symbol selectBestFromList(ZrResolve zrResolve, List<ZrResolve.ExMethodInfo> methodSymbolList, Env<AttrContext> env, Type site, List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar, boolean allowBoxing, boolean useVarargs, boolean operator, boolean memberReference) {
        if (bestSoFar instanceof Resolve.ResolveError && !(bestSoFar instanceof Resolve.AmbiguityError)) bestSoFar = zrResolve.methodNotFound;

        java.util.List<List> newResult = new ArrayList<>();
        Symbol lastMethodSymbol = zrResolve.methodNotFound;
        java.util.List<ZrResolve.ExMethodInfo> sortList = new ArrayList<>(methodSymbolList);
        sortList.sort((a1, a2) -> {
            final CompareSameMethod.MethodInfo<ZrResolve.ExMethodInfo> info1 = CompareSameMethod.MethodInfo.create(a1.methodSymbol.owner
                    .getQualifiedName().toString(), a1);
            final CompareSameMethod.MethodInfo<ZrResolve.ExMethodInfo> info2 = CompareSameMethod.MethodInfo.create(a2.methodSymbol.owner
                    .getQualifiedName().toString(), a2);
            return CompareSameMethod.compare(CompareSameMethod.CompareEnv.create(env.enclClass.sym
                    .getQualifiedName()
                    .toString()), info1, info2);
        });
        sortList=sortList.stream().filter(a -> {
            final List<Attribute.Class> filterAnnotation = a.filterAnnotation;
            if (filterAnnotation == null || filterAnnotation.isEmpty()) return true;
            for (Attribute.Class aClass : filterAnnotation) {
                boolean any = false;
                for (Attribute.Compound attribute : site.tsym.getAnnotationMirrors()) {
                    if (equalsIgnoreMetadata(attribute.type,aClass.classType)) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());
        exInfo:
        for (ZrResolve.ExMethodInfo methodInfo : sortList) {
            List<Type> newArgTypes = argtypes;
            if (!methodInfo.isStatic) {
                Type type = methodInfo.methodSymbol.getParameters().head.type.baseType();
                type = zrResolve.types.erasure(type);

                if (!memberReference) newArgTypes = newArgTypes.prepend(site);
                final Symbol best = zrResolve.selectBest(env, type, newArgTypes, typeargtypes, methodInfo.methodSymbol, zrResolve.methodNotFound, allowBoxing, useVarargs, operator);
                if (best == methodInfo.methodSymbol && best instanceof Symbol.MethodSymbol) {
                    lastMethodSymbol = methodInfo.methodSymbol;
                    newResult.add(List.of(type, methodInfo));
                    continue;
                } else {
                    lastMethodSymbol = best;
                }
            } else {
                for (Attribute.Class clazz : methodInfo.targetClass) {
                    final Type type = clazz.classType.baseType();
                    final boolean sameType = type.equals(site);
                    if (sameType || zrResolve.types.isAssignable(site, type)) {
                        final Symbol best = zrResolve.selectBest(env, type, argtypes, typeargtypes, methodInfo.methodSymbol, zrResolve.methodNotFound, allowBoxing, useVarargs, operator);
                        if (best == methodInfo.methodSymbol && best instanceof Symbol.MethodSymbol) {
                            lastMethodSymbol = methodInfo.methodSymbol;
                            newResult.add(List.of(type, methodInfo));
                            continue exInfo;
                        } else {
                            lastMethodSymbol = best;
                        }
                    }
                }
            }

        }
        if (newResult.isEmpty()) {
            return bestSoFar instanceof Symbol.MethodSymbol ? bestSoFar : lastMethodSymbol;
        }
        List<ZrResolve.ExMethodInfo> finalMethodSymbol = List.nil();
        final java.util.List<List> coverList = newResult.stream().filter(a -> ((ZrResolve.ExMethodInfo) (a.get(1))).cover).collect(Collectors.toList());
        if (bestSoFar != zrResolve.methodNotFound && coverList.isEmpty()) {
            return bestSoFar;
        } else {
            if (!coverList.isEmpty()) {
                newResult.clear();
                newResult.addAll(coverList);
            }
            Type lowestType = null;
//            取类型最低方法
            for (List<Object> thisMethod : newResult) {
                final Type type = (Type) thisMethod.head;
                final ZrResolve.ExMethodInfo methodInfo = (ZrResolve.ExMethodInfo) thisMethod.get(1);
                if (lowestType == null) {
                    lowestType = type;
                    finalMethodSymbol = List.of(methodInfo);
                } else if (zrResolve.types.isSameType(type, lowestType)) {
                    finalMethodSymbol = finalMethodSymbol.append(methodInfo);
                } else if (zrResolve.types.isAssignable(type, lowestType)) {
                    lowestType = type;
                    finalMethodSymbol = List.of(methodInfo);
                }
            }
        }
        if (finalMethodSymbol.isEmpty()) {
            return zrResolve.methodNotFound;
        }
        if (finalMethodSymbol.size() == 1) {
            return finalMethodSymbol.head.methodSymbol;
        }
        if (memberReference) {
            Resolve.AmbiguityError ambiguityError = zrResolve.new AmbiguityError(finalMethodSymbol.get(0).methodSymbol, finalMethodSymbol.get(1).methodSymbol);
            finalMethodSymbol.stream().skip(2).forEach(info -> ambiguityError.addAmbiguousSymbol(info.methodSymbol));
            return ambiguityError;
        } else {
            return finalMethodSymbol.last().methodSymbol;
        }
    }

}
