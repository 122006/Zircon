package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.CompareSameMethod;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static com.sun.tools.javac.code.Flags.PARAMETER;

public class ZrResolveEx {

    public static boolean equalsIgnoreMetadata(Type t1, Type t2) {
        return t1.baseType().equals(t2.baseType());
    }
    static Pair<Symbol, ExMethodInfo> selectBestFromList(ZrResolve zrResolve, List<ExMethodInfo> methodSymbolList, Env<AttrContext> env, Type site, List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar, boolean allowBoxing, boolean useVarargs, boolean operator, boolean memberReference) {
        if (bestSoFar instanceof Resolve.ResolveError && !(bestSoFar instanceof Resolve.AmbiguityError)) bestSoFar = zrResolve.methodNotFound;

        java.util.List<List> newResult = new ArrayList<>();
        Pair<Symbol, ExMethodInfo> lastMethodSymbol = Pair.of(zrResolve.methodNotFound, null);
        java.util.List<ExMethodInfo> sortList = new ArrayList<>(methodSymbolList);
        sortList.sort((a1, a2) -> {
            final CompareSameMethod.MethodInfo<ExMethodInfo> info1 = CompareSameMethod.MethodInfo.create(a1.methodSymbol.owner
                    .getQualifiedName().toString(), a1);
            final CompareSameMethod.MethodInfo<ExMethodInfo> info2 = CompareSameMethod.MethodInfo.create(a2.methodSymbol.owner
                    .getQualifiedName().toString(), a2);
            return CompareSameMethod.compare(CompareSameMethod.CompareEnv.create(env.enclClass.sym
                    .getQualifiedName()
                    .toString()), info1, info2);
        });
        sortList = sortList.stream().filter(a -> {
            final List<Type.ClassType> filterAnnotation = a.filterAnnotation;
            if (filterAnnotation == null || filterAnnotation.isEmpty()) return true;
            for (Type.ClassType aClass : filterAnnotation) {
                boolean any = false;
                for (Attribute.Compound attribute : site.tsym.getAnnotationMirrors()) {
                    if (equalsIgnoreMetadata(attribute.type,aClass)) {
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
        for (ExMethodInfo methodInfo : sortList) {
            if (methodInfo.siteCopyByClassHeadArgMethod) {
                for (Type.ClassType type : methodInfo.targetClass) {
                    final boolean sameType = equalsIgnoreMetadata(type,site);
                    final Symbol.VarSymbol head = methodInfo.methodSymbol.getParameters().head;
                    final Type firstTypeArgument = zrResolve.types.erasure(head.type.getTypeArguments().head);
                    final Type.MethodType oldType = (Type.MethodType) methodInfo.methodSymbol.type;
                    if (sameType || zrResolve.types.isAssignable(site, firstTypeArgument)) {
                        Type.MethodType newType = new Type.MethodType(oldType.argtypes.diff(List.of(oldType.argtypes.head)), oldType.restype, oldType.thrown, oldType.tsym);
                        Symbol.MethodSymbol clone = new Symbol.MethodSymbol(methodInfo.methodSymbol.flags_field, methodInfo.methodSymbol.name, newType, type.tsym);
                        clone.code = methodInfo.methodSymbol.code;
                        final Symbol best = zrResolve.selectBest(env, site, argtypes, typeargtypes, clone, zrResolve.methodNotFound, allowBoxing, useVarargs, operator);
                        if (best == clone && best instanceof Symbol.MethodSymbol) {
                            lastMethodSymbol = Pair.of(methodInfo.methodSymbol, methodInfo);
                            newResult.add(List.of(type, methodInfo));
                            continue;
                        } else {
                            if (best != lastMethodSymbol.fst) {
                                lastMethodSymbol = Pair.of(best, null);
                            }
                        }
                    }
                }
            } else if (!methodInfo.isStatic) {
                Type type = methodInfo.methodSymbol.getParameters().head.type.baseType();
                type = zrResolve.types.erasure(type);
                List<Type> newArgTypes = List.from(argtypes);
                if (!memberReference) newArgTypes = newArgTypes.prepend(site);
                final Symbol best = zrResolve.selectBest(env, type, newArgTypes, typeargtypes, methodInfo.methodSymbol, zrResolve.methodNotFound, allowBoxing, useVarargs, operator);
                if (best == methodInfo.methodSymbol && best instanceof Symbol.MethodSymbol) {
                    lastMethodSymbol = Pair.of(methodInfo.methodSymbol, methodInfo);
                    newResult.add(List.of(type, methodInfo));
                    continue;
                } else {
                    if (best != lastMethodSymbol.fst) {
                        lastMethodSymbol = Pair.of(best, null);
                    }
                }
            } else {
                for (Type.ClassType type : methodInfo.targetClass) {
                    final boolean sameType = equalsIgnoreMetadata(type,site);
                    if (sameType || zrResolve.types.isAssignable(site, type)) {
                        final Symbol best = zrResolve.selectBest(env, type, argtypes, typeargtypes, methodInfo.methodSymbol, zrResolve.methodNotFound, allowBoxing, useVarargs, operator);
                        if (best == methodInfo.methodSymbol && best instanceof Symbol.MethodSymbol) {
                            lastMethodSymbol = Pair.of(methodInfo.methodSymbol, methodInfo);
                            newResult.add(List.of(type, methodInfo));
                            continue exInfo;
                        } else {
                            if (best != lastMethodSymbol.fst) {
                                lastMethodSymbol = Pair.of(best, null);
                            }
                        }
                    }
                }
            }

        }
        if (newResult.isEmpty()) {
            return Pair.of(bestSoFar, null);
        }
        List<ExMethodInfo> finalMethodSymbol = List.nil();
        final java.util.List<List> coverList = newResult.stream().filter(a -> ((ExMethodInfo) (a.get(1))).cover).collect(Collectors.toList());
        if (bestSoFar != zrResolve.methodNotFound && coverList.isEmpty()) {
            return Pair.of(bestSoFar, null);
        } else {
            if (!coverList.isEmpty()) {
                newResult.clear();
                newResult.addAll(coverList);
            }
            Type lowestType = null;
//            取类型最低方法
            for (List<Object> thisMethod : newResult) {
                final Type type = (Type) thisMethod.head;
                final ExMethodInfo methodInfo = (ExMethodInfo) thisMethod.get(1);
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
            return Pair.of(zrResolve.methodNotFound, null);
        }
        if (finalMethodSymbol.size() == 1) {
            return Pair.of(finalMethodSymbol.head.methodSymbol, finalMethodSymbol.head);
        }
        if (memberReference) {
            Resolve.AmbiguityError ambiguityError = zrResolve.new AmbiguityError(finalMethodSymbol.get(0).methodSymbol, finalMethodSymbol.get(1).methodSymbol);
            finalMethodSymbol.stream().skip(2).forEach(info -> ambiguityError.addAmbiguousSymbol(info.methodSymbol));
            return Pair.of(ambiguityError, null);
        } else {
            return Pair.of(finalMethodSymbol.last().methodSymbol, finalMethodSymbol.last());
        }
    }
    static JCTree.JCLambda createLambdaTree(ZrResolve zrResolve, JCTree.JCMemberReference memberReference, ExMethodInfo methodInfo) {
        final JCTree.JCLambda lambda;
        final TreeMaker maker = TreeMaker.instance(zrResolve.context);
        final List<Symbol.VarSymbol> params = methodInfo.methodSymbol.params();
        if (methodInfo.siteCopyByClassHeadArgMethod) {
            ListBuffer<JCTree.JCVariableDecl> jcVariableDecls = new ListBuffer<>();
            ListBuffer<JCTree.JCExpression> jcIdents = new ListBuffer<>();
            jcIdents.add(maker.ClassLiteral(memberReference.expr.type));
            for (int i = 1; i < params.size(); i++) {
                Symbol.VarSymbol param = params.get(i);
                final Name nameA = zrResolve.names.fromString("$zr$a" + i);
                final Type type = zrResolve.types.boxedTypeOrType(param.type);
                Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA, type, zrResolve.syms.noSymbol);
                symA.adr = 1 << i;
                jcVariableDecls.add(maker.VarDef(symA, null));
                jcIdents.add(maker.Ident(symA));
            }
            final JCTree.JCFieldAccess add = maker.Select(maker.Ident(methodInfo.methodSymbol.owner), methodInfo.methodSymbol.name);
            final JCTree.JCMethodInvocation apply = maker.Apply(memberReference.typeargs, add, jcIdents.toList());
            lambda = maker.Lambda(jcVariableDecls.toList(), apply);
        } else if (methodInfo.isStatic) {
            ListBuffer<JCTree.JCVariableDecl> jcVariableDecls = new ListBuffer<>();
            ListBuffer<JCTree.JCExpression> jcIdents = new ListBuffer<>();
            for (int i = 1; i < params.size(); i++) {
                Symbol.VarSymbol param = params.get(i);
                final Name nameA = zrResolve.names.fromString("$zr$a" + i);
                final Type type = zrResolve.types.boxedTypeOrType(param.type);
                Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA, type, zrResolve.syms.noSymbol);
                jcVariableDecls.add(maker.VarDef(symA, null));
                jcIdents.add(maker.Ident(symA));
            }
            final JCTree.JCFieldAccess add = maker.Select(maker.Ident(methodInfo.methodSymbol.owner), methodInfo.methodSymbol.name);
            final JCTree.JCMethodInvocation apply = maker.Apply(memberReference.typeargs, add, jcIdents.toList());
            lambda = maker.Lambda(jcVariableDecls.toList(), apply);
        } else {
            ListBuffer<JCTree.JCVariableDecl> jcVariableDecls = new ListBuffer<>();
            ListBuffer<JCTree.JCExpression> jcIdents = new ListBuffer<>();
            jcIdents.add(memberReference.expr);
            for (int i = 1; i < params.size(); i++) {
                Symbol.VarSymbol param = params.get(i);
                final Name nameA = zrResolve.names.fromString("$zr$a" + i);
                final Type type = zrResolve.types.boxedTypeOrType(param.type);
                Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA, type, zrResolve.syms.noSymbol);
                symA.adr = 1 << i;
                jcVariableDecls.add(maker.VarDef(symA, null));
                jcIdents.add(maker.Ident(symA));
            }
            final JCTree.JCFieldAccess add = maker.Select(maker.Ident(methodInfo.methodSymbol.owner), methodInfo.methodSymbol.name);
            final JCTree.JCMethodInvocation apply = maker.Apply(memberReference.typeargs, add, jcIdents.toList());
            lambda = maker.Lambda(jcVariableDecls.toList(), apply);
        }

        return lambda;
    }

}
