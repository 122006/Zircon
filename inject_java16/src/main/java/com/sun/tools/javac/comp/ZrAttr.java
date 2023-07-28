package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import java.lang.reflect.Field;
import java.util.ArrayList;

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
        System.out.println("-ZrAttr visitReference " + that);
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
        }
        return super.attribTree(tree, env, resultInfo);
    }



//    @Override
//    public void visitApply(JCTree.JCMethodInvocation that) {
//        System.out.println("---ZrAttr-visitApply = " + that.toString());
//        System.out.println("----class = " + that.getClass().toString());
//        final JCTree.JCExpression meth = that.meth;
//        System.out.println("----meth = " + meth.toString());
//        Name identifier = null;
//        if (JCTree.JCIdent.class.equals(meth.getClass())) {
//            super.visitApply(that);
//            return;
//        } else if (JCTree.JCFieldAccess.class.equals(meth.getClass())) {
//            final JCTree.JCFieldAccess methFA = (JCTree.JCFieldAccess) meth;
//            identifier = methFA.getIdentifier();
//            if (identifier.toString().equals("add")) {
//                System.out.println("meth=" + meth.getClass().getName());
//                System.out.println("selected=" + methFA.selected);
//                System.out.println("selected=" + methFA.selected.getClass().getName());
////                super.visitApply(that);
////                return;
//            }
//            System.out.println("----selected = " + identifier);
//            if (identifier.toString().equals("add23") && that.meth instanceof JCTree.JCFieldAccess) {
//                final Name name = (Name) ReflectionUtil.getDeclaredField(methFA, JCTree.JCFieldAccess.class, "name");
//                System.out.println("-----name = " + name.getClass());
//                final Names names = Names.instance(context);
//                final TreeMaker maker = TreeMaker.instance(context);
//                final ArrayList<JCTree.JCExpression> args = new ArrayList<>();
//                final JCTree.JCExpression selected = ((JCTree.JCFieldAccess) that.meth).selected;
//                System.out.println("-------------name=" + TreeInfo.name(selected));
//                args.add(selected);
//                args.addAll(that.args);
//                final JCTree.JCFieldAccess add = maker.Select(maker.Ident(names.fromString("Test")), names.fromString("add"));
////                that = maker.Apply(List.nil(), add, List.from(args));
//                that.meth = add;
//                that.args = List.from(args);
//                System.out.println("--------=>" + that.toString());
//                super.visitApply(that);
//                System.out.println("--------type=" + result);
//                return;
//            }
//        }
//
//        super.visitApply(that);
//        System.out.println("----tree = " + TreeInfo.symbol(that.getTree()));
//    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        try {
            super.visitApply(that);
        } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
            final Symbol bestSoFar = redirectMethod.bestSoFar;
            final TreeMaker maker = TreeMaker.instance(context);
            final JCTree.JCFieldAccess add = maker.Select(maker.Ident(bestSoFar.owner), bestSoFar.name);
            that.args = that.args.prepend(((JCTree.JCFieldAccess) that.meth).selected);
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
