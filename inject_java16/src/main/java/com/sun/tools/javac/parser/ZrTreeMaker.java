package com.sun.tools.javac.parser;

import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

public class ZrTreeMaker extends TreeMaker {
    protected ZrTreeMaker(Context context) {
        super(context);
    }

    protected ZrTreeMaker(JCTree.JCCompilationUnit toplevel, Names names, Types types, Symtab syms) {
        super(toplevel, names, types, syms);
    }

    @Override
    public JCTree.JCMethodInvocation Apply(List<JCTree.JCExpression> typeargs, JCTree.JCExpression fn, List<JCTree.JCExpression> args) {
        final JCTree.JCMethodInvocation apply = super.Apply(typeargs, fn, args);
        args.forEach(a->{
            final Type type = a.type;
            System.out.println(type);
        });
        System.out.println(String.format("typeargs=%s,fn=%s,args=%s", typeargs, fn, args));
        return apply;
    }

    public static TreeMaker instance(Context context) {
        TreeMaker instance = context.get(treeMakerKey);
        if (!(instance instanceof ZrTreeMaker)) {
            instance = new ZrTreeMaker(context);
        }
        return instance;
    }
}
