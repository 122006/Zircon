package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.Flags.SIGNATURE_POLYMORPHIC;
import static com.sun.tools.javac.code.Kinds.Kind.AMBIGUOUS;
import static com.sun.tools.javac.code.TypeTag.FORALL;
import static com.sun.tools.javac.comp.Resolve.MethodResolutionPhase.BASIC;

public class ZrResolve extends Resolve {

    private Symbol methodNotFound = ReflectionUtil.getDeclaredField(this, Resolve.class, "methodNotFound");

    protected ZrResolve(Context context) {
        super(context);
    }

    public static ZrResolve instance(Context context) {
        Resolve res = context.get(resolveKey);
        if (res instanceof ZrResolve) return (ZrResolve) res;
        context.put(resolveKey, (Resolve) null);
        final ZrResolve zrResolve = new ZrResolve(context);
        context.put(resolveKey, zrResolve);
        {
            final Attr instance = Attr.instance(context);
            if (ReflectionUtil.getDeclaredField(instance, Attr.class, "rs") != null) {
                ReflectionUtil.setDeclaredField(instance, Attr.class, "rs", zrResolve);
            }
        }

        {
            final Check instance = Check.instance(context);
            if (ReflectionUtil.getDeclaredField(instance, Check.class, "rs") != null) {
                ReflectionUtil.setDeclaredField(instance, Check.class, "rs", zrResolve);
            }
        }
        {
            final DeferredAttr instance = DeferredAttr.instance(context);
            if (ReflectionUtil.getDeclaredField(instance, DeferredAttr.class, "rs") != null) {
                ReflectionUtil.setDeclaredField(instance, DeferredAttr.class, "rs", zrResolve);
            }
        }
        return zrResolve;
    }



    ReferenceLookupHelper makeReferenceLookupHelper(JCTree.JCMemberReference referenceTree,
                                                    Type site,
                                                    Name name,
                                                    List<Type> argtypes,
                                                    List<Type> typeargtypes,
                                                    MethodResolutionPhase maxPhase) {
        if (!name.equals(names.init)) {
            //method reference
            return new ZrMethodReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        }
        return super.makeReferenceLookupHelper(referenceTree, site, name, argtypes, typeargtypes, maxPhase);
    }

    class ZrMethodReferenceLookupHelper extends ReferenceLookupHelper {

        MethodReferenceLookupHelper helper;
        Type oSite;


