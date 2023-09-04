package com.sun.tools.javac.comp;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import java.lang.reflect.Field;

import static com.sun.tools.javac.code.Flags.PARAMETER;
import static com.sun.tools.javac.code.TypeTag.NONE;

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
        ReflectionUtil.setDeclaredField(TypeEnter.instance(context), TypeEnter.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(JavaCompiler.instance(context), JavaCompiler.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(DeferredAttr.instance(context), DeferredAttr.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(ArgumentAttr.instance(context), ArgumentAttr.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(Resolve.instance(context), Resolve.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(TypeAnnotations.instance(context), TypeAnnotations.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(Annotate.instance(context), Annotate.class, "attr", zrAttr);
        return zrAttr;
    }


    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        try {
            super.visitApply(that);
        } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
            final Symbol bestSoFar = redirectMethod.bestSoFar;
            final TreeMaker maker = TreeMaker.instance(context);
            final JCTree.JCFieldAccess add = maker.Select(maker.Ident(bestSoFar.owner), bestSoFar.name);
            final List<Attribute.Class> methodStaticExType = ZrResolve.getMethodStaticExType(names, (Symbol.MethodSymbol) bestSoFar);
            that.args = methodStaticExType.isEmpty() ? that.args.prepend(((JCTree.JCFieldAccess) that.meth).selected) : that.args;
            that.meth = add;
            super.visitApply(that);
        }
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
