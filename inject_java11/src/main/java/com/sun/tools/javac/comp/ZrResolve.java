package com.sun.tools.javac.comp;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrConstants;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    Map<Name, List<ExMethodInfo>> redirectMethodSymbolMap = null;

    @SuppressWarnings("unchecked")
    public synchronized List<ExMethodInfo> findRedirectMethod(Name methodName) {
        if (redirectMethodSymbolMap == null) {
            redirectMethodSymbolMap = new HashMap<>();
            long startTime = System.currentTimeMillis();
            final Map<Name, Map<Symbol.ModuleSymbol, Symbol.PackageSymbol>> allPackages = ReflectionUtil.getDeclaredField(syms, Symtab.class, "packages");
            final String clazzName = "zircon.ExMethod";
            for (Map<Symbol.ModuleSymbol, Symbol.PackageSymbol> packageSymPair : new ArrayList<>(allPackages.values())) {
                packageSymPair.values().stream()
                        .filter(packageSymbol -> ZrConstants.exMethodIgnorePackages.stream()
                                .noneMatch(a -> packageSymbol.fullname.toString().startsWith(a)))
                        .forEach(packageSymbol -> {
                            final java.util.List<Symbol> enclosedElements;
                            try {
                                enclosedElements = packageSymbol.getEnclosedElements();
                            } catch (Exception e) {
//                                System.err.println("[warn] scan enclosedElements fail:" + e.getMessage());
                                return;
                            }
                            enclosedElements.stream().filter(e -> e instanceof Symbol.ClassSymbol).forEach(e -> {
                                final Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) e;
                                classSymbol.members().getSymbols(symbol -> symbol instanceof Symbol.MethodSymbol && symbol.getAnnotationMirrors().stream().anyMatch(annotation -> annotation.type.toString().equals(clazzName)))
                                        .forEach(symbol -> {
                                            Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
                                            symbol.getAnnotationMirrors().stream().filter(annotation -> annotation.type.toString().equals(clazzName)).findFirst()
                                                    .ifPresent(compound -> {
                                                        try {
                                                            final ExMethodInfo exMethodInfo = new ExMethodInfo(method, false, false, List.nil(), List.nil());
                                                            final Attribute ex = compound.member(names.fromString("ex"));
                                                            if (ex != null && ((List<Attribute.Class>) ex.getValue()).size() > 0) {
                                                                exMethodInfo.targetClass = (List<Attribute.Class>) ex.getValue();
                                                            }
                                                            final Attribute cover = compound.member(names.fromString("cover"));
                                                            if (cover != null) {
                                                                exMethodInfo.cover = (boolean) cover.getValue();
                                                            }
                                                            exMethodInfo.isStatic = exMethodInfo.targetClass != null && exMethodInfo.targetClass.length() > 0;
//                                                            System.out.println("find method " + (exMethodInfo.isStatic ? "static " : "") + method + "[" + method.getSimpleName() + "[" + method.type);
                                                            final List<ExMethodInfo> list = redirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                                                            redirectMethodSymbolMap.put(method.getSimpleName(), list.append(exMethodInfo));
                                                        } catch (Exception exc) {
                                                            exc.printStackTrace();
                                                        }
                                                    });
                                        });
                            });
                        });
            }
            System.out.println("扫描耗时:" + (System.currentTimeMillis() - startTime) + "ms");
        }
        final List<ExMethodInfo> list = redirectMethodSymbolMap.get(methodName);
        return list == null ? List.nil() : list;
    }


    ReferenceLookupHelper makeReferenceLookupHelper(JCTree.JCMemberReference referenceTree, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
        if (!name.equals(names.init)) {
            //method reference
            return new ZrMethodReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        }
        return super.makeReferenceLookupHelper(referenceTree, site, name, argtypes, typeargtypes, maxPhase);
    }

    class ZrMethodReferenceLookupHelper extends ReferenceLookupHelper {

        MethodReferenceLookupHelper helper;
        Type oSite;


        ZrMethodReferenceLookupHelper(JCTree.JCMemberReference referenceTree, Name name, Type site, List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
            oSite = site;
            helper = new MethodReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        final Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            final Symbol method = findMethod(env, site, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired());
//            final Symbol symbol = TreeInfo.symbol(((JCTree.JCMemberReference) env.tree).getQualifierExpression());
            if (!TreeInfo.isStaticSelector(referenceTree.expr, names)) {
                Symbol method2 = method;
                for (ExMethodInfo methodInfo : findRedirectMethod(name)) {
                    final List<Symbol.VarSymbol> nParams = methodInfo.methodSymbol.params();
                    if (nParams.size() == 0) continue;
                    if (!types.isCastable(site, nParams.get(0).type)) {
                        continue;
                    }
                    String lambda = createLambdaTree(referenceTree, methodInfo).toString();
                    final RuntimeException runtimeException = new RuntimeException("搜索到被拓展的非静态方法引用：" + referenceTree + "\n暂不支持该拓展形式,请替换为lambda表达式：\n" + lambda);
                    runtimeException.setStackTrace(new StackTraceElement[0]);
                    throw runtimeException;
                }
                return method2;
            }
            Symbol method2 = findMethod2(env, oSite, name, argtypes, typeargtypes, method, phase.isBoxingRequired(), phase.isVarargsRequired(), true);
            if (!methodSymbolEnable(method2)) method2 = method;
            return method2;
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


    private JCTree.JCLambda createLambdaTree(JCTree.JCMemberReference memberReference, ExMethodInfo methodInfo) {
        final JCTree.JCLambda lambda;
        final TreeMaker maker = TreeMaker.instance(context);
        final List<Symbol.VarSymbol> params = methodInfo.methodSymbol.params;
        if (methodInfo.isStatic) {
            ListBuffer<JCTree.JCVariableDecl> jcVariableDecls = new ListBuffer<>();
            ListBuffer<JCTree.JCExpression> jcIdents = new ListBuffer<>();
            for (int i = 1; i < params.size(); i++) {
                Symbol.VarSymbol param = params.get(i);
                final Name nameA = names.fromString("$zr$a" + i);
                Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA, param.type, syms.noSymbol);
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
                Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA, param.type, syms.noSymbol);
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
        return resolveQualifiedMethod(new MethodResolutionContext(), pos, env, location, site, name, argtypes, typeargtypes);
    }

    protected class ZrLookupHelper extends BasicLookupHelper {

        ZrLookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
            super(name, site, argtypes, typeargtypes);
        }

        @Override
        Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
//            System.out.println("==============doLookup  site:" + site + " name:" + name + " phase:" + phase);
            final Symbol bestSoFar = findMethod(env, site, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired());
            final Symbol newSymbol = findMethod2(env, site, name, argtypes, typeargtypes, bestSoFar, phase.isBoxingRequired(), phase.isVarargsRequired(), false);
            if ((newSymbol instanceof Symbol.MethodSymbol && !(bestSoFar instanceof Symbol.MethodSymbol)) || ((newSymbol instanceof Symbol.MethodSymbol) && (bestSoFar instanceof Symbol.MethodSymbol) && newSymbol != bestSoFar)) {
                throw new NeedRedirectMethod(newSymbol);
            } else {
                return newSymbol;
            }
        }

    }

    private Symbol resolveQualifiedMethod(MethodResolutionContext resolveContext, JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
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
            return "ExMethodInfo{" + "methodSymbol=" + methodSymbol + ", isStatic=" + isStatic + ", cover=" + cover + ", targetType=" + targetType + ", targetClass=" + targetClass + '}';
        }

        public ExMethodInfo(Symbol.MethodSymbol methodSymbol, boolean isStatic, boolean cover, List<Type> targetType, List<Attribute.Class> targetClass) {
            this.methodSymbol = methodSymbol;
            this.isStatic = isStatic;
            this.cover = cover;
            this.targetType = targetType;
            this.targetClass = targetClass;
        }
    }


    public static List<Attribute.Class> getMethodStaticExType(Names names, Symbol.MethodSymbol symbol) {
        final String clazz = "zircon.ExMethod";
        final Optional<Attribute.Compound> exMethod = symbol.getAnnotationMirrors().stream().filter(annotation -> annotation.type.toString().equals(clazz)).findFirst();
        if (exMethod.isPresent()) {
            final Attribute.Compound compound = exMethod.get();
            final Attribute ex = compound.member(names.fromString("ex"));
            if (ex != null && ((List<Attribute.Class>) ex.getValue()).size() > 0) {
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
                              boolean useVarargs, boolean memberReference) {
        Symbol oBestSoFar = bestSoFar;
        boolean fCover = false;
        for (ExMethodInfo methodInfo : methodSymbolList) {
            if (methodSymbolEnable(oBestSoFar) && !methodInfo.cover) continue;
            if (fCover && !methodInfo.cover) continue;
            final List<Attribute.Class> methodStaticExType = methodInfo.targetClass;
            List<Type> newTypeArgTypes = typeargtypes;
            List<Type> newArgTypes = argtypes;
            if (typeargtypes == null) newTypeArgTypes = List.nil();
            final Type nSite;
            final Symbol lastSym;
            if (methodStaticExType.isEmpty()) {
                lastSym = !fCover && methodInfo.cover ? methodNotFound : bestSoFar;
                if (memberReference) {
                    if (methodInfo.methodSymbol.type.getParameterTypes().length() == 0) continue;
                    final Type head = methodInfo.methodSymbol.type.getParameterTypes().head;
                    if (!types.isAssignable(head, site)) continue;
                } else {
                    newArgTypes = newArgTypes.prepend(site);
                }
                nSite = memberReference ? methodInfo.methodSymbol.owner.type : site;
            } else {
                lastSym = !fCover && methodInfo.cover ? methodNotFound : bestSoFar;
                final Optional<Attribute.Class> aClass = methodStaticExType.stream().filter(a -> a.classType.equals(site.baseType())).findFirst();
                if (!aClass.isPresent()) continue;
                nSite = aClass.get().classType;
            }
            final Symbol findMethod = selectBest(env, nSite, newArgTypes, newTypeArgTypes, methodInfo.methodSymbol, lastSym, allowBoxing, useVarargs);
            if (methodInfo.cover) {
                if (findMethod == methodInfo.methodSymbol && methodSymbolEnable(findMethod)) {
                    bestSoFar = findMethod;
                    fCover = true;
                }
            } else {
                bestSoFar = findMethod;
            }
        }
        if (!methodSymbolEnable(bestSoFar)) return oBestSoFar;
        if (!methodSymbolEnable(oBestSoFar)) return bestSoFar;
        Symbol finalBestSoFar = bestSoFar;
        return methodSymbolList.stream().filter(a -> a.methodSymbol == finalBestSoFar).map(a -> a.cover).findFirst().orElse(false) ? bestSoFar : oBestSoFar;
    }


    protected Symbol findMethod2(Env<AttrContext> env, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar, boolean allowBoxing, boolean useVarargs, boolean memberReference) {
        final List<ExMethodInfo> redirectMethod = findRedirectMethod(name);
        if (redirectMethod != null && !redirectMethod.isEmpty()) {
            return selectBestFromList(redirectMethod, env, site, argtypes, typeargtypes, bestSoFar, allowBoxing, useVarargs, memberReference);
        } else {
            return bestSoFar;
        }
    }


    public boolean methodSymbolEnable(Symbol bestSoFar) {
        return bestSoFar instanceof Symbol.MethodSymbol || bestSoFar instanceof AmbiguityError;
    }
}
