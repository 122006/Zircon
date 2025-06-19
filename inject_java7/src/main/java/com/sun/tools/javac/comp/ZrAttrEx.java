package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;
import com.sun.tools.javac.util.Position;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @ClassName: ZrAttrEx
 * @Author: 0001185
 * @Date: 2025/6/19 15:45
 * @Description:
 */
public class ZrAttrEx {
    TreeMaker make;
    Symtab syms;
    Names names;
    ZrAttr zrAttr;
    ChangeOptionalChainingExpression2Expression changeOptionalChainingExpression2Expression;

    ZrAttrEx(ZrAttr zrAttr) {
        make = zrAttr.make;
        syms = zrAttr.syms;
        names = zrAttr.names;
        this.zrAttr = zrAttr;
        changeOptionalChainingExpression2Expression = new ChangeOptionalChainingExpression2Expression();
    }

    <T extends JCTree> Pair<Boolean, List<JCTree>> treeTranslator(List<T> trees, Env<AttrContext> env) {

        List<JCTree> nList = List.nil();
        boolean replace = false;
        for (JCTree tree : trees) {
            if (tree instanceof JCTree.JCExpressionStatement) {
                final JCTree.JCExpression expression = ((JCTree.JCExpressionStatement) tree).getExpression();
                if (expression instanceof JCTree.JCAssign) {
                    final JCTree.JCExpression variable = ((JCTree.JCAssign) expression).getVariable();
                    if (checkMethodInvocationIsOptionalChaining(variable)) {
                        final JCTree.JCExpression assignExpr;
                        if (checkMethodInvocationIsOptionalChaining(((JCTree.JCAssign) expression).getExpression())) {
                            assignExpr = changeOptionalChainingExpression2Expression.change(((JCTree.JCAssign) expression).getExpression());
                        } else {
                            assignExpr = ((JCTree.JCAssign) expression).getExpression();
                        }
                        final JCTree jcTree = changeOptionalChainingExpression2Call(variable, (trueExpr) -> {
                            return make.Exec(make.Assign(trueExpr, assignExpr));
                        }, env);
                        nList = nList.append(jcTree);
                        replace = true;
                        continue;
                    }
                } else if (findChainHasNullSafeFlag(expression, false)) {
                    final JCTree.JCExpressionStatement statement = (JCTree.JCExpressionStatement) tree;
                    final JCTree jcTree = changeOptionalChainingExpression2Call(statement.getExpression(), env);
                    nList = nList.append(jcTree);
                    replace = true;
                    continue;
                }

            } else if (tree instanceof JCTree.JCReturn) {
                JCTree.JCExpression expression = ((JCTree.JCReturn) tree).getExpression();
                final JCTree.JCExpression jcExpression = treeTranslatorExpressionWithReturnType(expression);
                replace = jcExpression != expression;
                if (replace) {
                    ((JCTree.JCReturn) tree).expr = jcExpression;
                    nList = nList.append(tree);
                } else if (findChainHasNullSafeFlag(expression, false)) {
                    final JCTree.JCReturn statement = (JCTree.JCReturn) tree;
                    final JCTree jcTree = changeOptionalChainingExpression2Call(statement.getExpression(), make::Return, make.Return(make.Literal(TypeTag.BOT, null)), env);
                    if (jcTree instanceof JCTree.JCBlock) {
                        nList = nList.appendList(listMap(((JCTree.JCBlock) jcTree).stats, a -> a));
                    }
                    replace = true;
                }
                if (replace) continue;

            }
            nList = nList.append(tree);
        }
        return Pair.of(replace, nList);
    }

    JCTree.JCExpression treeTranslator(JCTree.JCExpression tree) {
        final JCTree.JCExpression jcExpression = treeTranslatorExpressionWithReturnType(tree);
        if (jcExpression != tree) return jcExpression;
        if (checkMethodInvocationIsOptionalChaining(tree)) {
            return changeOptionalChainingExpression2Expression.change(tree, (e) -> e, make.Literal(TypeTag.BOT, null));
        }
        return tree;
    }

