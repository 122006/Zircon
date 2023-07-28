package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.Flags.SIGNATURE_POLYMORPHIC;
import static com.sun.tools.javac.code.Kinds.Kind.ABSENT_MTH;
import static com.sun.tools.javac.code.Kinds.Kind.ERR;
import static com.sun.tools.javac.comp.Resolve.MethodResolutionPhase.BASIC;

public class ZrResolve extends Resolve {
    protected ZrResolve(Context context) {
        super(context);
    }

    public static ZrResolve instance(Context context) {
        System.out.println("ZrResolve instance()");
        Resolve res = context.get(resolveKey);
        if (res instanceof ZrResolve) return (ZrResolve) res;
        context.put(resolveKey, (Resolve) null);
        final ZrResolve zrResolve = new ZrResolve(context);
        context.put(resolveKey, zrResolve);
        {
            final Attr instance = Attr.instance(context);
            if (ReflectionUtil.getDeclaredField(instance, Attr.class, "rs") != null) {
                System.out.println("覆盖rs");
                ReflectionUtil.setDeclaredField(instance, Attr.class, "rs", zrResolve);
            }
        }

        {
            final Check instance = Check.instance(context);
            if (ReflectionUtil.getDeclaredField(instance, Check.class, "rs") != null) {
                System.out.println("覆盖rs");
                ReflectionUtil.setDeclaredField(instance, Check.class, "rs", zrResolve);
            }
        }
        {
            final DeferredAttr instance = DeferredAttr.instance(context);
            if (ReflectionUtil.getDeclaredField(instance, DeferredAttr.class, "rs") != null) {
                System.out.println("覆盖rs");
                ReflectionUtil.setDeclaredField(instance, DeferredAttr.class, "rs", zrResolve);
            }
        }
        return zrResolve;
    }


    @Override
    Symbol resolveMethod(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        System.out.println("resolveMethod:name=" + name);
        final Symbol fun = super.resolveMethod(pos, env, name, argtypes, typeargtypes);
        return fun;
    }

    Symbol findFun(Env<AttrContext> env, Name name,
                   List<Type> argtypes, List<Type> typeargtypes,
                   boolean allowBoxing, boolean useVarargs) {
        final Symbol fun = super.findFun(env, name, argtypes, typeargtypes, allowBoxing, useVarargs);
        if (!fun.exists()) {
            Symbol sym = findMethod(env, syms.predefClass.type, name, argtypes.prepend(env.enclClass.sym.type),
                    typeargtypes.prepend(env.enclClass.sym.type), allowBoxing, useVarargs);
        }
        System.out.println("findFun:name=" + name);
        return fun;
    }

    @Override
    Pair<Symbol, ReferenceLookupHelper> resolveMemberReference(Env<AttrContext> env, JCTree.JCMemberReference referenceTree, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, Type descriptor, MethodCheck methodCheck, InferenceContext inferenceContext, ReferenceChooser referenceChooser) {
        System.out.println("env:" + env + "  referenceTree:" + referenceTree + "  site:" + site + "  argtypes:" + argtypes + "  typeargtypes:" + typeargtypes + "  descriptor:" + descriptor);
        return super.resolveMemberReference(env, referenceTree, site, name, argtypes, typeargtypes, descriptor, methodCheck, inferenceContext, referenceChooser);
    }

