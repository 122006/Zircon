package com.sun.tools.javac.comp;

import static com.sun.tools.javac.code.Flags.PARAMETER;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrConstants;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class ZrResolve extends Resolve {

    private Symbol methodNotFound = ReflectionUtil.getDeclaredField(this, Resolve.class, "methodNotFound");
    Context context;

    protected ZrResolve(Context context) {
        super(context);
        this.context = context;
    }

    public static ZrResolve instance(Context context) {
        Resolve res = context.get(resolveKey);
        if (res instanceof ZrResolve) return (ZrResolve) res;
        context.put(resolveKey, (Resolve) null);
        final ZrResolve zrResolve = new ZrResolve(context);
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
                for (ExMethodInfo methodInfo : findRedirectMethod(name, methodSymbolEnable(method))) {
                    final List<Symbol.VarSymbol> nParams = methodInfo.methodSymbol.params();
                    if (nParams.size() == 0) continue;
                    if (!types.isCastable(site, nParams.get(0).type)) {
                        continue;
                    }
                    final JCTree.JCLambda lambdaTree = createLambdaTree(referenceTree, methodInfo);
                    throw new NeedReplaceLambda(lambdaTree, referenceTree, methodInfo);
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

    @Override
    Symbol resolveMethod(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        return this.lookupMethod(env, pos, env.enclClass.sym, (MethodCheck) this.resolveMethodCheck, new ZrLookupHelper2(name, env.enclClass.sym.type, argtypes, typeargtypes));
    }

    protected class ZrLookupHelper2 extends BasicLookupHelper {

        ZrLookupHelper2(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
            super(name, site, argtypes, typeargtypes);
        }

        @Override
        Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            final Symbol bestSoFar = findFun(env, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired());
            final Symbol method2 = findMethod2(env, site, name, argtypes, typeargtypes, bestSoFar, phase.isBoxingRequired(), phase.isVarargsRequired(), false);
            if (method2 != bestSoFar && methodSymbolEnable(method2)) {
                throw new NeedRedirectMethod(method2);
            }
            return bestSoFar;
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
        return resolveQualifiedMethod(new MethodResolutionContext(), pos, env, location, site, name, argtypes, typeargtypes);
    }

    protected class ZrLookupHelper extends BasicLookupHelper {

        ZrLookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
            super(name, site, argtypes, typeargtypes);
        }

        @Override
        Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
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

    Map<Name, List<ExMethodInfo>> redirectMethodSymbolMap = null;
    Map<Name, List<ExMethodInfo>> coverStaticRedirectMethodSymbolMap = null;
    ArrayList<Name> hasScan = new ArrayList<>();
    int lastScanMapCount = 0;
    boolean scanEl = false;

    @SuppressWarnings("unchecked")
    public synchronized List<ExMethodInfo> findRedirectMethod(Name methodName, boolean onlyCover) {
        if (redirectMethodSymbolMap == null) {
            redirectMethodSymbolMap = new HashMap<>();
        }
        if (coverStaticRedirectMethodSymbolMap == null) {
            coverStaticRedirectMethodSymbolMap = new HashMap<>();
        }
        final Map<Name, Map<Symbol.ModuleSymbol, Symbol.PackageSymbol>> allPackages = ReflectionUtil.getDeclaredField(syms, Symtab.class, "packages");
        if (lastScanMapCount != allPackages.size()) {
            do {
                scanEl = false;
                final ArrayList<Name> names = new ArrayList<>(allPackages.keySet());
                names.stream().filter(name -> ZrConstants.exMethodIgnorePackages.stream().noneMatch(a -> name.toString()
                                                                                                             .startsWith(a)))
                     .filter(name -> hasScan.stream().noneMatch(a -> Objects.equals(name, a)))
                     .forEach(name -> {
                         final Map<Symbol.ModuleSymbol, Symbol.PackageSymbol> moduleSymbolPackageSymbolMap = allPackages.get(name);
                         moduleSymbolPackageSymbolMap.values().forEach(a -> {
                             final java.util.List<Symbol> enclosedElements;
                             try {
                                 enclosedElements = a.getEnclosedElements();
                             } catch (Exception e) {
                                 return;
                             }
                             enclosedElements.stream().filter(e -> e instanceof Symbol.ClassSymbol).forEach(c -> {
                                 final Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) c;
                                 scanMethod(classSymbol);
                             });
                         });
                         scanEl = true;
                         hasScan.add(name);
                     });
            } while (scanEl);
            lastScanMapCount = allPackages.size();
        }
        final List<ExMethodInfo> list = (onlyCover ? coverStaticRedirectMethodSymbolMap : redirectMethodSymbolMap).get(methodName);
        return list == null ? List.nil() : list;
    }

    private void scanMethod(Symbol.ClassSymbol classSymbol) {
        final String clazzName = "zircon.ExMethod";
        final Scope.WriteableScope members = classSymbol.members();
        members.getSymbols(symbol -> symbol instanceof Symbol.ClassSymbol).forEach(c -> {
            final Symbol.ClassSymbol c1 = (Symbol.ClassSymbol) c;
            scanMethod(c1);
        });
        members.getSymbols(symbol -> symbol instanceof Symbol.MethodSymbol && symbol.getAnnotationMirrors().stream()
                                                                                    .anyMatch(annotation -> annotation.type
                                                                                            .toString()
                                                                                            .equals(clazzName)))
               .forEach(symbol -> {
                   Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
                   symbol.getAnnotationMirrors().stream()
                         .filter(annotation -> annotation.type.toString().equals(clazzName)).findFirst()
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
                                 if (exMethodInfo.cover) {
                                     final List<ExMethodInfo> list = coverStaticRedirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                                     coverStaticRedirectMethodSymbolMap.put(method.getSimpleName(), list.append(exMethodInfo));
                                 }
                                 final List<ExMethodInfo> list = redirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                                 redirectMethodSymbolMap.put(method.getSimpleName(), list.append(exMethodInfo));
                             } catch (Exception exc) {
                                 exc.printStackTrace();
                             }
                         });
               });
    }

    public static List<Attribute.Class> getMethodStaticExType(Names names, Symbol.MethodSymbol symbol) {
        final String clazz = "zircon.ExMethod";
        final Optional<Attribute.Compound> exMethod = symbol.getAnnotationMirrors().stream()
                                                            .filter(annotation -> annotation.type.toString()
                                                                                                 .equals(clazz))
                                                            .findFirst();
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

    }

    public static class NeedReplaceLambda extends RuntimeException {
        public NeedReplaceLambda(JCTree.JCLambda bestSoFar, JCTree.JCMemberReference memberReference, ExMethodInfo methodInfo) {
            super("搜索到不支持且被拓展的非静态方法引用：" + memberReference + "\n暂不支持该拓展形式,请替换为lambda表达式：\n" + bestSoFar + "\n请至github联系开发者以修复该情况");
            this.bestSoFar = bestSoFar;
            this.memberReference = memberReference;
            this.methodInfo = methodInfo;
        }

        JCTree.JCLambda bestSoFar;
        JCTree.JCMemberReference memberReference;
        ExMethodInfo methodInfo;
    }

    Symbol selectBestFromList(List<ExMethodInfo> methodSymbolList, Env<AttrContext> env, Type site, List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar, boolean allowBoxing, boolean useVarargs, boolean memberReference) {
        if (bestSoFar instanceof ResolveError && !(bestSoFar instanceof AmbiguityError)) bestSoFar = methodNotFound;
        java.util.List<List> newResult = new ArrayList<>();
        Symbol lastMethodSymbol = methodNotFound;
        exInfo:
        for (ExMethodInfo methodInfo : methodSymbolList) {
            List<Type> newArgTypes = argtypes;
            if (!methodInfo.isStatic) {
                Type type = methodInfo.methodSymbol.getParameters().head.type.baseType();
                type = types.erasure(type);
                if (!memberReference)
                    newArgTypes = newArgTypes.prepend(site);
                final Symbol best = selectBest(env, type, newArgTypes, typeargtypes, methodInfo.methodSymbol, lastMethodSymbol, allowBoxing, useVarargs);
                if (best == methodInfo.methodSymbol && best instanceof Symbol.MethodSymbol) {
                    lastMethodSymbol = methodInfo.methodSymbol;
                    newResult.add(List.of(methodInfo.methodSymbol.owner.type, methodInfo));
                    break;
                } else {
                    lastMethodSymbol = best;
                }
            } else {
                for (Attribute.Class clazz : methodInfo.targetClass) {
                    final Type type = clazz.classType.baseType();
                    final boolean sameType = type.equalsIgnoreMetadata(site);
                    if (sameType || types.isAssignable(site, type)) {
                        final Symbol best = selectBest(env, type, argtypes, typeargtypes, methodInfo.methodSymbol, lastMethodSymbol, allowBoxing, useVarargs);
                        if (best == methodInfo.methodSymbol && best instanceof Symbol.MethodSymbol) {
                            lastMethodSymbol = methodInfo.methodSymbol;
                            newResult.add(List.of(methodInfo.methodSymbol.owner.type, methodInfo));
                            break exInfo;
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
        List<ExMethodInfo> finalMethodSymbol = List.nil();
        final java.util.List<List> coverList = newResult.stream().filter(a -> ((ExMethodInfo) (a.get(1))).cover)
                                                        .collect(Collectors.toList());
        if (bestSoFar != methodNotFound && coverList.isEmpty()) {
            return bestSoFar;
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
            return methodNotFound;
        }
        if (finalMethodSymbol.size() == 1) {
            return finalMethodSymbol.head.methodSymbol;
        }
        if (memberReference) {
            AmbiguityError ambiguityError = new AmbiguityError(finalMethodSymbol.get(0).methodSymbol, finalMethodSymbol.get(1).methodSymbol);
            finalMethodSymbol.stream().skip(2).forEach(info -> ambiguityError.addAmbiguousSymbol(info.methodSymbol));
            return ambiguityError;
        } else {
            return finalMethodSymbol.head.methodSymbol;
        }
    }


    protected Symbol findMethod2(Env<AttrContext> env, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar, boolean allowBoxing, boolean useVarargs, boolean memberReference) {
        final List<ExMethodInfo> redirectMethod = findRedirectMethod(name, methodSymbolEnable(bestSoFar));
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
