package com.sun.tools.javac.parser;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.comp.TypeEnter;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import java.lang.reflect.Field;

public class ZrEnter extends Enter {
    protected ZrEnter(Context context) {
        super(context);
    }
    public static ZrEnter instance(Context context) {
        System.out.println("ZrEnter instance");
        Enter res = context.get(enterKey);
        if (res instanceof ZrEnter) return (ZrEnter) res;
        context.put(enterKey, (Enter) null);
        final ZrEnter zrEnter = new ZrEnter(context);
        final TypeEnter instance = TypeEnter.instance(context);
        if (get(instance, "enter") != null) {
            set(instance, "enter", zrEnter);
        }
        return zrEnter;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        super.visitApply(that);
        System.out.println("------[ZrEnter]---visitApply--" + that.toString());

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
