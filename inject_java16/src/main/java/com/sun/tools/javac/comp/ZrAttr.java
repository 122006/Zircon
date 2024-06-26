package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.parser.ZrUnSupportCodeError;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Optional;

public class ZrAttr extends Attr {
    private final Context context;

    protected ZrAttr(Context context) {
        super(context);
        this.context = context;
    }

    public static ZrAttr instance(Context context) {
        Attr res = context.get(attrKey);
        if (res instanceof ZrAttr) return (ZrAttr) res;
        context.put(attrKey, (Attr) null);
        final ZrAttr zrAttr = new ZrAttr(context);
        {
            final TypeEnter instance = TypeEnter.instance(context);
            if (get(instance, "attr") != null) {
                set(instance, "attr", zrAttr);
            }
        }
        {
            final ArgumentAttr instance = ArgumentAttr.instance(context);
            if (get(instance, "attr") != null) {
                set(instance, "attr", zrAttr);
            }
        }
        {
            final Resolve instance = Resolve.instance(context);
            if (get(instance, "attr") != null) {
                set(instance, "attr", zrAttr);
            }
        }
        {
            final DeferredAttr instance = DeferredAttr.instance(context);
            if (get(instance, "attr") != null) {
                set(instance, "attr", zrAttr);
            }
        }
        context.put(attrKey, zrAttr);

        return zrAttr;
    }


