package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class ZrArgumentAttr extends ArgumentAttr {
    private final Context context;

    protected ZrArgumentAttr(Context context) {
        super(context);
        this.context = context;
    }

    public static ZrArgumentAttr instance(Context context) {
        System.out.println("ZrArgumentAttr instance");
        ArgumentAttr res = context.get(methodAttrKey);
        if (res instanceof ZrArgumentAttr) return (ZrArgumentAttr) res;
        context.put(methodAttrKey, (ArgumentAttr) null);
        final ZrArgumentAttr zrArgumentAttr = new ZrArgumentAttr(context);
        context.put(methodAttrKey, zrArgumentAttr);
        {
            final Attr instance = Attr.instance(context);
            if (ReflectionUtil.getDeclaredField(instance, Attr.class, "argumentAttr") != null) {
                System.out.println("覆盖argumentAttr");
                ReflectionUtil.setDeclaredField(instance, Attr.class, "argumentAttr", zrArgumentAttr);
            }
        }

        return zrArgumentAttr;
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
    Type attribArg(JCTree tree, Env<AttrContext> env) {
        System.out.println("attribArg:"+tree);
        return super.attribArg(tree, env);
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        System.out.println("---ZrArgumentAttr-visitApply = " + that.toString());
        System.out.println("----class = " + that.getClass().toString());
        final JCTree.JCExpression meth = that.meth;
        System.out.println("----meth = " + meth.toString());
        System.out.println("----TypeArguments = " + that.getTypeArguments());

        Name identifier = null;
        if (JCTree.JCIdent.class.equals(meth.getClass())) {
            super.visitApply(that);
            return;
        } else if (JCTree.JCFieldAccess.class.equals(meth.getClass())) {
            final JCTree.JCFieldAccess methFA = (JCTree.JCFieldAccess) meth;
            identifier = methFA.getIdentifier();
//            if (identifier.toString().equals("add")) {
//                System.out.println("meth=" + meth.getClass().getName());
//                System.out.println("selected=" + methFA.selected);
//                System.out.println("selected=" + methFA.selected.getClass().getName());
//                super.visitApply(that);
//                return;
//            }
            System.out.println("----selected = " + identifier);
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
//                that.meth = add;
//                that.args = List.from(args);
//                System.out.println("--------=>" + that.toString());
//                super.visitApply(that);
//                return;
//            }
        }

        super.visitApply(that);
        System.out.println("----result = " + result.toString());
//        System.out.println("----tree = " + TreeInfo.symbol(that.getTree()));
    }

}
