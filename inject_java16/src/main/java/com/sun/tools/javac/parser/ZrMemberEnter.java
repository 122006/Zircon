package com.sun.tools.javac.parser;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.comp.TypeEnter;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import java.lang.reflect.Field;

public class ZrMemberEnter extends MemberEnter {
    protected ZrMemberEnter(Context context) {
        super(context);
    }

    public static ZrMemberEnter instance(Context context) {
        System.out.println("ZrMemberEnter instance");

        MemberEnter res = context.get(memberEnterKey);
        if (res instanceof ZrMemberEnter) return (ZrMemberEnter) res;
        context.put(memberEnterKey, (MemberEnter) null);
        final ZrMemberEnter zrMemberEnter = new ZrMemberEnter(context);
        final JavacTrees javacTrees = JavacTrees.instance(context);
        if (get(javacTrees, "memberEnter") != null) {
            set(javacTrees, "memberEnter", zrMemberEnter);
        }
        final TypeEnter instance = TypeEnter.instance(context);
        if (get(instance, "memberEnter") != null) {
            set(instance, "memberEnter", zrMemberEnter);
        }
        return zrMemberEnter;
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
    public void visitClassDef(JCTree.JCClassDecl tree) {
        super.visitClassDef(tree);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        super.visitMethodDef(tree);
//        System.out.println("------[ZrMemberEnter]---visitMethodDef--" + tree.toString());
        tree.accept(new MyTreeTranslator());
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
//        System.out.println("------[ZrMemberEnter]-----" + tree.toString());
//        final JCTree.JCExpression methodSelect = tree.getMethodSelect();
//        System.out.println("  methodSelect:" + methodSelect.toString());
//        System.out.println("  methodSelect.type:" + methodSelect.type);
//        System.out.println("  methodSelect.class:" + methodSelect.getClass());
//        final List<JCTree.JCExpression> arguments = tree.getArguments();
//        arguments.forEach(jcExpression -> {
//            System.out.println("  arguments:" + jcExpression.toString());
//            System.out.println("  arguments.type:" + jcExpression.type);
//            System.out.println("  arguments.class:" + jcExpression.getClass());
//        });
        super.visitApply(tree);
    }

}
