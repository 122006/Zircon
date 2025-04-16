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
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.*;

import java.util.*;


@SuppressWarnings("rawtypes")
public class ZrResolve extends Resolve {

    protected Symbol methodNotFound;

    protected ZrResolve(Context context) {
        super(context);
        methodNotFound = ReflectionUtil.getDeclaredField(this, Resolve.class, "methodNotFound");
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
        final Map<Name, Symbol.PackageSymbol> allPackages = ReflectionUtil.getDeclaredField(classReader, ClassReader.class, "packages");
        if (lastScanMapCount != allPackages.size()) {
            do {
                scanEl = false;
                final ArrayList<Name> names = new ArrayList<>(allPackages.keySet());
                names.stream().filter(name -> ZrConstants.exMethodIgnorePackages.stream().noneMatch(a -> name.toString().startsWith(a)))
                        .filter(name -> hasScan.stream().noneMatch(a -> Objects.equals(name, a)))
                        .forEach(name -> {
                            Symbol.PackageSymbol packageSymbol = allPackages.get(name);
                            final java.util.List<Symbol> enclosedElements;
                            try {
                                enclosedElements = packageSymbol.getEnclosedElements();
                            } catch (Exception e) {
                                return;
                            }
                            enclosedElements.stream().filter(e -> e instanceof Symbol.ClassSymbol).forEach(c -> {
                                final Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) c;
                                scanMethod(classSymbol);
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
        final Scope members = classSymbol.members();
        members.getElements(symbol -> symbol instanceof Symbol.ClassSymbol).forEach(c -> {
            final Symbol.ClassSymbol c1 = (Symbol.ClassSymbol) c;
            scanMethod(c1);
        });
        members.getElements(symbol -> symbol instanceof Symbol.MethodSymbol && symbol.getAnnotationMirrors().stream().anyMatch(annotation -> annotation.type.toString().equals(clazzName))).forEach(symbol -> {
            Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
            symbol.getAnnotationMirrors().stream().filter(annotation -> annotation.type.toString().equals(clazzName)).findFirst().ifPresent(compound -> {
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
                    if (exMethodInfo.cover) {
                        final List<ExMethodInfo> list = coverStaticRedirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                        coverStaticRedirectMethodSymbolMap.put(method.getSimpleName(), list.append(exMethodInfo));
                    }
                    final List<ExMethodInfo> list = redirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                    redirectMethodSymbolMap.put(method.getSimpleName(), list.append(exMethodInfo));
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
                            if (newExMethodInfo.cover) {
                                final List<ExMethodInfo> pList = coverStaticRedirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                                coverStaticRedirectMethodSymbolMap.put(method.getSimpleName(), pList.append(newExMethodInfo));
                            }
                            final List<ExMethodInfo> plist = redirectMethodSymbolMap.getOrDefault(method.getSimpleName(), List.nil());
                            redirectMethodSymbolMap.put(method.getSimpleName(), plist.append(newExMethodInfo));
                        }
                    }
                } catch (Exception exc) {
                    exc.printStackTrace();
                }
            });
        });
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
        final List<ExMethodInfo> redirectMethod = findRedirectMethod(name, methodSymbolEnable(bestSoFar));
        if (redirectMethod != null && !redirectMethod.isEmpty()) {
            return ZrResolveEx.selectBestFromList(this, redirectMethod, env, site, argtypes, typeargtypes, bestSoFar, allowBoxing, useVarargs, memberReference, operator);

        } else {
            return Pair.of(bestSoFar, null);
        }
    }
    public static boolean equalsIgnoreMetadata(Type t1, Type t2) {
        return t1.baseType().equals(t2.baseType());
    }
}
