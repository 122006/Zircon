package com.sun.tools.javac.comp;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrUnSupportCodeError;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;

import java.util.Objects;
import java.util.Optional;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.TypeTag.*;

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
        ReflectionUtil.setDeclaredField(MemberEnter.instance(context), MemberEnter.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(JavacTrees.instance(context), JavacTrees.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(JavaCompiler.instance(context), JavaCompiler.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(DeferredAttr.instance(context), DeferredAttr.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(Resolve.instance(context), Resolve.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(TypeAnnotations.instance(context), TypeAnnotations.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(Annotate.instance(context), Annotate.class, "attr", zrAttr);
        return zrAttr;
    }

    Symbol.ClassSymbol biopClass = null;
    @Override
    public void visitVarDef(JCTree.JCVariableDecl that) {
        try {
            super.visitVarDef(that);
        } catch (ZrResolve.NeedReplaceLambda needReplaceLambda) {
            JCTree.JCExpression initializer = that.getInitializer();
            while (initializer instanceof JCTree.JCParens) {
                initializer=((JCTree.JCParens) initializer).getExpression();
            }
            if (Objects.equals(initializer.getStartPosition(), needReplaceLambda.memberReference.getStartPosition())) {
                that.init=needReplaceLambda.bestSoFar;
            }
            super.visitVarDef(that);
        }
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        try {
            super.visitApply(that);
        } catch (ZrResolve.NeedReplaceLambda needReplaceLambda) {
            List<JCTree.JCExpression> arguments = that.getArguments();
            List<JCTree.JCExpression> newList = List.nil();
            for (int i = 0; i < arguments.size(); i++) {
                JCTree.JCExpression argument = arguments.get(i);
                while (argument instanceof JCTree.JCParens) {
                    argument=((JCTree.JCParens) argument).getExpression();
                }
                if (Objects.equals(argument.getStartPosition(), needReplaceLambda.memberReference.getStartPosition())) {
                    newList = newList.append(needReplaceLambda.bestSoFar);
                } else {
                    newList = newList.append(argument);
                }
            }
            that.args = newList;
            super.visitApply(that);
        } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
            final JCTree.JCMethodInvocation oldTree = make.Apply(that.typeargs, that.meth, that.args);

            final Symbol bestSoFar = redirectMethod.bestSoFar;
            if (bestSoFar.owner == null) {
                System.err.println(bestSoFar);
                System.err.println(bestSoFar.owner);
            }
            final JCTree.JCFieldAccess add = make.Select(make.Ident(bestSoFar.owner), bestSoFar.name);
            final List<Attribute.Class> methodStaticExType = ZrResolve.getMethodStaticExType(names, (Symbol.MethodSymbol) bestSoFar);
            if (methodStaticExType.isEmpty()) {
                if (that.meth instanceof JCTree.JCFieldAccess) {
                    that.args = that.args.prepend(((JCTree.JCFieldAccess) that.meth).selected);
                } else if (that.meth instanceof JCTree.JCIdent) {
                    that.args = that.args.prepend(make.Ident(names._this));
                }
            }
            JCTree.JCExpression oldMeth = that.meth;
            that.meth = add;
            that.type = redirectMethod.bestSoFar.type;
            if (oldMeth.hasTag(JCTree.Tag.SELECT)) {
                final JCTree.JCExpression selected = ((JCTree.JCFieldAccess) oldMeth).selected;
                final boolean staticInvoke = selected.hasTag(JCTree.Tag.IDENT) || TreeInfo.isStaticSelector(selected, names);
                if (!staticInvoke) {
                    final Optional<ZrResolve.ExMethodInfo> first = ((ZrResolve) rs).findRedirectMethod(bestSoFar.getSimpleName()).stream().filter(a -> a.methodSymbol == bestSoFar)
                            .findFirst();
                    if (first.isPresent()) {
                        if (first.get().isStatic) {
                            if (((Symbol.MethodSymbol) bestSoFar).getReturnType().hasTag(VOID)) {
                                throw new ZrUnSupportCodeError("对实例对象调用无返回值的静态方法", oldTree);
                            } else {
                                if (biopClass == null) {
                                    final Iterable<Symbol> elements = syms.packages.get(names.fromString("zircon")).members().getElements(a -> Objects.equals(a.getSimpleName().toString(), "BiOp"));
                                    for (Symbol a : elements) {
                                        biopClass = (Symbol.ClassSymbol) a;
                                    }
                                }
                                if (biopClass == null) throw new ZrUnSupportCodeError("请确定是否正确引入依赖zircon");
                                final JCTree.JCFieldAccess and = make.Select(make.QualIdent(biopClass), names.fromString("sec"));
                                final JCTree.JCMethodInvocation copy = make.Apply(that.typeargs, that.meth, that.args);
                                that.typeargs = List.nil();
                                that.meth = and;
                                that.args = List.of(selected, copy);
                                super.visitApply(that);
                                return;
                            }
                        }
                    }
                }
            }
            super.visitApply(that);
        }
    }
}
