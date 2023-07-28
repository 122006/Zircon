package com.sun.tools.javac.comp;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

public class ZrTransTypes  extends TransTypes {
    public static ZrTransTypes instance(Context context) {
        System.out.println("ZrTransTypes instance");
        TransTypes res = context.get(transTypesKey);
        if (res instanceof ZrTransTypes) return (ZrTransTypes) res;
        context.put(transTypesKey, (TransTypes)null);
        return new ZrTransTypes(context);
    }
    protected ZrTransTypes(Context context) {
        super(context);
        ZrTransTypes rs = ZrTransTypes.instance(context);
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
//        System.out.println("visitClassDef:"+tree);
        super.visitClassDef(tree);
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
        System.out.println(tree.meth);
    }
}