    @Override
    public void visitVarDef(JCTree.JCVariableDecl that) {
        try {
            super.visitVarDef(that);
        } catch (ZrResolve.NeedReplaceLambda needReplaceLambda) {
            JCTree.JCExpression initializer = that.getInitializer();
            while (initializer instanceof JCTree.JCParens) {
                initializer = ((JCTree.JCParens) initializer).getExpression();
            }
            if (Objects.equals(initializer.getStartPosition(), needReplaceLambda.memberReference.getStartPosition())) {
                that.init = needReplaceLambda.bestSoFar;
            }
            super.visitVarDef(that);
        }
    }
    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        Name methName = TreeInfo.name(that.meth);
        boolean isConstructorCall = methName == this.names._this || methName == this.names._super;
        if (isConstructorCall) {
            super.visitApply(that);
        } else
            visitNoConstructorApply(that);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void visitNoConstructorApply(JCTree.JCMethodInvocation that) {
        Env<AttrContext> localEnv = this.env.dup(that, ((AttrContext) this.env.info).dup());
        Name methName = TreeInfo.name(that.meth);
        ListBuffer<Type> argtypesBuf = new ListBuffer();
        Kinds.KindSelector kind;
        try {
            kind = this.attribArgs(Kinds.KindSelector.VAL, that.args, localEnv, argtypesBuf);
        } catch (ZrResolve.NeedReplaceLambda needReplaceLambda) {
            List<JCTree.JCExpression> newList = List.nil();
            for (int i = 0; i < that.args.size(); i++) {
                JCTree.JCExpression argument = that.args.get(i);
                while (argument instanceof JCTree.JCParens) {
                    argument = ((JCTree.JCParens) argument).getExpression();
                }
                if (Objects.equals(argument.getStartPosition(), needReplaceLambda.memberReference.getStartPosition())) {
                    newList = newList.append(needReplaceLambda.bestSoFar);
                } else {
                    newList = newList.append(argument);
                }
            }
            that.args = newList;
            kind = this.attribArgs(Kinds.KindSelector.VAL, that.args, localEnv, argtypesBuf);
        }
        List argtypes = argtypesBuf.toList();
        List<Type> typeargtypes = this.attribAnyTypes(that.typeargs, localEnv);
        final Type pt = this.resultInfo.pt;
        Type site = this.newMethodTemplate(pt, argtypes, typeargtypes);
        Type encl;
        try {
            encl = this.attribTree(that.meth, localEnv, new ResultInfo(kind, site, this.resultInfo.checkContext));
        } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
            final JCTree.JCMethodInvocation oldTree = make.Apply(that.typeargs, that.meth, that.args);
            final Symbol bestSoFar = redirectMethod.bestSoFar;
            final JCTree.JCFieldAccess add = make.Select(make.Ident(bestSoFar.owner), bestSoFar.name);
            final List<Attribute.Class> methodStaticExType = ZrResolve.getMethodStaticExType(names, (Symbol.MethodSymbol) bestSoFar);
            if (methodStaticExType.isEmpty()) {
                if (that.meth instanceof JCTree.JCFieldAccess) {
                    final JCTree.JCExpression prepend = ((JCTree.JCFieldAccess) that.meth).selected;
                    that.args = that.args.prepend(prepend);
                    argtypes = argtypes.prepend(prepend.type);
                } else if (that.meth instanceof JCTree.JCIdent) {
                    final Type enclClassType = localEnv.enclClass.type;
                    final JCTree.JCExpression ident = make.This(enclClassType);
                    that.args = that.args.prepend(ident);
                    argtypes = argtypes.prepend(ident.type);
                }
            }
            site = this.newMethodTemplate(pt, argtypes, typeargtypes);
            JCTree.JCExpression oldMeth = that.meth;
            that.meth = add;
            that.type = redirectMethod.bestSoFar.type;
            if (oldMeth.hasTag(JCTree.Tag.SELECT)) {
                final JCTree.JCExpression selected = ((JCTree.JCFieldAccess) oldMeth).selected;
                final boolean staticInvoke = selected.hasTag(JCTree.Tag.IDENT) || TreeInfo.isStaticSelector(selected, names);
                if (!staticInvoke) {
                    final Optional<ZrResolve.ExMethodInfo> first = ((ZrResolve) rs)
                            .findRedirectMethod(bestSoFar.getSimpleName(), false).stream()
                            .filter(a -> a.methodSymbol == bestSoFar)
                            .findFirst();
                    if (first.isPresent()) {
                        if (first.get().isStatic) {
                            if (((Symbol.MethodSymbol) bestSoFar).getReturnType().hasTag(TypeTag.VOID)) {
                                throw new ZrUnSupportCodeError("对实例对象调用无返回值的静态方法", oldTree);
                            } else {
                                final Symbol.ClassSymbol biopClass = syms.getClass(syms.unnamedModule, names.fromString("zircon.BiOp"));
                                final JCTree.JCFieldAccess and = make.Select(make.QualIdent(biopClass), names.fromString("sec"));
                                final JCTree.JCMethodInvocation copy = make.Apply(that.typeargs, that.meth, that.args);
                                that.typeargs = List.nil();
                                that.meth = and;
                                that.args = List.of(selected, copy);
                                this.visitApply(that);
                                return;
                            }
                        }
                    }
                }
            }
            encl = this.attribTree(that.meth, localEnv, new ResultInfo(kind, site, this.resultInfo.checkContext));
//            System.out.println("==============\n" + oldTree + "=>\n" + that);
        }

        Type restype = encl.getReturnType();
        if (restype.hasTag(TypeTag.WILDCARD)) {
            throw new AssertionError(encl);
        }
        Type qualifier = that.meth.hasTag(JCTree.Tag.SELECT) ? ((JCTree.JCFieldAccess) that.meth).selected.type : this.env.enclClass.sym.type;
        Symbol msym = TreeInfo.symbol(that.meth);
        restype = this.adjustMethodReturnType(msym, qualifier, methName, argtypes, restype);
        this.chk.checkRefTypes(that.typeargs, typeargtypes);
        Type capturedRes = this.resultInfo.checkContext.inferenceContext().cachedCapture(that, restype, true);
        this.result = this.check(that, capturedRes, Kinds.KindSelector.VAL, this.resultInfo);
        this.chk.validate(that.typeargs, localEnv);

    }


    public static Object get(Object obj, String field) {
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void set(Object obj, String field, Object val) {
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(obj, val);
        } catch (Exception e) {
            throw new RuntimeException(e);

        }
    }
}
