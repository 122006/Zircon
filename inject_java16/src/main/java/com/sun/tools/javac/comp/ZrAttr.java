package com.sun.tools.javac.comp;

import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static com.sun.tools.javac.code.Flags.PARAMETER;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;
import static com.sun.tools.javac.code.Kinds.Kind.ABSENT_MTH;
import static com.sun.tools.javac.code.Kinds.Kind.ERR;

public class ZrAttr extends Attr {
    private final Context context;

    protected ZrAttr(Context context) {
        super(context);
        this.context = context;
    }

    public static ZrAttr instance(Context context) {
        System.out.println("ZrAttr instance");
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
    public void visitReference(JCTree.JCMemberReference that) {
        System.out.println("-ZrAttr visitReference " + that + " class:" + that.getClass());
        super.visitReference(that);
    }


    @Override
    Type attribTree(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        if (tree instanceof JCTree.JCMethodInvocation) {
            System.out.println("---ZrAttr-attribTree = " + tree.toString());
            final JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation) tree;
            final JCTree.JCExpression meth = invocation.meth;
            System.out.println("----attribArg = " + invocation.getTypeArguments().toString());
            if (resultInfo.pt instanceof Type.ForAll) {
                final List<Type> typeArguments = ((Type.ForAll) resultInfo.pt).getTypeArguments();
                System.out.println("----typeArguments ForAll = " + typeArguments.toString());
            } else {
                System.out.println("----typeArguments = " + resultInfo.pt.getReturnType());
            }
            return super.attribTree(tree, env, resultInfo);
        } else if (tree instanceof JCTree.JCMemberReference) {
            int prevPos = make.pos;
            final JCTree.JCMemberReference memberReference = (JCTree.JCMemberReference) tree;
            System.out.println("memberReference: class:" + memberReference.getClass().getName() + " sym:" + memberReference);
            System.out.println("pt :" + resultInfo.pt);
            if (resultInfo.pt!=null) System.out.println("ptClass :" + resultInfo.pt.getClass());
            try {
                return super.attribTree(memberReference, env, resultInfo);
            } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
                make.at(prevPos);
                final Symbol.MethodSymbol bestSoFar = (Symbol.MethodSymbol) redirectMethod.bestSoFar;
                final TreeMaker maker = TreeMaker.instance(context);
                System.out.println("use lambda method :" + bestSoFar + " class:" + bestSoFar.getClass());
                final JCTree.JCFieldAccess add = maker.Select(maker.Ident(bestSoFar.owner), bestSoFar.name);
                add.sym = bestSoFar;
                add.type = bestSoFar.type;
                final List<Attribute.Class> methodStaticExType = ZrResolve.getMethodStaticExType(names, (Symbol.MethodSymbol) bestSoFar);
                System.out.println("use lambda method ex:" + methodStaticExType);
                if (methodStaticExType.isEmpty()) {
                    final Name nameA = names.fromString("$zr$a");
                    Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA
                            , bestSoFar.params.get(1).type, syms.noSymbol);
                    System.out.println("VarSymbol:" + symA);
                    final JCTree.JCIdent idA = maker.Ident(symA);
                    final JCTree.JCMethodInvocation apply = maker.Apply(List.nil(), add, List.of(memberReference.getQualifierExpression(), idA));
                    System.out.println("JCMethodInvocation:" + apply);
                    JCTree.JCVariableDecl a = make.VarDef(symA, null);
                    final JCTree.JCLambda lambda = maker.Lambda(List.of(a), apply);
                    lambda.target = memberReference.target;
                    lambda.type = memberReference.type;
                    lambda.pos = memberReference.pos;
                    System.out.println("--------lambda=>" + lambda.toString());
//                    super.resultInfo = super.resultInfo.dup(bestSoFar.getReturnType());
                    super.resultInfo = resultInfo.dup(resultInfo.pt);
                    System.out.println("--------next.tree==" + env.next.tree + "   [" + env.next.tree.getClass());
                    final Env<AttrContext> envDup = env.dup(lambda);
                    envDup.next = env.next;
                    final Type type = super.attribTree(lambda, envDup, super.resultInfo);
                    System.out.println("--------lambda type=" + type);
                    return type;
                }
//                } else {
////                    final Name nameA = names.fromString("a");
////                    Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA
////                            , methodStaticExType.head.type, syms.noSymbol);
////                    System.out.println("VarSymbol:" + symA);
////                    final JCTree.JCIdent idA = maker.Ident(symA);
////                    final JCTree.JCMethodInvocation apply = maker.App(add, List.of(idA));
////                    System.out.println("JCMethodInvocation:" + apply);
////                    JCTree.JCVariableDecl a = make.VarDef(symA, null);
////                    final JCTree.JCLambda lambda = maker.Reference(List.of(a), apply);
////                    lambda.target = memberReference.target;
////                    lambda.type = memberReference.type;
////                    lambda.pos = memberReference.pos;
////                    System.out.println("--------lambda=>" + lambda.toString());
//////                    super.resultInfo = super.resultInfo.dup(bestSoFar.getReturnType());
////                    final Type type = super.attribTree(lambda, env, resultInfo);
////                    System.out.println("--------lambda type=" + result);
//                    final JCTree.JCMemberReference reference = maker.Reference(memberReference.mode, bestSoFar.name, maker.Ident(methodStaticExType.head.getValue().tsym), null);
//                    System.out.println("--------lambda=>" + reference.toString());
//                    final Type type = super.attribTree(reference, env, resultInfo);
//                    System.out.println("--------lambda type=" + result);
//                    return type;
//                }

            }

        }
        System.out.println("attribTree " + tree + "    class:" + tree.getClass().getName());
        return super.attribTree(tree, env, resultInfo);
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        try {
            super.visitApply(that);
        } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
            final Symbol bestSoFar = redirectMethod.bestSoFar;
            final TreeMaker maker = TreeMaker.instance(context);
            System.out.println("use method :" + bestSoFar);
            final JCTree.JCFieldAccess add = maker.Select(maker.Ident(bestSoFar.owner), bestSoFar.name);
            final List<Attribute.Class> methodStaticExType = ZrResolve.getMethodStaticExType(names, (Symbol.MethodSymbol) bestSoFar);
            System.out.println("use method ez:" + methodStaticExType);
            that.args = methodStaticExType.isEmpty() ? that.args.prepend(((JCTree.JCFieldAccess) that.meth).selected) : that.args;
            that.meth = add;
            System.out.println("--------=>" + that.toString());
            super.visitApply(that);
            System.out.println("--------type=" + result);
        }
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        System.out.println("--visitSelect=" + tree);
        super.visitSelect(tree);

    }

    @Override
    public Type attribExpr(JCTree tree, Env<AttrContext> env) {
        if (tree instanceof JCTree.JCMethodInvocation) {
            JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation) tree;
            System.out.println("meth=" + invocation.meth.toString());
            System.out.println("tree=" + tree.toString());
            System.out.println("invocation=" + invocation.getTree().toString());
            System.out.println("invocation.sym=" + TreeInfo.symbol(invocation.getTree()));
            System.out.println("invocation.sym2=" + TreeInfo.symbol(invocation.getTree()));
            final Type type = super.attribExpr(tree, env);
            return type;
        } else if (tree instanceof JCTree.JCFieldAccess) {
            JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) tree;
            System.out.println("meth=" + fieldAccess.sym);
            System.out.println("tree=" + tree);
            System.out.println("invocation=" + fieldAccess.getTree().toString());
            System.out.println("invocation.sym=" + TreeInfo.symbol(fieldAccess.getTree()));
            final Type type = super.attribExpr(tree, env);
            System.out.println("invocation.sym2=" + TreeInfo.symbol(fieldAccess.getTree()));
            return type;
        }
        return super.attribExpr(tree, env);

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

    @Override
    public void attribClass(JCDiagnostic.DiagnosticPosition pos, Symbol.ClassSymbol c) {
        System.out.println("开始解析符号：" + c.toString());
        super.attribClass(pos, c);
        System.out.println("完成解析符号：" + c.toString());
    }
}
