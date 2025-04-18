package com.sun.tools.javac.comp;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.parser.CompareSameMethod;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrConstants;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sun.tools.javac.code.Flags.PARAMETER;

@SuppressWarnings("unchecked")
public class ZrResolve extends Resolve {

    private Symbol methodNotFound = ReflectionUtil.getDeclaredField(this, Resolve.class, "methodNotFound");

    protected ZrResolve(Context context) {
        super(context);
    }

    ClassReader classReader;
    Context context;


    public static ZrResolve instance(Context context) {
        Resolve res = context.get(resolveKey);
        if (res instanceof ZrResolve) return (ZrResolve) res;
        context.put(resolveKey, (Resolve) null);
        final ZrResolve zrResolve = new ZrResolve(context);
        zrResolve.context = context;
        zrResolve.classReader = ClassReader.instance(context);
        ReflectionUtil.setDeclaredField(Attr.instance(context), Attr.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(Check.instance(context), Check.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(Flow.instance(context), Flow.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(Infer.instance(context), Infer.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(DeferredAttr.instance(context), DeferredAttr.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(TransTypes.instance(context), TransTypes.class, "resolve", zrResolve);
        ReflectionUtil.setDeclaredField(LambdaToMethod.instance(context), LambdaToMethod.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(Gen.instance(context), Gen.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(JavacTrees.instance(context), JavacTrees.class, "resolve", zrResolve);


        return zrResolve;
    }

    HashMap<JCTree.JCCompilationUnit, java.util.List<ExMethodInfo>> CompilationUnitCache = new HashMap<>();

    @SuppressWarnings("unchecked")
    public synchronized List<ExMethodInfo> findRedirectMethod(Env<AttrContext> env, Name methodName, boolean onlyCover) {
        java.util.List<ExMethodInfo> result = CompilationUnitCache.get(env.toplevel);
        if (result == null) {
            CompilationUnitCache.put(env.toplevel, result = new ArrayList<>());
            java.util.List<Symbol> symbols = new ArrayList<>();
            try {
                symbols.addAll(env.toplevel.packge.getEnclosedElements());
            } catch (Exception e) {
            }
            try {
                for (Symbol currentSym : env.toplevel.toplevelScope.getSymbols(a -> a instanceof Symbol.ClassSymbol, Scope.LookupKind.NON_RECURSIVE)) {
                    symbols.add(currentSym);
                }
            } catch (Exception ignored) {
            }
            try {
                for (Symbol currentSym : env.toplevel.namedImportScope.getSymbols(a -> a instanceof Symbol.ClassSymbol, Scope.LookupKind.NON_RECURSIVE)) {
                    symbols.add(currentSym);
                }
            } catch (Exception ignored) {
            }
            try {
                for (Symbol currentSym : env.toplevel.starImportScope.getSymbols(a -> a instanceof Symbol.ClassSymbol, Scope.LookupKind.NON_RECURSIVE)) {
                    symbols.add(currentSym);
                }
            } catch (Exception ignored) {
            }
            for (Symbol symbol : symbols) {
                if (!(symbol instanceof Symbol.ClassSymbol)) {
                    continue;
                }
                final String qualifiedName = symbol.getQualifiedName().toString();
                if (ZrConstants.exMethodIgnorePackages.stream().anyMatch(qualifiedName::startsWith)) {
                    continue;
                }
                try {
                    scanMethod((Symbol.ClassSymbol) symbol, result);
                } catch (Exception ignored) {
                }
            }

        }

        List<ExMethodInfo> ret = List.nil();
        for (ExMethodInfo exMethodInfo : result) {
            if (onlyCover && !exMethodInfo.cover) continue;
            if (!exMethodInfo.methodSymbol.name.contentEquals(methodName.toString())) {
                continue;
            }
            if (ret.contains(exMethodInfo)) continue;
            ret = ret.append(exMethodInfo);
        }
        return ret;
    }

    HashMap<String, java.util.List<ExMethodInfo>> exMethodCache = new HashMap<>();

    private void scanMethod(Symbol.ClassSymbol classSymbol, java.util.List<ExMethodInfo> result) {
        final String clazzName = "zircon.ExMethod";
        final Scope.WriteableScope members = classSymbol.members();
        members.getSymbols(symbol -> symbol instanceof Symbol.ClassSymbol).forEach(c -> {
            final Symbol.ClassSymbol c1 = (Symbol.ClassSymbol) c;
            scanMethod(c1, result);
        });
        final String classQualifiedName = classSymbol.getQualifiedName().toString();
        java.util.List<ExMethodInfo> classAllMethod = exMethodCache.get(classQualifiedName);
        if (classAllMethod == null) {
            classAllMethod = new ArrayList<>();
            exMethodCache.put(classQualifiedName, classAllMethod);
            for (Symbol symbol1 : members.getSymbols(symbol -> symbol instanceof Symbol.MethodSymbol && symbol.getAnnotationMirrors().stream()
                    .anyMatch(annotation -> annotation.type
                            .toString()
                            .equals(clazzName)))) {
                Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol1;

                final Optional<Attribute.Compound> first = symbol1.getAnnotationMirrors().stream()
                        .filter(annotation -> annotation.type.toString().equals(clazzName)).findFirst();
                if (!first.isPresent()) continue;
                final Attribute.Compound compound = first.get();
                try {
                    final ExMethodInfo exMethodInfo = new ExMethodInfo(method, false, false, List.nil(), List.nil());
                    final Attribute ex = compound.member(names.fromString("ex"));
                    if (ex != null) {
                        final List<Attribute.Class> exValue = (List<Attribute.Class>) ex.getValue();
                        for (Attribute.Class aClass : exValue) {
                            if (aClass.getValue() instanceof Type.ClassType) {
                                exMethodInfo.targetClass = exMethodInfo.targetClass.append((Type.ClassType) aClass.getValue());
                            } else {
                                throw new UnsupportedOperationException(method.getSimpleName() + "方法ex注解不能定义非Class值");
                            }
                        }
                    }
                    final Attribute cover = compound.member(names.fromString("cover"));
                    if (cover != null) {
                        exMethodInfo.cover = (boolean) cover.getValue();
                    }
                    exMethodInfo.isStatic = exMethodInfo.targetClass != null && exMethodInfo.targetClass.length() > 0;
                    final Attribute filterAnnotation = compound.member(names.fromString("filterAnnotation"));
                    if (filterAnnotation != null) {
                        final List<Attribute.Class> exValue = (List<Attribute.Class>) filterAnnotation.getValue();
                        for (Attribute.Class aClass : exValue) {
                            if (aClass.getValue() instanceof Type.ClassType) {
                                exMethodInfo.filterAnnotation = exMethodInfo.filterAnnotation.append((Type.ClassType) aClass.getValue());
                            } else {
                                throw new UnsupportedOperationException(method.getSimpleName() + "方法filterAnnotation注解不能定义非Class值");
                            }
                        }
                    }
                    classAllMethod.add(exMethodInfo);
                    if (!exMethodInfo.isStatic) {
                        final Symbol.VarSymbol head = exMethodInfo.methodSymbol.getParameters().head;
                        if (head.type.tsym == syms.classType.tsym) {
                            Type.ClassType clazz = null;
                            if (head.type.getTypeArguments().isEmpty()) {
                                clazz = (Type.ClassType) syms.objectType;
                            } else {
                                final Type firstTypeArgument = types.erasure(head.type.getTypeArguments().head);
                                if (!(firstTypeArgument instanceof Type.ClassType)) {
                                    throw new UnsupportedOperationException(method.getSimpleName() + "方法代理Class值时，类型无法解析为Class:" + firstTypeArgument.toString());
                                }
                                clazz = (Type.ClassType) firstTypeArgument;

                            }
                            final ExMethodInfo newExMethodInfo = new ExMethodInfo(exMethodInfo.methodSymbol, true, exMethodInfo.cover, List.of(clazz), exMethodInfo.filterAnnotation);
                            newExMethodInfo.siteCopyByClassHeadArgMethod = true;
                            classAllMethod.add(newExMethodInfo);
                        }
                    }
                } catch (Exception exc) {
                    exc.printStackTrace();
                }
            }
        }
        result.addAll(classAllMethod);
    }


    ReferenceLookupHelper makeReferenceLookupHelper(JCTree.JCMemberReference referenceTree, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
        if (!name.equals(names.init)) {
            //method reference
            return new ZrMethodReferenceLookupHelper(this, referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        }
        return super.makeReferenceLookupHelper(referenceTree, site, name, argtypes, typeargtypes, maxPhase);
    }


    protected JCTree.JCLambda createLambdaTree(JCTree.JCMemberReference memberReference, ExMethodInfo methodInfo) {
        final JCTree.JCLambda lambda;
        final TreeMaker maker = TreeMaker.instance(context);
        final List<Symbol.VarSymbol> params = methodInfo.methodSymbol.params();
        if (methodInfo.siteCopyByClassHeadArgMethod) {
            ListBuffer<JCTree.JCVariableDecl> jcVariableDecls = new ListBuffer<>();
            ListBuffer<JCTree.JCExpression> jcIdents = new ListBuffer<>();
            jcIdents.add(maker.ClassLiteral(memberReference.expr.type).setType(syms.classType));
            for (int i = 1; i < params.size(); i++) {
                Symbol.VarSymbol param = params.get(i);
                final Name nameA = names.fromString("$zr$a" + i);
                final Type type = types.boxedTypeOrType(param.type);
                Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA, type, syms.noSymbol);
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
                final Name nameA = names.fromString("$zr$a" + i);
                final Type type = types.boxedTypeOrType(param.type);
                Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA, type, syms.noSymbol);
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
                final Name nameA = names.fromString("$zr$a" + i);
                final Type type = types.boxedTypeOrType(param.type);
                Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA, type, syms.noSymbol);
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

    @Override
    Symbol resolveQualifiedMethod(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        if (pos == null) {
            return super.resolveQualifiedMethod(pos, env, location, site, name, argtypes, typeargtypes);
        }
        JCTree that = env.tree;
        if (that instanceof JCTree.JCMethodInvocation) {
            final JCTree.JCExpression meth = ((JCTree.JCMethodInvocation) that).meth;
            if (meth instanceof JCTree.JCFieldAccess) {
                final JCTree.JCExpression selected = ((JCTree.JCFieldAccess) meth).selected;
                if (selected instanceof JCTree.JCIdent) {
                    if (((JCTree.JCIdent) selected).getName() == this.names._super) {
                        return super.resolveQualifiedMethod(pos, env, location, site, name, argtypes, typeargtypes);
                    }
                }
            }
        }
        return resolveQualifiedMethod(new MethodResolutionContext(), pos, env, location, site, name, argtypes, typeargtypes);
    }

    @Override
    Symbol resolveMethod(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        return this.lookupMethod(env, pos, env.enclClass.sym, (MethodCheck) this.resolveMethodCheck, new ZrLookupHelper2(this, name, env.enclClass.sym.type, argtypes, typeargtypes));
    }


    private Symbol resolveQualifiedMethod(MethodResolutionContext resolveContext, JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        return lookupMethod(env, pos, location, resolveContext, new ZrLookupHelper(this, name, site, argtypes, typeargtypes));
    }


    Pair<Symbol, ExMethodInfo> selectBestFromList(List<ExMethodInfo> methodSymbolList, Env<AttrContext> env, Type site, List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar, boolean allowBoxing, boolean useVarargs, boolean memberReference) {
        if (bestSoFar instanceof ResolveError && !(bestSoFar instanceof AmbiguityError)) bestSoFar = methodNotFound;
        java.util.List<List> newResult = new ArrayList<>();
        Pair<Symbol, ExMethodInfo> lastMethodSymbol = Pair.of(methodNotFound, null);
        java.util.List<ExMethodInfo> sortList = new ArrayList<>(methodSymbolList);
        sortList.sort((a1, a2) -> {
            final CompareSameMethod.MethodInfo<ExMethodInfo> info1 = CompareSameMethod.MethodInfo.create(a1.methodSymbol.owner
                    .getQualifiedName().toString(), a1);
            final CompareSameMethod.MethodInfo<ExMethodInfo> info2 = CompareSameMethod.MethodInfo.create(a2.methodSymbol.owner
                    .getQualifiedName().toString(), a2);
            return CompareSameMethod.compare(CompareSameMethod.CompareEnv.create(env.toplevel.packge
                    .getQualifiedName()
                    .toString()), info1, info2);
        });
        sortList = sortList.stream().filter(a -> {
            final List<Type.ClassType> filterAnnotation = a.filterAnnotation;
            if (filterAnnotation == null || filterAnnotation.isEmpty()) return true;
            for (Type.ClassType aClass : filterAnnotation) {
                boolean any = false;
                for (Attribute.Compound attribute : site.tsym.getAnnotationMirrors()) {
                    if (attribute.type.equalsIgnoreMetadata(aClass)) {
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
                    final boolean sameType = type.equalsIgnoreMetadata(site);
                    final Symbol.VarSymbol head = methodInfo.methodSymbol.getParameters().head;
                    final List<Type> typeArguments = head.type.getTypeArguments();
                    final Type firstTypeArgument = typeArguments.isEmpty() ? syms.objectType : types.erasure(typeArguments.head);
                    final Type.MethodType oldType = methodInfo.methodSymbol.type.asMethodType();
                    if (sameType || types.isAssignable(site, firstTypeArgument)) {
                        Type.MethodType newType = new Type.MethodType(oldType.argtypes.diff(List.of(oldType.argtypes.head)), oldType.restype, oldType.thrown, oldType.tsym);
                        Symbol.MethodSymbol clone = new Symbol.MethodSymbol(methodInfo.methodSymbol.flags_field, methodInfo.methodSymbol.name, newType, type.tsym);
                        clone.code = methodInfo.methodSymbol.code;
                        final Symbol best = selectBest(env, site, argtypes, typeargtypes, clone, methodNotFound, allowBoxing, useVarargs);
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
                type = types.erasure(type);
                List<Type> newArgTypes = List.from(argtypes);
                if (!memberReference) newArgTypes = newArgTypes.prepend(site);
                final Symbol best = selectBest(env, type, newArgTypes, typeargtypes, methodInfo.methodSymbol, methodNotFound, allowBoxing, useVarargs);
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
                    final boolean sameType = type.equalsIgnoreMetadata(site);
                    if (sameType || types.isAssignable(site, type)) {
                        final Symbol best = selectBest(env, type, argtypes, typeargtypes, methodInfo.methodSymbol, methodNotFound, allowBoxing, useVarargs);
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
        final java.util.List<List> coverList = newResult.stream().filter(a -> ((ExMethodInfo) (a.get(1))).cover)
                .collect(Collectors.toList());
        if (bestSoFar != methodNotFound && coverList.isEmpty()) {
            return Pair.of(bestSoFar, null);
        } else {
            if (!coverList.isEmpty()) {
                newResult.clear();
                newResult.addAll(coverList);
            }
            Type lowestType = Type.noType;
            //取类型最低方法
            for (int i = 0; i < newResult.size(); i++) {
                final List thisMethod = newResult.get(i);
                final Type type = (Type) thisMethod.head;
                final ExMethodInfo methodInfo = (ExMethodInfo) thisMethod.get(1);
                if (lowestType == Type.noType) {
                    lowestType = type;
                    finalMethodSymbol = List.of(methodInfo);
                } else if (types.isSameType(type, lowestType)) {
                    finalMethodSymbol = finalMethodSymbol.append(methodInfo);
                } else if (types.isAssignable(type, lowestType)) {
                    lowestType = type;
                    finalMethodSymbol = List.of(methodInfo);
                }
            }
        }
        if (finalMethodSymbol.isEmpty()) {
            return Pair.of(methodNotFound, null);
        }
        if (finalMethodSymbol.size() == 1) {
            return Pair.of(finalMethodSymbol.head.methodSymbol, finalMethodSymbol.head);
        }
        if (memberReference) {
            AmbiguityError ambiguityError = new AmbiguityError(finalMethodSymbol.get(0).methodSymbol, finalMethodSymbol.get(1).methodSymbol);
            finalMethodSymbol.stream().skip(2).forEach(info -> ambiguityError.addAmbiguousSymbol(info.methodSymbol));
            return Pair.of(ambiguityError, null);
        } else {
            return Pair.of(finalMethodSymbol.last().methodSymbol, finalMethodSymbol.last());
        }
    }


    protected Pair<Symbol, ExMethodInfo> findMethod2(Env<AttrContext> env, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar, boolean allowBoxing, boolean useVarargs, boolean memberReference) {
        final List<ExMethodInfo> redirectMethod = findRedirectMethod(env, name, methodSymbolEnable(bestSoFar));
        if (redirectMethod != null && !redirectMethod.isEmpty()) {
            return selectBestFromList(redirectMethod, env, site, argtypes, typeargtypes, bestSoFar, allowBoxing, useVarargs, memberReference);
        } else {
            return Pair.of(bestSoFar, null);
        }
    }


    public boolean methodSymbolEnable(Symbol bestSoFar) {
        return bestSoFar instanceof Symbol.MethodSymbol || bestSoFar instanceof AmbiguityError;
    }
}
