package com.sun.tools.javac.comp;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.*;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static com.sun.tools.javac.code.Flags.PARAMETER;
import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.TypeTag.NONE;

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
            final Symbol method = findMethod(env, site, name, argtypes, typeargtypes,
                    phase.isBoxingRequired(), phase.isVarargsRequired());
//            final Symbol symbol = TreeInfo.symbol(((JCTree.JCMemberReference) env.tree).getQualifierExpression());
            if (!TreeInfo.isStaticSelector(referenceTree.expr, names)) {
                Symbol method2 = method;
                for (ExMethodInfo methodInfo : findRedirectMethod(name)) {
                    final List<Symbol.VarSymbol> nParams = methodInfo.methodSymbol.params();
                    if (nParams.size() == 0) continue;
                    if (!types.isCastable(site, nParams.get(0).type)) {
                        continue;
                    }
                    String lambda = createLambdaTree(referenceTree, methodInfo.methodSymbol).toString();
                    final RuntimeException runtimeException = new RuntimeException("搜索到被拓展的非静态方法引用：" + referenceTree + "\n暂不支持该拓展形式,请替换为lambda表达式：\n" + lambda);
                    runtimeException.setStackTrace(new StackTraceElement[0]);
                    throw runtimeException;
                }
                return method2;
            }
            Symbol method2 = findMethod2(env, oSite, name, argtypes, typeargtypes,
                    method,
                    phase.isBoxingRequired(),
                    phase.isVarargsRequired());
            if (!method2.exists() && !(method2 instanceof AmbiguityError)) method2 = method;
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




    private JCTree.JCLambda createLambdaTree(JCTree.JCMemberReference memberReference, Symbol.MethodSymbol bestSoFar) {
        final JCTree.JCLambda lambda;
        final TreeMaker maker = TreeMaker.instance(context);
        final Name nameA = names.fromString("$zr$a");
        Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA
                , bestSoFar.params.get(1).type, syms.noSymbol);
        final JCTree.JCIdent idA = maker.Ident(symA);
        final List<JCTree.JCExpression> of = List.of(memberReference.getQualifierExpression(), idA);
        final JCTree.JCFieldAccess add = maker.Select(maker.Ident(bestSoFar.owner), bestSoFar.name);
        final JCTree.JCMethodInvocation apply = maker.Apply(memberReference.typeargs, add, of);
//                        apply.setType(bestSoFar.getReturnType());
        JCTree.JCVariableDecl a = maker.VarDef(symA, null);
        lambda = maker.Lambda(List.of(a), apply);
        return lambda;
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
            if ((newSymbol instanceof Symbol.MethodSymbol && !(bestSoFar instanceof Symbol.MethodSymbol))
                    || ((newSymbol instanceof Symbol.MethodSymbol) && (bestSoFar instanceof Symbol.MethodSymbol) && newSymbol != bestSoFar)) {
                throw new NeedRedirectMethod(newSymbol);
            } else {
                return newSymbol;
            }
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

        @Override
        public String toString() {
            return "ExMethodInfo{" +
                    "methodSymbol=" + methodSymbol +
                    ", isStatic=" + isStatic +
                    ", cover=" + cover +
                    ", targetType=" + targetType +
                    ", targetClass=" + targetClass +
                    '}';
        }

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
    public synchronized List<ExMethodInfo> findRedirectMethod(Name methodName) {
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
                            final java.util.List<Symbol> enclosedElements;
                            try {
                                enclosedElements = packageSymbol.getEnclosedElements();
                            } catch (Exception e) {
                                System.err.println("[warn] scan enclosedElements fail:" + e.getMessage());
                                return;
                            }
                            enclosedElements.stream().filter(e -> e instanceof Symbol.ClassSymbol).forEach(e -> {
                                final Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) e;
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
        final List<ExMethodInfo> redirectMethod = findRedirectMethod(name);
        if (redirectMethod != null && !redirectMethod.isEmpty()) {
            return selectBestFromList(redirectMethod, env, site, argtypes, typeargtypes, bestSoFar, allowBoxing, useVarargs);
        } else {
            return bestSoFar;
        }
    }


}