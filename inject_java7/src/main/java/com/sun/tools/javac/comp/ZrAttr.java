package com.sun.tools.javac.comp;

import static com.sun.tools.javac.code.TypeTag.VOID;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeAnnotations;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrUnSupportCodeError;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

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
        } catch (NeedReplaceLambda needReplaceLambda) {
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
        int kind;
        try {
            kind = this.attribArgs(Kinds.VAL, that.args, localEnv, argtypesBuf);
        } catch (NeedReplaceLambda needReplaceLambda) {
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
            kind = this.attribArgs(Kinds.VAL, that.args, localEnv, argtypesBuf);
        }
        List<Type> argtypes = argtypesBuf.toList();
        List<Type> typeargtypes = this.attribAnyTypes(that.typeargs, localEnv);
        final Type pt = this.resultInfo.pt;
        Type methodTemplate = this.newMethodTemplate(pt, argtypes, typeargtypes);
        Type encl;
        try {
            encl = this.attribTree(that.meth, localEnv, new ResultInfo(kind, methodTemplate, this.resultInfo.checkContext));
        } catch (NeedRedirectMethod redirectMethod) {
            final ExMethodInfo methodInfo = redirectMethod.exMethodInfo;
            if (methodInfo == null) throw new ZrUnSupportCodeError("拓展方法解析异常：异常匹配的方法信息。于：" + that);
            final JCTree.JCMethodInvocation oldTree = make.Apply(that.typeargs, that.meth, that.args);
            final Symbol bestSoFar = redirectMethod.bestSoFar;
            final JCTree.JCFieldAccess add = make.Select(make.Ident(bestSoFar.owner), bestSoFar.name);
            if (methodInfo.siteCopyByClassHeadArgMethod) {
                if (that.meth instanceof JCTree.JCFieldAccess) {
                    final Type currentClassType = redirectMethod.site;
                    final Type type = new Type.ClassType(Type.noType, List.of(currentClassType), syms.classType.tsym);
                    final JCTree.JCExpression jcExpression = make.ClassLiteral(currentClassType).setType(type);
                    that.args = that.args.prepend(jcExpression);
                    argtypes = argtypes.prepend(type);
                } else if (that.meth instanceof JCTree.JCIdent) {
                    final Type oType = env.enclClass.type;
                    final Type type = new Type.ClassType(Type.noType, List.of(oType), syms.classType.tsym);
                    final JCTree.JCExpression x = make.ClassLiteral(oType);
                    that.args = that.args.prepend(x);
                    argtypes = argtypes.prepend(type);
                } else {
                    throw new ZrUnSupportCodeError("拓展方法解析异常：使用Class定义的实例拓展方法，匹配至其静态副本，但是其site不是JCFieldAccess或者JCIdent。于：" + that);
                }
            } else if (!methodInfo.isStatic) {
                JCTree.JCExpression prepend = null;
                if (that.meth instanceof JCTree.JCFieldAccess) {
                    prepend = ((JCTree.JCFieldAccess) that.meth).selected;
                } else if (that.meth instanceof JCTree.JCIdent) {
                    final Type oType = env.enclClass.type;
                    prepend = make.This(oType);
                }
                if (prepend != null) {
                    that.args = that.args.prepend(prepend);
                    argtypes = argtypes.prepend(prepend.type);
                } else {
                    throw new ZrUnSupportCodeError("拓展方法解析异常：实例拓展方法，但是其site不是JCFieldAccess或者JCIdent(" + that.meth.getClass() + ")。于：" + that);
                }
            }
            methodTemplate = this.newMethodTemplate(pt, argtypes, typeargtypes);
            JCTree.JCExpression oldMeth = that.meth;
            final boolean hasTagSelect = oldMeth.hasTag(JCTree.Tag.SELECT);
            final JCTree.JCExpression selected = !hasTagSelect ? null : ((JCTree.JCFieldAccess) oldMeth).selected;
            that.meth = add;
            that.type = redirectMethod.bestSoFar.type;
            if (hasTagSelect) {
                final boolean staticInvoke = selected.hasTag(JCTree.Tag.IDENT) || TreeInfo.isStaticSelector(selected, names);
                if (!staticInvoke) {
                    if (methodInfo.isStatic) {
                        if (((Symbol.MethodSymbol) bestSoFar).getReturnType().hasTag(VOID)) {
                            throw new ZrUnSupportCodeError("对实例对象调用无返回值的静态方法", oldTree);
                        } else {
                            final Symbol.ClassSymbol biopClass = syms.classes.get(names.fromString("zircon.BiOp"));
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
//            System.out.println("==============\n" + oldTree + "=>\n" + that);
            encl = this.attribTree(that.meth, localEnv, new ResultInfo(kind, methodTemplate, this.resultInfo.checkContext));
        }

        Type restype = encl.getReturnType();
        if (restype.hasTag(TypeTag.WILDCARD)) {
            throw new AssertionError(encl);
        }
        Type qualifier = that.meth.hasTag(JCTree.Tag.SELECT) ? ((JCTree.JCFieldAccess) that.meth).selected.type : this.env.enclClass.sym.type;
        restype = this.adjustMethodReturnType(qualifier, methName, argtypes, restype);
        this.chk.checkRefTypes(that.typeargs, typeargtypes);
        this.result = this.check(that, this.types.capture(restype), Kinds.VAL, this.resultInfo);
        this.chk.validate(that.typeargs, localEnv);

    }
}