    @Override
    Symbol lookupMethod(Env<AttrContext> env, JCDiagnostic.DiagnosticPosition pos, Symbol location, MethodCheck methodCheck, LookupHelper lookupHelper) {
        System.out.println("env:" + env + "  location:" + location);
        return super.lookupMethod(env, pos, location, methodCheck, lookupHelper);
    }

//    @Override
//    Symbol findMethodInScope(Env<AttrContext> env, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, Scope sc, Symbol bestSoFar, boolean allowBoxing, boolean useVarargs, boolean abstractok) {
//        System.out.println("=========start find method: " + site + "." + name + "   from:" + sc);
//        Iterable<Symbol> symbolsByName = sc.getSymbolsByName(name, new LookupFilter(abstractok));
//        final boolean isExist = symbolsByName.iterator().hasNext();
//        System.out.println("=========未找到方法");
//        if (!isExist) {
//            final LookupFilter sf = new LookupFilter(abstractok);
//            java.util.List<Symbol.ClassSymbol> list = new ArrayList<>();
////            syms.getAllClasses().forEach(list::add);
////            final Optional<Symbol> first = list.stream().filter(cs -> cs.kind != ERR && cs.classfile != null)
////                    .map(cs -> {
////                        Symbol find = null;
////                        for (Symbol symbol : cs.members().getSymbols()) {
////                            if (symbol.getAnnotationMirrors().stream().anyMatch(annotation -> {
////                                return annotation.toString().contains("@java.lang.Deprecated");
////                            }) && Objects.equals(symbol.name, name)) {
////                                System.out.println(symbol.name + "" + symbol.getAnnotationMirrors());
////                                find = symbol;
////                            }
////                        }
////                        return find;
////                    })
////                    .filter(Objects::nonNull)
////                    .findFirst();
//            if (first.isPresent()) {
//                Symbol s = first.get();
//                System.out.println("=========found method: " + s.owner.name + "." + s.name);
//                bestSoFar = selectBest(env, site, argtypes, typeargtypes, s,
//                        bestSoFar, allowBoxing, useVarargs);
//                return bestSoFar;
//            }
//        }
//        for (Symbol s : symbolsByName) {
//            System.out.println("=========found method: " + s.owner.name + "." + s.name);
//            bestSoFar = selectBest(env, site, argtypes, typeargtypes, s,
//                    bestSoFar, allowBoxing, useVarargs);
//        }
//        return bestSoFar;
//    }


    @Override
    Symbol resolveQualifiedMethod(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env,
                                  Symbol location, Type site, Name name, List<Type> argtypes,
                                  List<Type> typeargtypes) {
        return resolveQualifiedMethod(new MethodResolutionContext(), pos, env, location, site, name, argtypes, typeargtypes);
    }

    protected class ZrLookupHelper extends BasicLookupHelper {

        ZrLookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
            super(name, site, argtypes, typeargtypes);
        }