    JCTree.JCExpression treeTranslatorExpressionWithReturnType(JCTree.JCExpression tree) {
        if (tree instanceof JCTree.JCBinary) {
        } else if (tree instanceof JCTree.JCAssign) {
            final JCTree.JCExpression variable = ((JCTree.JCAssign) tree).getVariable();
            if (checkMethodInvocationIsOptionalChaining(variable)) {
                //特别的，非语句的赋值表达式会判断左值是否存在，如果不存在该左值不会被赋值
                final JCTree.JCExpression expression = ((JCTree.JCAssign) tree).getExpression();
                final JCTree.JCExpression elseExpr = checkMethodInvocationIsOptionalChaining(expression) ? changeOptionalChainingExpression2Expression.change(expression) : expression;
                return changeOptionalChainingExpression2Expression.change(variable, e -> make.Assign(e, elseExpr), elseExpr);
            }
        } else if (tree instanceof JCTree.JCConditional) {
            final JCTree.JCExpression trueExpression = ((JCTree.JCConditional) tree).getTrueExpression();
            if (trueExpression instanceof JCTree.JCFieldAccess) {
                if (((JCTree.JCFieldAccess) trueExpression).name.contentEquals("$$elvisExpr")) {
                    make.at(tree);
                    final Symbol.ClassSymbol biopClass = getBiopClass();
                    final JCTree.JCFieldAccess $$elvisExpr = make.Select(make.QualIdent(biopClass), names.fromString("$$elvisExpr"));
                    final JCTree.JCFieldAccess wrap = make.Select(make.QualIdent(biopClass), names.fromString("$$wrap"));
                    JCTree.JCExpression condition = ((JCTree.JCConditional) tree).getCondition();
                    JCTree.JCExpression falseExpression = ((JCTree.JCConditional) tree).getFalseExpression();
                    if (checkMethodInvocationIsOptionalChaining(condition)) {
                        condition = changeOptionalChainingExpression2Expression.change(condition, (e) -> e, falseExpression);
                    }
                    final JCTree.JCExpression apply = make.Apply(List.nil(), $$elvisExpr, List.of(condition.setPos(tree.pos + 1), falseExpression.setPos(tree.pos + 2))).setPos(tree.pos);
                    return apply;
                }
            }
        }
        return tree;
    }

    protected Type getType(Env<AttrContext> env, JCTree.JCExpression jcExpression) {
        return zrAttr.attribTree(jcExpression, env.dup(jcExpression, env.info.dup()), zrAttr.new ResultInfo(Kinds.VAL, Infer.anyPoly));
    }

    public <E extends JCTree> E copyExpr(E expr) {
        return new TreeCopier<Void>(make).copy(expr);
    }

    public JCTree.JCExpression cleanExprNullSafeFlag(JCTree.JCExpression expr) {
        expr = copyExpr(expr);

        new TreeScanner() {
            @Override
            public void visitSelect(JCTree.JCFieldAccess tree) {
                if (tree.selected instanceof JCTree.JCMethodInvocation) {
                    final JCTree.JCExpression meth = ((JCTree.JCMethodInvocation) tree.selected).meth;
                    if (meth instanceof JCTree.JCFieldAccess) {
                        if (((JCTree.JCFieldAccess) meth).name.contentEquals("$$NullSafe")) {
                            tree.selected = ((JCTree.JCFieldAccess) meth).selected;
                        }
                    }
                } else if (tree.selected instanceof JCTree.JCFieldAccess) {
                    if (((JCTree.JCFieldAccess) tree.selected).name.contentEquals("$$NullSafe")) {
                        tree.selected = ((JCTree.JCFieldAccess) tree.selected).selected;
                    }
                }

                super.visitSelect(tree);
            }
        }.scan(expr);
        expr = TreeInfo.skipParens(expr);

        return expr;
    }

    protected boolean checkMethodInvocationIsOptionalChaining(JCTree jctree) {
        if (jctree instanceof JCTree.JCMethodInvocation) {
            return findChainHasNullSafeFlag(((JCTree.JCMethodInvocation) jctree).meth, false);
        } else if (jctree instanceof JCTree.JCFieldAccess) {
            return findChainHasNullSafeFlag(((JCTree.JCFieldAccess) jctree).getExpression(), false);
        } else return false;
    }

