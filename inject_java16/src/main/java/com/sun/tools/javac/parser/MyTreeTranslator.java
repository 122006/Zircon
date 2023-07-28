package com.sun.tools.javac.parser;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;

public class MyTreeTranslator extends TreeTranslator {
    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
//        final JCTree.JCExpression methodSelect = tree.getMethodSelect();
//        if (!(methodSelect instanceof JCTree.JCFieldAccess)) return;
//        System.out.println("------[ZrMemberEnter]-----" + tree.toString());
//        System.out.println("  methodSelect:" + methodSelect.toString());
//        System.out.println("  methodSelect.sym:" + ((JCTree.JCFieldAccess) methodSelect).sym);
//        System.out.println("  methodSelect.selected:" + ((JCTree.JCFieldAccess) methodSelect).selected.toString());
//        System.out.println("  methodSelect.type:" + methodSelect.type);
//        System.out.println("  methodSelect.class:" + methodSelect.getClass());
//        final List<JCTree.JCExpression> arguments = tree.getArguments();
//        arguments.forEach(jcExpression -> {
//            System.out.println("  arguments:" + jcExpression.toString());
//            System.out.println("  arguments.type:" + jcExpression.type);
//            System.out.println("  arguments.class:" + jcExpression.getClass());
//        });
    }
}