        @Override
        Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            System.out.println("==============doLookup  site:" + site + " name:" + name);
            return findMethod(env, site, name, argtypes, typeargtypes,
                    phase.isBoxingRequired(),
                    phase.isVarargsRequired());
        }

        @Override
        Symbol access(Env<AttrContext> env, JCDiagnostic.DiagnosticPosition pos, Symbol location, Symbol sym) {
            System.out.println("==============access  location:" + location + " sym:" + sym);
            if (sym.kind.isResolutionError()) {
                System.out.println("==============access isResolutionError");
                sym = super.access(env, pos, location, sym);
            } else {
                System.out.println("==============access noResolutionError");
                Symbol.MethodSymbol msym = (Symbol.MethodSymbol) sym;
                System.out.println("==============access (msym.flags() & SIGNATURE_POLYMORPHIC) != 0   =   " + ((msym.flags() & SIGNATURE_POLYMORPHIC) != 0));
                if ((msym.flags() & SIGNATURE_POLYMORPHIC) != 0) {
                    env.info.pendingResolutionPhase = BASIC;
                    return findPolymorphicSignatureInstance(env, sym, argtypes);
                }
            }
            return sym;
        }
    }

    private Symbol resolveQualifiedMethod(MethodResolutionContext resolveContext,
                                          JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env,
                                          Symbol location, Type site, Name name, List<Type> argtypes,
                                          List<Type> typeargtypes) {
        return lookupMethod(env, pos, location, resolveContext, new ZrLookupHelper(name, site, argtypes, typeargtypes));
    }

    Map<Name, List<Symbol.MethodSymbol>> redirectMethodSymbolMap = null;

    public List<Symbol.MethodSymbol> findRedirectMethod(Name methodName) {
        if (redirectMethodSymbolMap == null) {
            redirectMethodSymbolMap = new HashMap<>();
            final Map<Name, Map<Symbol.ModuleSymbol, Symbol.PackageSymbol>> allPackages = ReflectionUtil.getDeclaredField(syms, Symtab.class, "packages");
            System.out.println("======allPackages：" + allPackages);
            allPackages.values()
                    .forEach(packageSymPair -> {
                        packageSymPair.values().stream()
                                .filter(packageSymbol -> packageSymbol.fullname.toString().startsWith("test"))
                                .forEach(packageSymbol -> {
                                    System.out.println("======packageSymbol：" + packageSymbol.fullname);
                                    final List<Symbol> enclosedElements = packageSymbol.getEnclosedElements();
                                    enclosedElements.stream().filter(e -> e instanceof Symbol.ClassSymbol).forEach(e -> {
                                        final Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) e;
                                        System.out.println("find in " + classSymbol + "[" + classSymbol.getClass());
                                        classSymbol.members().getSymbols(symbol -> symbol instanceof Symbol.MethodSymbol && symbol.getAnnotationMirrors().stream().anyMatch(annotation -> annotation.toString().endsWith("ExMethod")))
                                                .forEach(symbol -> {
                                                    Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
                                                    System.out.println("find method " + method + "[" + method.getSimpleName());
                                                    final List<Symbol.MethodSymbol> list = redirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                                                    redirectMethodSymbolMap.put(method.getSimpleName(), list.append(method));
                                                });
                                    });
                                });
                    });
            System.out.println("======search end");
            System.out.println(redirectMethodSymbolMap.values());
        }
        return redirectMethodSymbolMap.get(methodName);
    }


    public static class NeedRedirectMethod extends RuntimeException{
        public NeedRedirectMethod(Symbol bestSoFar) {
            this.bestSoFar = bestSoFar;
        }

        Symbol bestSoFar;
    }


    @Override
    Symbol findMethod(Env<AttrContext> env,
                      Type site,
                      Name name,
                      List<Type> argtypes,
                      List<Type> typeargtypes,
                      boolean allowBoxing,
                      boolean useVarargs) {
        System.out.println("====findMethod name=" + name + ";" + "site=" + site + ";" + "argtypes=" + argtypes + ";" + "typeargtypes=" + typeargtypes);
        Symbol bestSoFar = super.findMethod(env, site, name, argtypes, typeargtypes, allowBoxing, useVarargs);
        if (bestSoFar instanceof SymbolNotFoundError) {
            System.out.println("======methodNotFound：" + name);
            final List<Symbol.MethodSymbol> redirectMethod = findRedirectMethod(name);
            if (!redirectMethod.isEmpty()){
                throw new NeedRedirectMethod(redirectMethod.get(0));
            }
            System.out.println("======method Found：" + redirectMethod);
            System.out.println("======env：" + env + "[" + env.getClass());
            System.out.println("======env.next：" + env.next + "[" + env.next.getClass());
            System.out.println("======env.outer：" + env.outer + "[" + env.outer.getClass());
            System.out.println("======env.tree：" + env.tree + "[" + env.tree.getClass());
            new Exception().printStackTrace();
            if (env.tree instanceof JCTree.JCMethodInvocation) {
                final JCTree.JCMethodInvocation methodInvocation = (JCTree.JCMethodInvocation) env.tree;
                System.out.println("======Arguments：" + methodInvocation.getArguments());
                System.out.println("======TypeArguments：" + methodInvocation.getTypeArguments());
                System.out.println("======MethodSelect：" + methodInvocation.getMethodSelect() + "[" + methodInvocation.getMethodSelect().getClass());
                if (methodInvocation.getMethodSelect() instanceof JCTree.JCFieldAccess) {
                    final JCTree.JCFieldAccess methodSelect = (JCTree.JCFieldAccess) methodInvocation.getMethodSelect();
                    methodInvocation.getArguments().prepend(methodSelect.selected);
                    methodInvocation.getTypeArguments().prepend(methodInvocation.getMethodSelect()).prepend(methodSelect.selected);
                } else {
                    System.err.println("unknown MethodSelect:" + methodInvocation.getMethodSelect().getClass());
                }

            } else {
                System.err.println("unknown treeType:" + env.tree.getClass());
            }
//            final TreeMaker maker = TreeMaker.instance(context);
//            final ArrayList<JCTree.JCExpression> args = new ArrayList<>();
//            final JCTree.JCExpression selected = ((JCTree.JCFieldAccess) that.meth).selected;
//            System.out.println("-------------name=" + TreeInfo.name(selected));
//            args.add(selected);
//            args.addAll(that.args);
//            final JCTree.JCFieldAccess add = maker.Select(maker.Ident(names.fromString("Test")), names.fromString("add"));
//            that = maker.Apply(List.nil(), add, List.from(args));
//            that.meth = add;
//            that.args = List.from(args);
//            System.out.println("--------=>" + that.toString());
//            super.visitApply(that);
//            System.out.println("--------type=" + result);
//            new Exception().printStackTrace();
            return redirectMethod.get(0);
        }
//        if (bestSoFar.kind.ordinal() >= Kinds.Kind.ERR.ordinal()) {
//            if (name.toString().equals("add")&&argtypes.size()==2){
//                final List<Type> tail = argtypes.tail;
//                tail.add(0,site);
//                bestSoFar = findMethod(env, findType(env,names.fromString("test.TestClass2.Test")).type, names.fromString("add"), tail, null, true, false);
//            }
//        }
        return bestSoFar;
    }
}