        ZrMethodReferenceLookupHelper(JCTree.JCMemberReference referenceTree, Name name, Type site,
                                      List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
            oSite = site;
            helper = new MethodReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        final Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            System.out.println("lookup :" + env.tree + " phase:" + phase);
            final Symbol method = findMethod(env, site, name, argtypes, typeargtypes,
                    phase.isBoxingRequired(), phase.isVarargsRequired());
            final Symbol symbol = TreeInfo.symbol(((JCTree.JCMemberReference) env.tree).getQualifierExpression());
            System.out.println("symbol:" + symbol);
            System.out.println("attr.pt():" + attr.pt() + "[" + (attr.pt().getTag() != TypeTag.NONE));
            if (!TreeInfo.isStaticSelector(referenceTree.expr, names)) {
                Symbol method2 = method;
                for (ExMethodInfo methodInfo : findRedirectMethod(name)) {
                    final List<Symbol.VarSymbol> nParams = methodInfo.methodSymbol.params();
                    if (nParams.size() == 0) continue;
                    if (!types.isCastable(site, nParams.get(0).type)) {
                        continue;
                    }
                    ListBuffer listBuffer = new ListBuffer();
                    nParams.stream().skip(1).map(a -> a.type).forEach(listBuffer::append);
                    final Type returnType = ((Symbol.MethodSymbol) methodInfo.methodSymbol).type.getReturnType();
                    Type.MethodType methodType = new Type.MethodType(listBuffer.toList(), returnType, List.nil(), oSite.tsym);
                    Symbol.MethodSymbol tempSymbol = new Symbol.MethodSymbol(PUBLIC, name, methodType, oSite.tsym);
                    System.out.println("start Check=" + tempSymbol);
                    System.out.println("returnResult=" + env.info.returnResult.checkContext.inferenceContext());
                    throw new NeedRedirectMethod(methodInfo.methodSymbol);
                }

                if (method instanceof AmbiguityError) {
                    System.out.println("method1: " + ((AmbiguityError) method).ambiguousSyms);
                } else
                    System.out.println("method1: " + method);

                if (method2 instanceof AmbiguityError) {
                    System.out.println("method2: " + ((AmbiguityError) method2).ambiguousSyms);
                } else
                    System.out.println("method2: " + method2);
//                    if (method == methodNotFound && method2.exists()) {
//                        throw new NeedRedirectMethod(method2);
//                    }
                return method2;
            }

            System.out.println("oSite: " + oSite);
            Symbol method2 = findMethod2(env, oSite, name, argtypes, typeargtypes,
                    method,
                    phase.isBoxingRequired(),
                    phase.isVarargsRequired());
//            System.out.println("method : " + method + " best? " + method2);
            if (!method.exists() && method2.exists() && env.tree.toString().startsWith("testString")) {
                if (method2 instanceof Symbol.MethodSymbol) {
                    if (attr.pt().getTag() == TypeTag.NONE && env.tree.toString().startsWith("testString")) {
                        Type.MethodType methodType = new Type.MethodType(argtypes, ((Symbol.MethodSymbol) method2).type.getReturnType(), List.nil(), oSite.tsym);
                        Symbol.MethodSymbol tempSymbol = new Symbol.MethodSymbol(PUBLIC, name, methodType, oSite.tsym);
                        System.out.println("temp symbol:" + methodType);
                        return tempSymbol;
                    }
                    throw new NeedRedirectMethod(method2);
                }
                if (method2 instanceof AmbiguityError) {
                    for (Symbol methodSymbolItem : ((AmbiguityError) method2).ambiguousSyms) {
                        Symbol.MethodSymbol methodSymbol = (Symbol.MethodSymbol) methodSymbolItem;
                        final List<Symbol.VarSymbol> nParams = methodSymbol.params();
                        if (nParams.size() == 0) continue;
                        if (!types.isSubtype(site, nParams.get(0).type)) {
                            continue;
                        }
                        ListBuffer listBuffer = new ListBuffer();
                        nParams.stream().skip(1).map(a -> a.type).forEach(listBuffer::append);
                        if (attr.pt().getTag() == TypeTag.NONE && env.tree.toString().startsWith("testString")) {
                            final Type returnType = ((Symbol.MethodSymbol) methodSymbol).type.getReturnType();
                            Type.MethodType methodType = new Type.MethodType(listBuffer.toList(), returnType, List.nil(), oSite.tsym);
                            Symbol.MethodSymbol tempSymbol = new Symbol.MethodSymbol(PUBLIC, name, methodType, oSite.tsym);
                            System.out.println("temp symbol2:" + methodType);
                            return tempSymbol;
                        }
                        throw new NeedRedirectMethod(methodSymbol);
                    }
                }
            }


            if (!method2.exists() && !(method2 instanceof AmbiguityError)) method2 = method;
            if (method2 instanceof AmbiguityError) {
                System.out.println("method2: " + ((AmbiguityError) method2).ambiguousSyms);
            } else
                System.out.println("method2: " + method2);
            return method2;
        }

        @Override
        Symbol access(Env<AttrContext> env, JCDiagnostic.DiagnosticPosition pos, Symbol location, Symbol sym) {
            final JCTree.JCExpression qualifierExpression = ((JCTree.JCMemberReference) env.tree).getQualifierExpression();
            final Symbol symbol = TreeInfo.symbol(qualifierExpression);
            if (symbol != null) {
                if (sym == methodNotFound && !TreeInfo.isStaticSelector(referenceTree.expr, names)) {
//                    throw new NeedLowerLambda(site, qualifierExpression, name);
                }
            } else {

            }
            return super.access(env, pos, location, sym);
        }

        @Override
        ReferenceLookupHelper unboundLookup(InferenceContext inferenceContext) {
            return helper.unboundLookup(inferenceContext);
        }

        @Override
        JCTree.JCMemberReference.ReferenceKind referenceKind(Symbol sym) {
            return helper.referenceKind(sym);
        }
    }

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
//            System.out.println("==============doLookup  site:" + site + " name:" + name + " phase:" + phase);
            final Symbol bestSoFar = findMethod(env, site, name, argtypes, typeargtypes,
                    phase.isBoxingRequired(),
                    phase.isVarargsRequired());
            final Symbol newSymbol = findMethod2(env, site, name, argtypes, typeargtypes,
                    bestSoFar,
                    phase.isBoxingRequired(),
                    phase.isVarargsRequired());
//            if (bestSoFar instanceof AmbiguityError) {
//                System.out.println("method1: " + ((AmbiguityError) bestSoFar).ambiguousSyms + " [" + bestSoFar.kind.isValid());
//            } else
//                System.out.println("method1: " + bestSoFar + " [" + bestSoFar.kind.isValid());
//
//            if (newSymbol instanceof AmbiguityError) {
//                System.out.println("method2: " + ((AmbiguityError) newSymbol).ambiguousSyms + " [" + newSymbol.kind.isValid());
//            } else
//                System.out.println("method2: " + newSymbol + " [" + newSymbol.kind.isValid());

