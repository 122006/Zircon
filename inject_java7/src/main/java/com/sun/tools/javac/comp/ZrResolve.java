package com.sun.tools.javac.comp;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrConstants;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;


@SuppressWarnings("rawtypes")
public class ZrResolve extends ZrResolveEx {

    protected Symbol methodNotFound;

    protected ZrResolve(Context context) {
        super(context);
        methodNotFound = ReflectionUtil.getDeclaredField(this, Resolve.class, "methodNotFound");
    }

    ClassReader classReader;


    public static ZrResolve instance(Context context) {
        Resolve res = context.get(resolveKey);
        if (res instanceof ZrResolve) return (ZrResolve) res;
        context.put(resolveKey, (Resolve) null);
        final ZrResolve zrResolve = new ZrResolve(context);
        zrResolve.context = context;
        zrResolve.classReader = ClassReader.instance(context);
        ReflectionUtil.setDeclaredField(Attr.instance(context), Attr.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(Check.instance(context), Check.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(DeferredAttr.instance(context), DeferredAttr.class, "rs", zrResolve);
        ReflectionUtil.setDeclaredField(JavacTrees.instance(context), JavacTrees.class, "resolve", zrResolve);
        return zrResolve;
    }


    ReferenceLookupHelper makeReferenceLookupHelper(JCTree.JCMemberReference referenceTree, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
        if (!name.equals(names.init)) {
            //method reference
            return new ZrMethodReferenceLookupHelper(this, referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        }
        return super.makeReferenceLookupHelper(referenceTree, site, name, argtypes, typeargtypes, maxPhase);
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


    private Symbol resolveQualifiedMethod(MethodResolutionContext resolveContext, JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        return lookupMethod(env, pos, location, resolveContext, new ZrLookupHelper(this, name, site, argtypes, typeargtypes));
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
            } catch (Exception ignored) {
            }
            try {
                for (Symbol currentSym : env.toplevel.namedImportScope.getElements(a -> a instanceof Symbol.ClassSymbol)) {
                    symbols.add(currentSym);
                }
            } catch (Exception ignored) {
            }
            try {
                for (Symbol currentSym : env.toplevel.starImportScope.getElements(a -> a instanceof Symbol.ClassSymbol)) {
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
        final Scope members = classSymbol.members();
        members.getElements(symbol -> symbol instanceof Symbol.ClassSymbol).forEach(c -> {
            final Symbol.ClassSymbol c1 = (Symbol.ClassSymbol) c;
            scanMethod(c1, result);
        });
        final String classQualifiedName = classSymbol.getQualifiedName().toString();
        java.util.List<ExMethodInfo> classAllMethod = exMethodCache.get(classQualifiedName);
        if (classAllMethod == null) {
            classAllMethod = new ArrayList<>();
            exMethodCache.put(classQualifiedName, classAllMethod);
            for (Symbol symbol1 : members.getElements(symbol -> symbol instanceof Symbol.MethodSymbol && symbol.getAnnotationMirrors().stream()
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

    @Override
    Symbol resolveMethod(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        return this.lookupMethod(env, pos, env.enclClass.sym, (MethodCheck) this.resolveMethodCheck, new ZrLookupHelper2(this, name, env.enclClass.sym.type, argtypes, typeargtypes));
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


    public boolean methodSymbolEnable(Symbol bestSoFar) {
        return bestSoFar instanceof Symbol.MethodSymbol || bestSoFar instanceof AmbiguityError;
    }

    protected Pair<Symbol, ExMethodInfo> findMethod2(Env<AttrContext> env, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar, boolean allowBoxing, boolean useVarargs, boolean operator, boolean memberReference) {
        final List<ExMethodInfo> redirectMethod = findRedirectMethod(env, name, methodSymbolEnable(bestSoFar));
        if (redirectMethod != null && !redirectMethod.isEmpty()) {
            return selectBestFromList(this, redirectMethod, env, site, argtypes, typeargtypes, bestSoFar, allowBoxing, useVarargs, memberReference, operator);

        } else {
            return Pair.of(bestSoFar, null);
        }
    }

    public static boolean equalsIgnoreMetadata(Type t1, Type t2) {
        return t1.baseType().equals(t2.baseType());
    }
}
