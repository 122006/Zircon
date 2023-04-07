package com.by122006.zircon;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import java.util.Collection;

public class ZirconReMethodPlugin extends TreeScanner<Void,Void> implements Plugin {
    @Override
    public String getName() {
        return "MethodRedirect";
    }

    @Override
    public void init(JavacTask task, String... args) {
        System.out.println("inject [方法重定向插件] jdk:" + System.getProperty("java.version"));
        BasicJavacTask javacTask = (BasicJavacTask) task;
        Context context = javacTask.getContext();
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                System.out.println("start: TaskEvent.Kind." + e.getKind());
                if (e.getKind() != TaskEvent.Kind.ENTER) {
                    return;
                }
                e.getCompilationUnit().getTypeDecls()
                        .stream()
                        .filter(a -> a.getKind() == Tree.Kind.CLASS).map(ClassTree.class::cast)
                        .map(ClassTree::getMembers)
                        .flatMap(Collection::stream)
                        .filter(a -> a.getKind() == Tree.Kind.METHOD).map(MethodTree.class::cast)
                        .map(methodTree -> methodTree.getBody().getStatements())
                        .flatMap(Collection::stream)
                        .forEach(a -> {
                            if (a.getKind()== Tree.Kind.EXPRESSION_STATEMENT){
                                final JCTree.JCExpression expression = ((JCTree.JCExpressionStatement) a).getExpression();
                                System.out.println("==expression:"+expression);

                            }
                            System.out.println(a.getClass().getName()+":" + a);
//                    a.accept(ZirconReMethodPlugin.this,null);
                        });

            }

            @Override
            public void finished(TaskEvent e) {

                if (e.getKind() != TaskEvent.Kind.ENTER) {
                    return;
                }
            }
        });
    }


}