            if ((newSymbol.kind.isValid() && !bestSoFar.kind.isValid())
                    || (newSymbol.kind.isValid() && bestSoFar.kind.isValid() && newSymbol != bestSoFar)) {
                throw new NeedRedirectMethod(newSymbol);
            } else {
                return newSymbol;
            }
        }

        @Override
        Symbol access(Env<AttrContext> env, JCDiagnostic.DiagnosticPosition pos, Symbol location, Symbol sym) {
//            System.out.println("==============access  location:" + location + "  env:" + env.tree + "  location:" + location + " sym:" + sym);
//            System.out.println("argtypes  :" + argtypes);
//            System.out.println("typeargtypes  :" + typeargtypes);
//            System.out.println("sym  :" + sym);
//            System.out.println("sym.kind  :" + sym.kind);
            sym = super.access(env, pos, location, sym);
//            System.out.println("access result  " + sym + " [" + sym.type + " [" + sym.type.getClass());
            return sym;
        }
    }

    private Symbol resolveQualifiedMethod(MethodResolutionContext resolveContext,
                                          JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env,
                                          Symbol location, Type site, Name name, List<Type> argtypes,
                                          List<Type> typeargtypes) {
        return lookupMethod(env, pos, location, resolveContext, new ZrLookupHelper(name, site, argtypes, typeargtypes));
    }

    public static class ExMethodInfo {
        Symbol.MethodSymbol methodSymbol;
        boolean isStatic;
        boolean cover;
        List<Type> targetType;
        List<Attribute.Class> targetClass;

        public ExMethodInfo(Symbol.MethodSymbol methodSymbol, boolean isStatic, boolean cover, List<Type> targetType, List<Attribute.Class> targetClass) {
            this.methodSymbol = methodSymbol;
            this.isStatic = isStatic;
            this.cover = cover;
            this.targetType = targetType;
            this.targetClass = targetClass;
        }
    }

    Map<Name, List<ExMethodInfo>> redirectMethodSymbolMap = null;

    @SuppressWarnings("unchecked")
    public List<ExMethodInfo> findRedirectMethod(Name methodName) {
        if (redirectMethodSymbolMap == null) {
            redirectMethodSymbolMap = new HashMap<>();
            long startTime = System.currentTimeMillis();
            final Map<Name, Map<Symbol.ModuleSymbol, Symbol.PackageSymbol>> allPackages = ReflectionUtil.getDeclaredField(syms, Symtab.class, "packages");
            System.out.println("======allPackages：" + allPackages);
            final String clazzName = "zircon.ExMethod";
            for (Map<Symbol.ModuleSymbol, Symbol.PackageSymbol> packageSymPair : new ArrayList<>(allPackages.values())) {
                packageSymPair.values().stream()
                        .filter(packageSymbol -> Stream.of("java.util", "com.sun", "sun", "jdk", "org.junit", "java.io", "java.nio", "java.lang", "java.security", "java.net")
                                .noneMatch(a -> packageSymbol.fullname.toString().startsWith(a)))
                        .forEach(packageSymbol -> {
                            final List<Symbol> enclosedElements = packageSymbol.getEnclosedElements();
                            enclosedElements.stream().filter(e -> e instanceof Symbol.ClassSymbol).forEach(e -> {
                                final Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) e;
                                System.out.println("find in " + classSymbol + "[" + classSymbol.getClass());
                                classSymbol.members().getSymbols(symbol -> symbol instanceof Symbol.MethodSymbol && symbol.getAnnotationMirrors().stream().anyMatch(annotation -> annotation.type.toString().equals(clazzName)))
                                        .forEach(symbol -> {
                                            Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
                                            symbol.getAnnotationMirrors().stream().filter(annotation -> annotation.type.toString().equals(clazzName)).findFirst()
                                                    .ifPresent(compound -> {
                                                        final ExMethodInfo exMethodInfo = new ExMethodInfo(method, false, false, List.nil(), List.nil());
                                                        final Attribute ex = compound.member(names.fromString("ex"));
                                                        if (ex != null) {
                                                            exMethodInfo.targetClass = (List<Attribute.Class>) ex.getValue();
                                                        }
                                                        final Attribute cover = compound.member(names.fromString("cover"));
                                                        if (cover != null) {
                                                            exMethodInfo.cover = (boolean) cover.getValue();
                                                        }
                                                        System.out.println("find method " + method + "[" + method.getSimpleName() + "[" + method.type);
                                                        final List<ExMethodInfo> list = redirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                                                        redirectMethodSymbolMap.put(method.getSimpleName(), list.append(exMethodInfo));
                                                    });
                                        });
                            });
                        });
            }
            System.out.println("======search end");
            System.out.println(redirectMethodSymbolMap.values());
            System.out.println("扫描耗时:" + (System.currentTimeMillis() - startTime) + "ms");
        }
        final List<ExMethodInfo> list = redirectMethodSymbolMap.get(methodName);
        return list == null ? List.nil() : list;
    }

    public static List<Attribute.Class> getMethodStaticExType(Names names, Symbol.MethodSymbol symbol) {
        final String clazz = "zircon.ExMethod";
        final Optional<Attribute.Compound> exMethod = symbol.getAnnotationMirrors().stream().filter(annotation -> annotation.type.toString().equals(clazz)).findFirst();
        if (exMethod.isPresent()) {
            final Attribute.Compound compound = exMethod.get();
            final Attribute ex = compound.member(names.fromString("ex"));
            if (ex != null) {
                @SuppressWarnings("unchecked") final List<Attribute.Class> value = (List<Attribute.Class>) ex.getValue();
                return value;
            }
        }
        return List.nil();
    }


    public static class NeedRedirectMethod extends RuntimeException {
        public NeedRedirectMethod(Symbol bestSoFar) {
            this.bestSoFar = bestSoFar;
        }

        Symbol bestSoFar;
        Attr.ResultInfo resultInfo;

        public NeedRedirectMethod(Symbol bestSoFar, Attr.ResultInfo resultInfo) {
            this.bestSoFar = bestSoFar;
            this.resultInfo = resultInfo;
        }
    }

    Symbol selectBestFromList(List<ExMethodInfo> methodSymbolList, Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes,
                              Symbol bestSoFar,
                              boolean allowBoxing,
                              boolean useVarargs) {
        final Symbol oBestSoFar = bestSoFar;
        for (ExMethodInfo methodInfo : methodSymbolList) {
            if (oBestSoFar.exists() && !methodInfo.cover) continue;
            final List<Attribute.Class> methodStaticExType = methodInfo.targetClass;
            List<Type> newTypeArgTypes = typeargtypes;
            List<Type> newArgTypes = argtypes;
            if (methodStaticExType.isEmpty()) {
                if (typeargtypes == null) newTypeArgTypes = List.nil();
                newArgTypes = newArgTypes.prepend(site);
                bestSoFar = selectBest(env, methodInfo.methodSymbol.owner.type, newArgTypes, newTypeArgTypes, methodInfo.methodSymbol,
                        bestSoFar, allowBoxing, useVarargs);
            } else {
                if (methodStaticExType.stream().anyMatch(a -> Objects.equals(a.classType.toString(), site.toString()))) {
                    if (methodInfo.cover) {
                        bestSoFar = selectBest(env, site, newArgTypes, newTypeArgTypes, methodInfo.methodSymbol,
                                bestSoFar == oBestSoFar ? methodNotFound : bestSoFar, allowBoxing, useVarargs);
                    } else {
                        bestSoFar = selectBest(env, site, newArgTypes, newTypeArgTypes, methodInfo.methodSymbol,
                                bestSoFar, allowBoxing, useVarargs);
                    }
                }
            }

        }
        return bestSoFar;
    }


    protected Symbol findMethod2(Env<AttrContext> env,
                                 Type site,
                                 Name name,
                                 List<Type> argtypes,
                                 List<Type> typeargtypes,
                                 Symbol bestSoFar,
                                 boolean allowBoxing,
                                 boolean useVarargs) {
//        System.out.println("====findMethod name=" + name
//                + "  ;env.tree:" + env.tree + "[" + env.tree.getClass()
//                + "  ;site=" + site
//                + "  ;receiver=" + site.getTypeArguments()
//                + "  ;" + "argtypes=" + argtypes + ";" + "typeargtypes=" + typeargtypes);
        final List<ExMethodInfo> redirectMethod = findRedirectMethod(name);
        if (redirectMethod != null && !redirectMethod.isEmpty()) {
            return selectBestFromList(redirectMethod, env, site, argtypes, typeargtypes, bestSoFar, allowBoxing, useVarargs);
        } else {
            return bestSoFar;
        }
    }

}