    public class ChangeOptionalChainingExpression2Expression {
        protected JCTree.JCExpression change(JCTree.JCExpression expr) {
            return change(expr, (e) -> e, make.Literal(TypeTag.BOT, null));
        }

        protected JCTree.JCExpression change(JCTree.JCExpression expr, Function<JCTree.JCExpression, JCTree.JCExpression> trueExpr, JCTree.JCExpression elseExpr) {
            final JCTree.JCExpression jcExpression = change(null, expr, null, expr, trueExpr, elseExpr, new ArrayList<>(), false, false);
            if (jcExpression instanceof JCTree.JCConditional && elseExpr.getKind() == TypeTag.BOT.getKindLiteral()) {
                //防止简化推断，或者使用的是基础类型，包裹
                final Symbol.ClassSymbol biopClass = getBiopClass();
                final JCTree.JCFieldAccess wrapMethod = make.Select(make.QualIdent(biopClass), names.fromString("$$wrap"));
                ((JCTree.JCConditional) jcExpression).truepart = make.Apply(List.nil(), wrapMethod, List.of(((JCTree.JCConditional) jcExpression).truepart.setPos(Position.NOPOS)));
            } else if (jcExpression instanceof JCTree.JCConditional) {
                ((JCTree.JCConditional) jcExpression).truepart.setPos(Position.NOPOS);
            }
            return jcExpression;
        }

