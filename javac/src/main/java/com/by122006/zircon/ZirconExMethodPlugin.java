package com.by122006.zircon;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;

public class ZirconExMethodPlugin extends TreeScanner<Void, Void> implements Plugin {
    @Override
    public String getName() {
        return "ZrExMethod";
    }

    @Override
    public void init(JavacTask task, String... args) {
        System.out.println("inject [方法重定向插件] jdk:" + System.getProperty("java.version"));
        BasicJavacTask javacTask = (BasicJavacTask) task;
        Context context = javacTask.getContext();
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                System.out.println("=========================================");
                System.out.println("start: [" + (e.getSourceFile() == null ? "-" : e.getSourceFile().getName()) + "] TaskEvent.Kind." + e.getKind());
                if (e.getKind() != TaskEvent.Kind.ENTER) {
                    return;
                }
//                e.getCompilationUnit().getTypeDecls()
//                        .stream()
//                        .filter(a -> a.getKind() == Tree.Kind.CLASS).map(ClassTree.class::cast)
//                        .forEach(classTree -> {
//                            System.out.println("classTree.type:" + classTree.getClass().toString());
//                            if (classTree instanceof JCTree.JCClassDecl) {
//                                ((JCTree.JCClassDecl) classTree).accept(new TreeTranslator() {
//                                    @Override
//                                    public void visitApply(JCTree.JCMethodInvocation tree) {
//                                        super.visitApply(tree);
//                                        System.out.println("------"+tree.toString());
//                                        final JCTree.JCExpression methodSelect = tree.getMethodSelect();
//                                        System.out.println("  methodSelect:" + methodSelect.toString());
//                                        System.out.println("  methodSelect.type:" + methodSelect.type);
//                                        System.out.println("  methodSelect.class:" + methodSelect.getClass());
//                                        final List<JCTree.JCExpression> arguments = tree.getArguments();
//                                        arguments.forEach(jcExpression -> {
//                                            System.out.println("  arguments:" + jcExpression.toString());
//                                            System.out.println("  arguments.type:" + jcExpression.type);
//                                            System.out.println("  arguments.class:" + jcExpression.getClass());
//                                        });
//
//                                    }
//                                });
//                            }
//                        });

//                e.getCompilationUnit().getTypeDecls()
//                        .stream()
//                        .filter(a -> a.getKind() == Tree.Kind.CLASS).map(ClassTree.class::cast)
//                        .map(ClassTree::getMembers)
//                        .flatMap(Collection::stream)
//                        .filter(a -> a.getKind() == Tree.Kind.METHOD).map(MethodTree.class::cast)
//                        .map(methodTree -> methodTree.getBody().getStatements())
//                        .flatMap(Collection::stream)
//                        .forEach(a -> {
//                            System.out.println("==expression:" + a);
//                            System.out.println("==expression.type:" + a.getClass());
//                            if (a.getKind() == Tree.Kind.EXPRESSION_STATEMENT) {
//                                final JCTree.JCExpression expression = ((JCTree.JCExpressionStatement) a).getExpression();
//                                System.out.println("==expression:" + expression);
//                                System.out.println("==expression.type:" + expression.getClass());
//                                if(expression instanceof JCTree.JCMethodInvocation){
//                                    final JCTree.JCExpression methodSelect = ((JCTree.JCMethodInvocation) expression).getMethodSelect();
//                                    System.out.println("==methodSelect.type:" + methodSelect.type);
//                                    System.out.println("==methodSelect.class:" + methodSelect.getClass());
//
//                                }
//
//                            }
//                            System.out.println(a.getClass().getName() + ":" + a);
////                    a.accept(ZirconReMethodPlugin.this,null);
//                        });

            }

            @Override
            public void finished(TaskEvent e) {

                if (e.getKind() != TaskEvent.Kind.ENTER) {
                    return;
                }
                e.getCompilationUnit().getTypeDecls()
                        .stream()
                        .filter(a -> a.getKind() == Tree.Kind.CLASS).map(ClassTree.class::cast)
                        .forEach(classTree -> {
                            System.out.println("classTree.class:" + classTree.getClass().toString());
                            if (classTree instanceof JCTree.JCClassDecl) {
                                ((JCTree.JCClassDecl) classTree).accept(new TreeTranslator() {
                                    @Override
                                    public void visitApply(JCTree.JCMethodInvocation tree) {
                                        super.visitApply(tree);
                                        final JCTree.JCExpression methodSelect = tree.getMethodSelect();
                                        if (!(methodSelect instanceof JCTree.JCFieldAccess)) return;
//                                        System.out.println("------" + tree.toString());
//                                        System.out.println("  methodSelect:" + methodSelect.toString());
//                                        System.out.println("  methodSelect.sym:" + TreeInfo.symbol(((JCTree.JCFieldAccess) methodSelect)));
//                                        System.out.println("  methodSelect.selected:" + ((JCTree.JCFieldAccess) methodSelect).selected.toString());
//                                        System.out.println("  methodSelect.type:" + methodSelect);
//                                        System.out.println("  methodSelect.class:" + methodSelect.getClass());
//                                        final List<JCTree.JCExpression> arguments = tree.getArguments();
//                                        arguments.forEach(jcExpression -> {
//                                            System.out.println("  arguments:" + jcExpression.toString());
//                                            System.out.println("  arguments.type:" + jcExpression.type);
//                                            System.out.println("  arguments.class:" + jcExpression.getClass());
//                                        });

                                    }
                                });
                            }
                        });
            }
        });
    }


}
