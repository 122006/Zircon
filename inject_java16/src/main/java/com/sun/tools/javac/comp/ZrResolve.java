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
    ;

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
    Symbol findFun(Env<AttrContext> env, Name name,
                   List<Type> argtypes, List<Type> typeargtypes,
                   boolean allowBoxing, boolean useVarargs) {
        return super.findFun(env, name, argtypes, typeargtypes, allowBoxing, useVarargs);
    }

    @Override
    Symbol resolveMethod(JCDiagnostic.DiagnosticPosition pos,
                         Env<AttrContext> env,
                         Name name,
                         List<Type> argtypes,
                         List<Type> typeargtypes) {
        System.out.println("resolveMethod:" + env.tree);
        return lookupMethod(env, pos, env.enclClass.sym, resolveMethodCheck,
                new MyBasicLookupHelper(name, env, argtypes, typeargtypes));
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

        Type descriptorType;

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
            if (symbol != null) {
                if (!TreeInfo.isStaticSelector(referenceTree.expr, names)) {
                    Symbol method2 = method;
                    for (Symbol.MethodSymbol methodSymbol : findRedirectMethod(name)) {
                        final List<Attribute.Class> methodStaticExType = getMethodStaticExType(names, methodSymbol);
                        List<Type> newTypeArgTypes = typeargtypes;
                        List<Type> newArgTypes = argtypes;
                        final List<Symbol.VarSymbol> nParams = methodSymbol.params();
                        if (nParams.size() == 0) continue;
                        if (!types.isCastable(site, nParams.get(0).type)) {
                            continue;
                        }
                        ListBuffer listBuffer = new ListBuffer();
                        nParams.stream().skip(1).map(a -> a.type).forEach(listBuffer::append);
                        final Type returnType = ((Symbol.MethodSymbol) methodSymbol).type.getReturnType();
                        Type.MethodType methodType = new Type.MethodType(listBuffer.toList(), returnType, List.nil(), oSite.tsym);
                        Symbol.MethodSymbol tempSymbol = new Symbol.MethodSymbol(PUBLIC, name, methodType, oSite.tsym);
                        System.out.println("start Check=" + tempSymbol);
                        System.out.println("returnResult=" + env.info.returnResult.checkContext.inferenceContext());
//                        currentResolutionContext = new Resolve.MethodResolutionContext();
//                        currentResolutionContext.step = methodResolutionSteps.head;
                        throw new NeedRedirectMethod(methodSymbol);
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
                } else {
                    System.out.println("TreeInfo.symbol :" + symbol + "   [" + symbol.getClass());
                }
            }
            System.out.println("oSite: " + oSite);
            Symbol method2 = findMethod2(env, oSite, name, argtypes, typeargtypes,
                    method,
                    phase.isBoxingRequired(),
                    phase.isVarargsRequired());
            System.out.println("method : " + method + " best? " + method2);
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
                        final List<Attribute.Class> methodStaticExType = getMethodStaticExType(names, methodSymbol);
                        List<Type> newTypeArgTypes = typeargtypes;
                        List<Type> newArgTypes = argtypes;
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
                System.out.println("TreeInfo.symbol :" + symbol + "   [" + symbol.getClass());
                if (sym == methodNotFound && !TreeInfo.isStaticSelector(referenceTree.expr, names)) {
//                    throw new NeedLowerLambda(site, qualifierExpression, name);
                }
            } else {
                System.out.println("TreeInfo.symbol :" + symbol);

            }
            return super.access(env, pos, location, sym);
        }

        @Override
        ReferenceLookupHelper unboundLookup(InferenceContext inferenceContext) {
            System.out.println("unboundLookup:" + inferenceContext.inferenceVars());
            return helper.unboundLookup(inferenceContext);
        }

        @Override
        JCTree.JCMemberReference.ReferenceKind referenceKind(Symbol sym) {
            return helper.referenceKind(sym);
        }
    }

    @Override
    Pair<Symbol, ReferenceLookupHelper> resolveMemberReference(Env<AttrContext> env, JCTree.JCMemberReference referenceTree, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, Type descriptor, MethodCheck methodCheck, InferenceContext inferenceContext, ReferenceChooser referenceChooser) {
        System.out.println("env:" + env + "  referenceTree:" + referenceTree + "  site:" + site + "  argtypes:" + argtypes + "  typeargtypes:" + typeargtypes + "  descriptor:" + descriptor);
        System.out.println("resolveMemberReference:" + env.tree);
        System.out.println("resolveMemberReference inferenceVars:" + inferenceContext.inferenceVars());
        return super.resolveMemberReference(env, referenceTree, site, name, argtypes, typeargtypes, descriptor, methodCheck, inferenceContext, referenceChooser);
    }

    @Override
    Symbol resolveQualifiedMethod(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        System.out.println("resolveQualifiedMethod:" + env.tree);
        return super.resolveQualifiedMethod(pos, env, site, name, argtypes, typeargtypes);
    }

    @Override
    Symbol resolveDiamond(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Type site, List<Type> argtypes, List<Type> typeargtypes) {
        System.out.println("resolveDiamond:" + env.tree);
        return super.resolveDiamond(pos, env, site, argtypes, typeargtypes);
    }

    @Override
    Symbol lookupMethod(Env<AttrContext> env, JCDiagnostic.DiagnosticPosition pos, Symbol location, MethodCheck methodCheck, LookupHelper lookupHelper) {
        System.out.println("env:" + env + "  location:" + location);
        System.out.println("lookupMethod:" + env.tree);
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
            System.out.println("==============doLookup  site:" + site + " name:" + name + " phase:" + phase);
            final Symbol bestSoFar = findMethod(env, site, name, argtypes, typeargtypes,
                    phase.isBoxingRequired(),
                    phase.isVarargsRequired());
            final Symbol newSymbol = findMethod2(env, site, name, argtypes, typeargtypes,
                    bestSoFar,
                    phase.isBoxingRequired(),
                    phase.isVarargsRequired());
            if (bestSoFar instanceof AmbiguityError) {
                System.out.println("method1: " + ((AmbiguityError) bestSoFar).ambiguousSyms + " [" + bestSoFar.kind.isValid());
            } else
                System.out.println("method1: " + bestSoFar + " [" + bestSoFar.kind.isValid());

            if (newSymbol instanceof AmbiguityError) {
                System.out.println("method2: " + ((AmbiguityError) newSymbol).ambiguousSyms + " [" + newSymbol.kind.isValid());
            } else
                System.out.println("method2: " + newSymbol + " [" + newSymbol.kind.isValid());

            if ((newSymbol.kind.isValid() && !bestSoFar.kind.isValid())
                    || (newSymbol.kind.isValid() && bestSoFar.kind.isValid() && newSymbol != bestSoFar)) {
                throw new NeedRedirectMethod(newSymbol);
            } else {
                return newSymbol;
            }
        }

        @Override
        Symbol access(Env<AttrContext> env, JCDiagnostic.DiagnosticPosition pos, Symbol location, Symbol sym) {
            System.out.println("==============access  location:" + location + "  env:" + env.tree + "  location:" + location + " sym:" + sym);
            System.out.println("argtypes  :" + argtypes);
            System.out.println("typeargtypes  :" + typeargtypes);
            System.out.println("sym  :" + sym);
            System.out.println("sym.kind  :" + sym.kind);

            try {
                sym = super.access(env, pos, location, sym);
            } catch (Exception e) {
                System.err.println("tree " + env.tree);
                System.err.println("error " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            System.out.println("access result  " + sym + " [" + sym.type + " [" + sym.type.getClass());
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
            long startTime = System.currentTimeMillis();
            final Map<Name, Map<Symbol.ModuleSymbol, Symbol.PackageSymbol>> allPackages = ReflectionUtil.getDeclaredField(syms, Symtab.class, "packages");
            System.out.println("======allPackages：" + allPackages);
            final String clazzName = "zircon.ExMethod";
            for (Map<Symbol.ModuleSymbol, Symbol.PackageSymbol> packageSymPair : new ArrayList<>(allPackages.values())) {
                packageSymPair.values().stream()
                        .filter(packageSymbol -> Stream.of("java.util", "com.sun", "sun", "jdk", "org.junit", "java.io", "java.nio", "java.lang", "java.security", "java.net")
                                .noneMatch(a -> packageSymbol.fullname.toString().startsWith(a)))
                        .forEach(packageSymbol -> {
                            System.out.println("======packageSymbol：" + packageSymbol.fullname);
                            final List<Symbol> enclosedElements = packageSymbol.getEnclosedElements();
                            enclosedElements.stream().filter(e -> e instanceof Symbol.ClassSymbol).forEach(e -> {
                                final Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) e;
                                System.out.println("find in " + classSymbol + "[" + classSymbol.getClass());
                                classSymbol.members().getSymbols(symbol -> symbol instanceof Symbol.MethodSymbol && symbol.getAnnotationMirrors().stream().anyMatch(annotation -> annotation.type.toString().equals(clazzName)))
                                        .forEach(symbol -> {
                                            Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
                                            System.out.println("find method " + method + "[" + method.getSimpleName() + "[" + method.type);
                                            final List<Symbol.MethodSymbol> list = redirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                                            redirectMethodSymbolMap.put(method.getSimpleName(), list.append(method));
                                        });
                            });
                        });
            }
            System.out.println("======search end");
            System.out.println(redirectMethodSymbolMap.values());
            System.out.println("扫描耗时:" + (System.currentTimeMillis() - startTime) + "ms");
        }
        final List<Symbol.MethodSymbol> list = redirectMethodSymbolMap.get(methodName);
        return list == null ? List.nil() : list;
    }

    public static List<Attribute.Class> getMethodStaticExType(Names names, Symbol.MethodSymbol symbol) {
        final String clazz = "zircon.ExMethod";
        final Optional<Attribute.Compound> exMethod = symbol.getAnnotationMirrors().stream().filter(annotation -> annotation.type.toString().equals(clazz)).findFirst();
        if (exMethod.isPresent()) {
            final Attribute.Compound compound = exMethod.get();
            final Attribute ex = compound.member(names.fromString("ex"));
            if (ex != null) {
                System.out.println("ex.getValue().class=" + ex.getValue().getClass());
                @SuppressWarnings("unchecked") final List<Attribute.Class> value = (List<Attribute.Class>) ex.getValue();
                return value;
            }
        }
        return List.nil();
    }

    public static class CoverTree extends RuntimeException {
        public CoverTree(Env<AttrContext> env, Attr.ResultInfo resultInfo) {
            this.env = env;
            this.resultInfo = resultInfo;
        }

        Env<AttrContext> env;
        Attr.ResultInfo resultInfo;
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

    public static class NeedLowerLambda extends RuntimeException {
        Type site;

        public NeedLowerLambda(Type site, JCTree.JCExpression qualifierExpression, Name name) {
            this.site = site;
            this.qualifierExpression = qualifierExpression;
            this.name = name;
        }

        JCTree.JCExpression qualifierExpression;
        Name name;

    }

    @Override
    Symbol getMemberReference(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, JCTree.JCMemberReference referenceTree, Type site, Name name) {
        return super.getMemberReference(pos, env, referenceTree, site, name);
    }

    Symbol selectBestFromList(List<Symbol.MethodSymbol> methodSymbolList, Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes,
                              Symbol bestSoFar,
                              boolean allowBoxing,
                              boolean useVarargs) {
        for (Symbol.MethodSymbol methodSymbol : methodSymbolList) {
            final List<Attribute.Class> methodStaticExType = getMethodStaticExType(names, methodSymbol);
            List<Type> newTypeArgTypes = typeargtypes;
            List<Type> newArgTypes = argtypes;
            if (methodStaticExType.isEmpty()) {
                if (typeargtypes == null) newTypeArgTypes = List.nil();
                newArgTypes = newArgTypes.prepend(site);
                bestSoFar = selectBest(env, methodSymbol.owner.type, newArgTypes, newTypeArgTypes, methodSymbol,
                        bestSoFar, allowBoxing, useVarargs);
            } else {
                System.out.println("class=" + methodStaticExType.head.classType + " site=" + site);
                if (methodStaticExType.stream().anyMatch(a -> a.classType.equals(site))) {
                    System.out.println("ex type=" + site.toString());
                    bestSoFar = selectBest(env, site, newArgTypes, newTypeArgTypes, methodSymbol,
                            bestSoFar, allowBoxing, useVarargs);
                }
            }

        }
        return bestSoFar;
    }

    @Override
    Type rawInstantiate(Env<AttrContext> env,
                        Type site,
                        Symbol m,
                        Attr.ResultInfo resultInfo,
                        List<Type> argtypes,
                        List<Type> typeargtypes,
                        boolean allowBoxing,
                        boolean useVarargs,
                        Warner warn) throws Infer.InferenceException {
        Type mt = types.memberType(site, m);
        // tvars is the list of formal type variables for which type arguments
        // need to inferred.
        List<Type> tvars = List.nil();
        if (typeargtypes == null) typeargtypes = List.nil();
        if (!mt.hasTag(FORALL) && typeargtypes.nonEmpty()) {
            // This is not a polymorphic method, but typeargs are supplied
            // which is fine, see JLS 15.12.2.1
        } else if (mt.hasTag(FORALL) && typeargtypes.nonEmpty()) {
            Type.ForAll pmt = (Type.ForAll) mt;
            if (typeargtypes.length() != pmt.tvars.length()) {
                new Exception().printStackTrace();
                System.out.println("InapplicableMethodException:" + env.tree + "  typeargtypes:" + typeargtypes + "  typeargtypes.length():" + typeargtypes.length());
                System.out.println("InapplicableMethodException:  pmt:" + pmt);
                System.out.println("InapplicableMethodException:  pmt.tvars:" + pmt.tvars);
                // not enough args
                throw new InapplicableMethodException(diags.fragment(CompilerProperties.Fragments.WrongNumberTypeArgs(Integer.toString(pmt.tvars.length()))));
            }
            // Check type arguments are within bounds
            List<Type> formals = pmt.tvars;
            List<Type> actuals = typeargtypes;
            while (formals.nonEmpty() && actuals.nonEmpty()) {
                List<Type> bounds = types.subst(types.getBounds((Type.TypeVar) formals.head),
                        pmt.tvars, typeargtypes);
                for (; bounds.nonEmpty(); bounds = bounds.tail) {
                    if (!types.isSubtypeUnchecked(actuals.head, bounds.head, warn)) {
                        throw new InapplicableMethodException(diags.fragment(CompilerProperties.Fragments.ExplicitParamDoNotConformToBounds(actuals.head, bounds)));
                    }
                }
                formals = formals.tail;
                actuals = actuals.tail;
            }
            mt = types.subst(pmt.qtype, pmt.tvars, typeargtypes);
        } else if (mt.hasTag(FORALL)) {
            Type.ForAll pmt = (Type.ForAll) mt;
            List<Type> tvars1 = types.newInstances(pmt.tvars);
            tvars = tvars.appendList(tvars1);
            mt = types.subst(pmt.qtype, pmt.tvars, tvars1);
        }

        // find out whether we need to go the slow route via infer
        boolean instNeeded = tvars.tail != null; /*inlined: tvars.nonEmpty()*/
        for (List<Type> l = argtypes;
             l.tail != null/*inlined: l.nonEmpty()*/ && !instNeeded;
             l = l.tail) {
            if (l.head.hasTag(FORALL)) instNeeded = true;
        }

        if (instNeeded) {
            return infer.instantiateMethod(env,
                    tvars,
                    (Type.MethodType) mt,
                    resultInfo,
                    (Symbol.MethodSymbol) m,
                    argtypes,
                    allowBoxing,
                    useVarargs,
                    currentResolutionContext,
                    warn);
        }

        DeferredAttr.DeferredAttrContext dc = currentResolutionContext.deferredAttrContext(m, infer.emptyContext, resultInfo, warn);
        currentResolutionContext.methodCheck.argumentsAcceptable(env, dc,
                argtypes, mt.getParameterTypes(), warn);
        dc.complete();
        return mt;
    }

    protected Symbol findMethod(Env<AttrContext> env,
                                Type site,
                                Name name,
                                List<Type> argtypes,
                                List<Type> typeargtypes,
                                boolean allowBoxing,
                                boolean useVarargs) {
//        new Exception(name.toString()).printStackTrace();
        return super.findMethod(env, site, name, argtypes, typeargtypes, allowBoxing, useVarargs);
    }

    protected Symbol findMethod2(Env<AttrContext> env,
                                 Type site,
                                 Name name,
                                 List<Type> argtypes,
                                 List<Type> typeargtypes,
                                 Symbol bestSoFar,
                                 boolean allowBoxing,
                                 boolean useVarargs) {
        System.out.println("====findMethod name=" + name
                + "  ;env.tree:" + env.tree + "[" + env.tree.getClass()
                + "  ;site=" + site
                + "  ;receiver=" + site.getTypeArguments()
                + "  ;" + "argtypes=" + argtypes + ";" + "typeargtypes=" + typeargtypes);
        final List<Symbol.MethodSymbol> redirectMethod = findRedirectMethod(name);
        if (redirectMethod != null && !redirectMethod.isEmpty()) {
            return selectBestFromList(redirectMethod, env, site, argtypes, typeargtypes, bestSoFar, allowBoxing, useVarargs);
        } else {
            return bestSoFar;
        }
    }
//            System.out.println("======method Found：" + redirectMethod);
//            System.out.println("======env：" + env + "[" + env.getClass());
//            System.out.println("======env.next：" + env.next + "[" + env.next.getClass());
//            System.out.println("======env.outer：" + env.outer + "[" + env.outer.getClass());
//            System.out.println("======env.tree：" + env.tree + "[" + env.tree.getClass());
//            if (env.tree instanceof JCTree.JCMethodInvocation) {
//                final JCTree.JCMethodInvocation methodInvocation = (JCTree.JCMethodInvocation) env.tree;
//                System.out.println("======Arguments：" + methodInvocation.getArguments());
//                System.out.println("======TypeArguments：" + methodInvocation.getTypeArguments());
//                System.out.println("======MethodSelect：" + methodInvocation.getMethodSelect() + "[" + methodInvocation.getMethodSelect().getClass());
//                if (methodInvocation.getMethodSelect() instanceof JCTree.JCFieldAccess) {
//                    final JCTree.JCFieldAccess methodSelect = (JCTree.JCFieldAccess) methodInvocation.getMethodSelect();
//                    methodInvocation.getArguments().prepend(methodSelect.selected);
//                    methodInvocation.getTypeArguments().prepend(methodInvocation.getMethodSelect()).prepend(methodSelect.selected);
//                } else {
//                    System.err.println("unknown MethodSelect:" + methodInvocation.getMethodSelect().getClass());
//                }
//
//            } else {
//                System.err.println("unknown treeType:" + env.tree.getClass());
//            }
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
//            return redirectMethod.get(0);
//        if (bestSoFar.kind.ordinal() >= Kinds.Kind.ERR.ordinal()) {
//            if (name.toString().equals("add")&&argtypes.size()==2){
//                final List<Type> tail = argtypes.tail;
//                tail.add(0,site);
//                bestSoFar = findMethod(env, findType(env,names.fromString("test.TestClass2.Test")).type, names.fromString("add"), tail, null, true, false);
//            }
//        }

    private class MyBasicLookupHelper extends BasicLookupHelper {
        public MyBasicLookupHelper(Name name, Env<AttrContext> env, List<Type> argtypes, List<Type> typeargtypes) {
            super(name, env.enclClass.sym.type, argtypes, typeargtypes);
        }

        @Override
        Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            return findFun(env, name, argtypes, typeargtypes,
                    phase.isBoxingRequired(),
                    phase.isVarargsRequired());
        }
    }
}