        public JCTree.JCExpression change(JCTree.JCExpression expr, JCTree.JCExpression restExpr, Consumer<JCTree.JCExpression> replace, JCTree.JCExpression allExpr, Function<JCTree.JCExpression, JCTree.JCExpression> trueExpr, JCTree.JCExpression elseExpr, java.util.List<JCTree.JCVariableDecl> varDecls, boolean useVarAndSkipWarpByFirst, boolean hasSkip) {
            if (expr == null) {
                expr = allExpr;
            }
            make.at(expr);
            final int pos = expr.pos;

            if (expr instanceof JCTree.JCMethodInvocation || expr instanceof JCTree.JCFieldAccess) {//a.b.$$NullSafe().c()
                final JCTree.JCExpression meth = expr instanceof JCTree.JCFieldAccess ? expr : ((JCTree.JCMethodInvocation) expr).meth;//a.b.$$NullSafe().c
                if (meth instanceof JCTree.JCFieldAccess) {
                    final JCTree.JCExpression lastExpr = ((JCTree.JCFieldAccess) meth).getExpression();//a.b.$$NullSafe()
                    if (isNullSafeMethod(lastExpr)) {
                        final JCTree.JCExpression lastMethodSelect = ((JCTree.JCMethodInvocation) lastExpr).getMethodSelect();//a.b.$$NullSafe

                        final JCTree.JCExpression lastExprLeft = ((JCTree.JCFieldAccess) lastMethodSelect).getExpression();//a.b

                        boolean wrap = !useVarAndSkipWarpByFirst || hasSkip;
                        final JCTree.JCLiteral nullLiteral = make.Literal(TypeTag.BOT, null);
                        hasSkip = true;
                        if (wrap) {
                            final Symbol.ClassSymbol biopClass = getBiopClass();
                            final JCTree.JCFieldAccess dup = make.Select(make.QualIdent(biopClass), names.fromString("$$dup"));
                            final JCTree.JCFieldAccess ignore = make.Select(make.QualIdent(biopClass), names.fromString("$$ignore"));


                            final JCTree.JCMethodInvocation copy = make.Apply(List.nil(), ignore, List.of(cleanExprNullSafeFlag(lastExprLeft)));
                            final JCTree.JCMethodInvocation lastExprLeftAndDup = make.Apply(List.nil(), dup, List.of(lastExprLeft));

                            JCTree.JCExpression copyRestExpr = cleanExprNullSafeFlag(restExpr);

                            ((JCTree.JCFieldAccess) meth).selected = copy;//a.b.c
                            final JCTree.JCConditional conditional;
                            if (elseExpr.getKind() == TypeTag.BOT.getKindLiteral()) {
                                final JCTree.JCFieldAccess $$pop$$useParam2WithParam1Type = make.Select(make.QualIdent(biopClass), names.fromString("$$pop$$useParam2WithParam1Type"));
                                final JCTree.JCMethodInvocation invokeUseParam2WithParam1Type = make.Apply(List.nil(), $$pop$$useParam2WithParam1Type, List.of(copyRestExpr.setPos(Position.NOPOS), elseExpr.setPos(Position.NOPOS)));
                                conditional = make.Conditional(make.Binary(JCTree.Tag.NE, lastExprLeftAndDup, nullLiteral), restExpr, invokeUseParam2WithParam1Type);
                            } else {
                                final JCTree.JCFieldAccess $$pop = make.Select(make.QualIdent(biopClass), names.fromString("$$pop"));
                                final JCTree.JCMethodInvocation invokePop = make.Apply(List.nil(), $$pop, List.of(elseExpr));
                                conditional = make.Conditional(make.Binary(JCTree.Tag.NE, lastExprLeftAndDup, nullLiteral), restExpr, invokePop);
                            }


                            conditional.pos = expr.pos + 1;
                            if (replace == null) {
                                allExpr = conditional;//a.b==null?null:a.b.c
                            } else {
                                replace.accept(conditional);
                            }
                            replace = (ex) -> {
                                lastExprLeftAndDup.args = List.of(ex);
                            };
                            restExpr = lastExprLeft;
                            elseExpr = nullLiteral;
                            return change(lastExprLeft, restExpr, replace, allExpr, trueExpr, elseExpr, varDecls, useVarAndSkipWarpByFirst, hasSkip);
                        } else {
                            final Type symsType = syms.objectType;

                            final JCTree.JCVariableDecl jcVariableDecl = make.VarDef(make.Modifiers(Flags.FINAL), names.fromString("ZROPTIONALCHAINING"), make.Type(symsType), restExpr);
                            jcVariableDecl.type = symsType;

                            jcVariableDecl.sym = new Symbol.VarSymbol(Flags.FINAL, jcVariableDecl.name, symsType, zrAttr.env.enclMethod.sym);
                            final JCTree.JCExpression ident = make.at(pos + 1).Ident(jcVariableDecl);


                            final Symbol.ClassSymbol biopClass = getBiopClass();
                            final JCTree.JCFieldAccess useParam2WithParam1Type = make.Select(make.QualIdent(biopClass), names.fromString("$$useParam2WithParam1Type"));
                            final JCTree.JCMethodInvocation copy = make.Apply(List.nil(), useParam2WithParam1Type, List.of(cleanExprNullSafeFlag(lastExprLeft), ident));
                            ((JCTree.JCFieldAccess) meth).selected = copy;//a.b.c
                            varDecls.add(jcVariableDecl);
                            final JCTree.JCBinary binary = make.Binary(JCTree.Tag.NE, ident, nullLiteral);
                            final JCTree.JCConditional conditional = make.Conditional(binary, restExpr, elseExpr);

                            conditional.pos = expr.pos + 1;

                            if (replace == null) {
                                allExpr = conditional;//a.b==null?null:a.b.c
                            } else {
                                replace.accept(conditional);
                            }
                            replace = (ex) -> {
                                jcVariableDecl.init = ex;
                            };
                            restExpr = lastExprLeft;
                            elseExpr = nullLiteral;
                            return change(lastExprLeft, restExpr, replace, allExpr, trueExpr, elseExpr, varDecls, useVarAndSkipWarpByFirst, hasSkip);
                        }


                    } else if (lastExpr instanceof JCTree.JCMethodInvocation) {
                        return change(lastExpr, restExpr, replace, allExpr, trueExpr, elseExpr, varDecls, useVarAndSkipWarpByFirst, hasSkip);
                    } else if (lastExpr instanceof JCTree.JCFieldAccess) {
                        return change(lastExpr, restExpr, replace, allExpr, trueExpr, elseExpr, varDecls, useVarAndSkipWarpByFirst, hasSkip);
                    } else {
                        if (replace != null) {
                            replace.accept(restExpr);
                        }
                        return allExpr;
                    }
                } else {
                    if (replace != null) {
                        replace.accept(restExpr);
                    }
                    return allExpr;
                }
            } else if (expr instanceof JCTree.JCParens) {
                if (replace != null) {
                    replace.accept(restExpr);
                }
                ((JCTree.JCParens) expr).expr = change(((JCTree.JCParens) expr).expr);


                return allExpr;
            }
            if (replace != null) {
                replace.accept(restExpr);
            }
            return allExpr;
        }
    }

    public Symbol.ClassSymbol getBiopClass() {
        final Symbol.ClassSymbol biopClass = syms.classes.get(names.fromString("zircon.BiOp"));
        return biopClass;
    }


    protected JCTree.JCStatement changeOptionalChainingExpression2Call(JCTree.JCExpression allExpr, Env<AttrContext> env) {
        return changeOptionalChainingExpression2Call(allExpr, (trueExpression) -> {
            return make.Exec(trueExpression);
        }, env);
    }

    protected JCTree.JCStatement changeOptionalChainingExpression2Call(JCTree.JCExpression allExpr, Function<JCTree.JCExpression, JCTree.JCStatement> action, Env<AttrContext> env) {
        return changeOptionalChainingExpression2Call(allExpr, action, null, env);
    }

    //转语句
    protected JCTree.JCStatement changeOptionalChainingExpression2Call(JCTree.JCExpression allExpr, Function<JCTree.JCExpression, JCTree.JCStatement> action, JCTree.JCStatement elseAction, Env<AttrContext> env) {
        make.at(allExpr);
        final ArrayList<JCTree.JCVariableDecl> varDecls = new ArrayList<>();
        final JCTree.JCLiteral nullLiteral = make.Literal(TypeTag.BOT, null);
        final JCTree.JCExpression expression = changeOptionalChainingExpression2Expression.change(null, allExpr, null, allExpr, e -> e, nullLiteral, varDecls, true, false);
        if (expression instanceof JCTree.JCConditional) {
            List<JCTree.JCStatement> statements = List.nil();
            final JCTree.JCVariableDecl variableDecl = varDecls.get(0);
            if (!(variableDecl.getInitializer() instanceof JCTree.JCConditional)) {
                //防止flow自动内联
                final Symbol.ClassSymbol biopClass = getBiopClass();
                final JCTree.JCFieldAccess wrap = make.Select(make.QualIdent(biopClass), names.fromString("$$wrap"));
                variableDecl.init = make.Apply(List.nil(), wrap, List.of(variableDecl.getInitializer()));
            }

            statements = statements.prepend(variableDecl);
            {
                final JCTree.JCExpression trueExpression = ((JCTree.JCConditional) expression).getTrueExpression();
                final JCTree.JCExpression cond = make.Binary(JCTree.Tag.NE, make.Ident(variableDecl.sym), nullLiteral);
                final JCTree.JCIf anIf = make.If(cond, make.Block(0, List.of(action.apply(trueExpression))), elseAction);
                statements = statements.append(anIf);
            }
            final JCTree.JCBlock block = make.Block(0, statements);

            return block;
        } else {

            return make.Exec(expression);
        }

    }

    boolean findChainHasNullSafeFlag(JCTree.JCExpression expr, boolean found) {
        if (expr instanceof JCTree.JCFieldAccess) {
            return findChainHasNullSafeFlag(((JCTree.JCFieldAccess) expr).selected, found);
        } else if (expr instanceof JCTree.JCMethodInvocation) {
            if (isNullSafeMethod(expr)) {
                return true;
            }
            final JCTree.JCExpression meth = ((JCTree.JCMethodInvocation) expr).meth;
            return findChainHasNullSafeFlag(meth, found);
        } else {
            return found;
        }
    }

    /**
     * 判断是否为 $$NullSafe 方法调用。
     */
    private boolean isNullSafeMethod(JCTree.JCExpression expr) {
        if (expr instanceof JCTree.JCMethodInvocation) {
            JCTree.JCMethodInvocation invoc = (JCTree.JCMethodInvocation) expr;
            if (invoc.meth instanceof JCTree.JCFieldAccess) {
                final JCTree.JCFieldAccess meth = (JCTree.JCFieldAccess) invoc.meth;
                return meth.name.toString().equals("$$NullSafe");
            }
        }
        return false;
    }

    public <T, R> List<R> listMap(List<T> list, Function<T, R> mapper) {
        List<R> r = List.nil();
        for (T t : list) {
            r = r.append(mapper.apply(t));
        }
        return r;
    }
}
