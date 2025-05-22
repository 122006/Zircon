package com.sun.tools.javac.comp;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrUnSupportCodeError;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.sun.tools.javac.code.TypeTag.VOID;

public class ZrAttr extends Attr {
    private final Context context;

    protected ZrAttr(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public void visitModuleDef(JCTree.JCModuleDecl tree) {
        super.visitModuleDef(tree);
        if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining"))
            System.err.println(tree);
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
    public Type attribStat(JCTree tree, Env<AttrContext> env) {

        if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining"))
            System.err.println("attribStat start  " + tree);

        return super.attribStat(tree, env);
    }

    @Override
    <T extends JCTree> void attribStats(List<T> trees, Env<AttrContext> env) {
        System.err.println("attribStats start");
        final Pair<Boolean, List<JCTree>> booleanListPair = treeTranslator(trees, env);
        if (booleanListPair.fst) {
            if (env.tree instanceof JCTree.JCBlock) {
//                argumentAttr.argumentTypeCache.clear();
                ((JCTree.JCBlock) env.tree).stats = booleanListPair.snd.map(a -> (JCTree.JCStatement) a);
                super.attribStats(((JCTree.JCBlock) env.tree).stats, env);
                System.err.println("attribStats end " + ((JCTree.JCBlock) env.tree).stats);
                return;
            }
        }
        super.attribStats(trees, env);
    }

    public void visitVarDef(JCTree.JCVariableDecl that) {
        try {
            super.visitVarDef(that);
        } catch (NeedReplaceLambda needReplaceLambda) {
            JCTree.JCExpression initializer = that.getInitializer();
            while (initializer instanceof JCTree.JCParens) {
                initializer = ((JCTree.JCParens) initializer).getExpression();
            }
            if (Objects.equals(initializer.getStartPosition(), needReplaceLambda.memberReference.getStartPosition())) {
                that.init = needReplaceLambda.bestSoFar;
            }
            super.visitVarDef(that);
        }
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining"))
            System.err.println("visitApply start  " + that);

        Name methName = TreeInfo.name(that.meth);
        boolean isConstructorCall = methName == this.names._this || methName == this.names._super;
        if (isConstructorCall) {
            super.visitApply(that);
        } else {
            visitNoConstructorApply(that);
        }
        if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining"))
            System.err.println("visitApply end  " + that);

    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        super.visitMethodDef(tree);

    }

//    JCTree.JCExpression treeTranslator(JCTree.JCExpression tree) {
//        return treeTranslator(tree,null);
//    }

    JCTree.JCExpression treeTranslator(JCTree.JCExpression tree) {
        if (tree instanceof JCTree.JCBinary) {
            if (tree.getTag() == JCTree.Tag.OR) {
                final JCTree.JCExpression lhs = ((JCTree.JCBinary) tree).lhs;
                final JCTree.JCExpression rhs = ((JCTree.JCBinary) tree).rhs;
                if (checkMethodInvocationIsOptionalChaining(lhs)) {
                    return changeOptionalChainingExpression2Expression(lhs, (e) -> e, rhs);
                }
            }
        } else if (tree instanceof JCTree.JCAssign) {
            final JCTree.JCExpression variable = ((JCTree.JCAssign) tree).getVariable();
            if (checkMethodInvocationIsOptionalChaining(variable)) {
                //特别的，非语句的赋值表达式会判断左值是否存在，如果不存在该左值不会被赋值
                final JCTree.JCExpression finalElseExpr = ((JCTree.JCAssign) tree).getExpression();
                return changeOptionalChainingExpression2Expression(variable, e -> make.Assign(e, finalElseExpr), finalElseExpr);
            }
        } else if (checkMethodInvocationIsOptionalChaining(tree)) {
            return changeOptionalChainingExpression2Expression(tree, (e) -> e, make.Literal(TypeTag.BOT, null));
        }
        return tree;
    }

    <T extends JCTree> Pair<Boolean, List<JCTree>> treeTranslator(List<T> trees, Env<AttrContext> env) {

        List<JCTree> nList = List.nil();
        boolean replace = false;
        for (JCTree tree : trees) {
            if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining")) {
                System.err.println("treeTranslator:" + tree);
            }
            if (tree instanceof JCTree.JCExpressionStatement) {
                final JCTree.JCExpression expression = ((JCTree.JCExpressionStatement) tree).getExpression();
                if (expression instanceof JCTree.JCAssign) {
                    final JCTree.JCExpression variable = ((JCTree.JCAssign) expression).getVariable();
                    if (checkMethodInvocationIsOptionalChaining(variable)) {
                        final JCTree jcTree = changeOptionalChainingExpression2Call(variable, (trueExpr) -> {
                            return make.Exec(make.Assign(trueExpr, ((JCTree.JCAssign) expression).getExpression()));
                        }, env);
                        nList = nList.append(jcTree);
                        replace = true;
                        continue;
                    }
                } else if (findChainHasNullSafeFlag(expression, false)) {
                    final JCTree.JCExpressionStatement statement = (JCTree.JCExpressionStatement) tree;

                    final JCTree jcTree = changeOptionalChainingExpression2Call(statement.getExpression(), env);

                    nList = nList.append(jcTree);
                    if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining")) {
                        System.err.println("tree:" + tree);
                        System.err.println("=>" + jcTree);
                        new RuntimeException().printStackTrace();
                    }
                    replace = true;
                    continue;
                }

            } else if (tree instanceof JCTree.JCReturn) {
                final JCTree.JCExpression expression = ((JCTree.JCReturn) tree).getExpression();
                if (expression instanceof JCTree.JCAssign) {
                    final JCTree.JCExpression variable = ((JCTree.JCAssign) expression).getVariable();
                    if (checkMethodInvocationIsOptionalChaining(variable)) {
                        final JCTree jcTree = changeOptionalChainingExpression2Call(variable, (trueExpr) -> {
                            return make.Exec(make.Assign(trueExpr, ((JCTree.JCAssign) expression).getExpression()));
                        }, env);
                        nList = nList.append(jcTree);
                        replace = true;
                        continue;
                    }
                } else if (findChainHasNullSafeFlag(expression, false)) {
                    final JCTree.JCReturn statement = (JCTree.JCReturn) tree;

                    final JCTree jcTree = changeOptionalChainingExpression2Call(statement.getExpression(), make::Return, make.Return(make.Literal(TypeTag.BOT, null)), env);
                    if (jcTree instanceof JCTree.JCBlock) {
                        nList = nList.appendList(((JCTree.JCBlock) jcTree).stats.map(a -> a));

                    }
                    if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining")) {
                        System.err.println("tree:" + tree);
                        System.err.println("=>" + jcTree);
                        new RuntimeException().printStackTrace();
                    }
                    replace = true;
                    continue;
                }

            } else {
                if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining")) {
                    System.err.println("unknown statement:" + tree + "  " + tree.getClass().getName());
                }
            }
            nList = nList.append(tree);
        }
        return Pair.of(replace, nList);
    }

    @Override
    Type attribTree(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining")) {
            System.err.println("=======" + tree + " " + tree.getClass().getName());
            if (tree instanceof JCTree.JCVariableDecl) {
                ((JCTree.JCVariableDecl) tree).init = treeTranslator(((JCTree.JCVariableDecl) tree).init);
            } else if (tree instanceof JCTree.JCBinary) {
                ((JCTree.JCBinary) tree).lhs = treeTranslator(((JCTree.JCBinary) tree).lhs);
                ((JCTree.JCBinary) tree).rhs = treeTranslator(((JCTree.JCBinary) tree).rhs);
            } else if (tree instanceof JCTree.JCParens) {
                ((JCTree.JCParens) tree).expr = treeTranslator(((JCTree.JCParens) tree).expr);
            } else if (tree instanceof JCTree.JCIf) {
                ((JCTree.JCIf) tree).cond = treeTranslator(((JCTree.JCIf) tree).cond);
            } else if (tree instanceof JCTree.JCSwitch) {
                ((JCTree.JCSwitch) tree).selector = treeTranslator(((JCTree.JCSwitch) tree).selector);
            } else if (tree instanceof JCTree.JCDoWhileLoop) {
                ((JCTree.JCDoWhileLoop) tree).cond = treeTranslator(((JCTree.JCDoWhileLoop) tree).cond);
            } else if (tree instanceof JCTree.JCConditional) {
                ((JCTree.JCConditional) tree).cond = treeTranslator(((JCTree.JCConditional) tree).cond);
                ((JCTree.JCConditional) tree).truepart = treeTranslator(((JCTree.JCConditional) tree).truepart);
                ((JCTree.JCConditional) tree).falsepart = treeTranslator(((JCTree.JCConditional) tree).falsepart);
            } else if (tree instanceof JCTree.JCAssert) {
                ((JCTree.JCAssert) tree).cond = treeTranslator(((JCTree.JCAssert) tree).cond);
            } else if (tree instanceof JCTree.JCReturn) {
                System.err.println("JCTree.JCReturn:" + tree);
                final JCTree.JCExpression expr = treeTranslator(((JCTree.JCReturn) tree).expr);
                System.err.println("JCTree.JCReturn:=>" + expr);
                ((JCTree.JCReturn) tree).expr = expr;
            } else if (tree instanceof JCTree.JCLambda) {
                JCTree body = ((JCTree.JCLambda) tree).body;
                final LambdaExpressionTree.BodyKind bodyKind = ((JCTree.JCLambda) tree).getBodyKind();
                System.err.println("JCTree.JCLambda:" + tree + "  " + tree.type + "  " + bodyKind + "   " + pt() + "  " + pt().getReturnType());

                if (bodyKind == LambdaExpressionTree.BodyKind.STATEMENT) {
                    final Pair<Boolean, List<JCTree>> pair = treeTranslator(((JCTree.JCBlock) body).stats, env);
                    if (pair.fst) {
                        ((JCTree.JCLambda) tree).body = make.at(tree.pos).Block(0, pair.snd.map(a -> (JCTree.JCStatement) a));
                    }
                } else if (bodyKind == LambdaExpressionTree.BodyKind.EXPRESSION) {
                    final JCTree.JCExpression expression = (JCTree.JCExpression) body;
                    if (findChainHasNullSafeFlag(expression, false)) {
                        boolean returnVoid = false;
                        if (expression instanceof JCTree.JCFieldAccess) {
                            returnVoid = false;
                        } else if (expression instanceof JCTree.JCMethodInvocation) {
                            final JCTree.JCExpression jcExpression = cleanExprNullSafeFlag(expression);
//                            jcExpression.accept(this);
                            final Type type = getType(env, jcExpression);
                            System.err.println("JCTree.JCLambda==>>>>:" + jcExpression);
                            System.err.println("JCTree.JCLambda==>>>>:" + type);
                            returnVoid = type == syms.voidType;
                        }
                        if (returnVoid) {
                            JCTree.JCStatement expressionStatement = make.at(tree.pos).Exec(expression);
                            final Pair<Boolean, List<JCTree>> pair = treeTranslator(List.of(expressionStatement), env);
                            if (pair.fst) {
                                ((JCTree.JCLambda) tree).body = make.at(tree.pos).Block(0, pair.snd.map(a -> (JCTree.JCStatement) a));
                            }
                        } else {
                            ((JCTree.JCLambda) tree).body = treeTranslator(expression);
                        }
                        System.err.println("JCTree.JCLambda==>>>>:" + ((JCTree.JCLambda) tree));


//                        JCTree.JCReturn expressionStatement = make.at(tree.pos).Return(expression);
//                        final Pair<Boolean, List<JCTree>> pair = treeTranslator(List.of(expressionStatement), env);
//                        if (pair.fst) {
//                            ((JCTree.JCLambda) tree).body = make.at(tree.pos).Block(0, pair.snd.map(a -> (JCTree.JCStatement) a));
//                        }
                    }
                }
                System.err.println("JCTree.JCLambda:=>" + tree);
            }
        }
        final Type type = super.attribTree(tree, env, resultInfo);

        return type;
    }

    private Type getType(Env<AttrContext> env, JCTree.JCExpression jcExpression) {
        return attribTree(jcExpression, env.dup(jcExpression, env.info.dup()), new ResultInfo(Kinds.KindSelector.VAL, Infer.anyPoly));
    }

    public <E extends JCTree> E copyExpr(E expr) {
        return new TreeCopier<Void>(make).copy(expr);
    }

    public JCTree.JCExpression cleanExprNullSafeFlag(JCTree.JCExpression expr) {
        expr = copyExpr(expr);
        System.err.println("=>cleanExprNullSafeFlag:" + expr);
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
                }
                super.visitSelect(tree);
            }
        }.scan(expr);
        if (expr instanceof JCTree.JCParens) expr = ((JCTree.JCParens) expr).expr;
        System.err.println("=>cleanExprNullSafeFlag:" + expr);
        return expr;
//        return _cleanExprNullSafeFlag(expr, expr);
    }

    public JCTree.JCExpression _cleanExprNullSafeFlag(JCTree jctree, JCTree.JCExpression allExpr) {
        if (jctree instanceof JCTree.JCMethodInvocation) {
            final JCTree.JCExpression meth = ((JCTree.JCMethodInvocation) jctree).meth;
            if (meth instanceof JCTree.JCFieldAccess) {
                final JCTree.JCExpression selected = ((JCTree.JCFieldAccess) meth).selected;
                if (selected instanceof JCTree.JCMethodInvocation) {
                    final JCTree.JCExpression lastMeth = ((JCTree.JCMethodInvocation) selected).meth;
                    if (lastMeth instanceof JCTree.JCFieldAccess && ((JCTree.JCFieldAccess) lastMeth).name.contentEquals("$$NullSafe")) {
                        ((JCTree.JCFieldAccess) meth).selected = ((JCTree.JCFieldAccess) lastMeth).getExpression();
                        return _cleanExprNullSafeFlag(((JCTree.JCFieldAccess) meth).selected, allExpr);
                    } else return _cleanExprNullSafeFlag(selected, allExpr);
                } else if (selected instanceof JCTree.JCFieldAccess) {
                    return _cleanExprNullSafeFlag(selected, allExpr);
                } else return allExpr;
            } else return allExpr;
        } else if (jctree instanceof JCTree.JCFieldAccess) {
            return _cleanExprNullSafeFlag(((JCTree.JCFieldAccess) jctree).selected, allExpr);
        } else return allExpr;
    }

    @Override
    public void visitLambda(JCTree.JCLambda that) {
        super.visitLambda(that);
    }

    @Override
    public void visitTypeTest(JCTree.JCInstanceOf tree) {
        if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining"))
            System.err.println("~~~~~~ visitTypeTest start " + tree + " " + tree.getClass().getName());
        super.visitTypeTest(tree);
        if (env.enclClass.getSimpleName().contentEquals("TestOptionalChaining"))
            System.err.println("~~~~~~ visitTypeTest " + tree + " " + tree.getClass().getName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void visitNoConstructorApply(JCTree.JCMethodInvocation that) {
        Env<AttrContext> localEnv = this.env.dup(that, ((AttrContext) this.env.info).dup());
        Name methName = TreeInfo.name(that.meth);
        ListBuffer<Type> argtypesBuf = new ListBuffer();
        Kinds.KindSelector kind;
        try {
            kind = this.attribArgs(Kinds.KindSelector.VAL, that.args, localEnv, argtypesBuf);
        } catch (NeedReplaceLambda needReplaceLambda) {
            List<JCTree.JCExpression> newList = List.nil();
            for (int i = 0; i < that.args.size(); i++) {
                JCTree.JCExpression argument = that.args.get(i);
                while (argument instanceof JCTree.JCParens) {
                    argument = ((JCTree.JCParens) argument).getExpression();
                }
                if (Objects.equals(argument.getStartPosition(), needReplaceLambda.memberReference.getStartPosition())) {
                    newList = newList.append(needReplaceLambda.bestSoFar);
                } else {
                    newList = newList.append(argument);
                }
            }
            that.args = newList;
            kind = this.attribArgs(Kinds.KindSelector.VAL, that.args, localEnv, argtypesBuf);
        }
        List<Type> argtypes = argtypesBuf.toList();
        List<Type> typeargtypes = this.attribAnyTypes(that.typeargs, localEnv);
        final Type pt = this.resultInfo.pt;
        Type methodTemplate = this.newMethodTemplate(pt, argtypes, typeargtypes);
        Type encl;
        try {
            encl = this.attribTree(that.meth, localEnv, new ResultInfo(kind, methodTemplate, this.resultInfo.checkContext));
        } catch (NeedRedirectMethod redirectMethod) {
            final ExMethodInfo methodInfo = redirectMethod.exMethodInfo;
            if (methodInfo == null) throw new ZrUnSupportCodeError("拓展方法解析异常：异常匹配的方法信息。于：" + that);
            final JCTree.JCMethodInvocation oldTree = make.Apply(that.typeargs, that.meth, that.args);
            final Symbol bestSoFar = redirectMethod.bestSoFar;
            final JCTree.JCFieldAccess add = make.Select(make.Ident(bestSoFar.owner), bestSoFar.name);
            if (methodInfo.siteCopyByClassHeadArgMethod) {
                if (that.meth instanceof JCTree.JCFieldAccess) {
                    final Type currentClassType = redirectMethod.site;
                    final Type type = new Type.ClassType(Type.noType, List.of(currentClassType), syms.classType.tsym);
                    final JCTree.JCExpression jcExpression = make.ClassLiteral(currentClassType).setType(type);
                    that.args = that.args.prepend(jcExpression);
                    argtypes = argtypes.prepend(type);
                } else if (that.meth instanceof JCTree.JCIdent) {
                    final Type oType = env.enclClass.type;
                    final Type type = new Type.ClassType(Type.noType, List.of(oType), syms.classType.tsym);
                    final JCTree.JCExpression x = make.ClassLiteral(oType);
                    that.args = that.args.prepend(x);
                    argtypes = argtypes.prepend(type);
                } else {
                    throw new ZrUnSupportCodeError("拓展方法解析异常：使用Class定义的实例拓展方法，匹配至其静态副本，但是其site不是JCFieldAccess或者JCIdent。于：" + that);
                }
            } else if (!methodInfo.isStatic) {
                JCTree.JCExpression prepend = null;
                if (that.meth instanceof JCTree.JCFieldAccess) {
                    prepend = ((JCTree.JCFieldAccess) that.meth).selected;
                } else if (that.meth instanceof JCTree.JCIdent) {
                    final Type oType = env.enclClass.type;
                    prepend = make.This(oType);
                }
                if (prepend != null) {
                    that.args = that.args.prepend(prepend);
                    argtypes = argtypes.prepend(prepend.type);
                } else {
                    throw new ZrUnSupportCodeError("拓展方法解析异常：实例拓展方法，但是其site不是JCFieldAccess或者JCIdent(" + that.meth.getClass() + ")。于：" + that);
                }
            }
            methodTemplate = this.newMethodTemplate(pt, argtypes, typeargtypes);
            JCTree.JCExpression oldMeth = that.meth;
            final boolean hasTagSelect = oldMeth.hasTag(JCTree.Tag.SELECT);
            final JCTree.JCExpression selected = !hasTagSelect ? null : ((JCTree.JCFieldAccess) oldMeth).selected;
            that.meth = add;
            that.type = redirectMethod.bestSoFar.type;
            if (hasTagSelect) {
                final boolean staticInvoke = selected.hasTag(JCTree.Tag.IDENT) || TreeInfo.isStaticSelector(selected, names);
                if (!staticInvoke) {
                    if (methodInfo.isStatic) {
                        if (((Symbol.MethodSymbol) bestSoFar).getReturnType().hasTag(VOID)) {
                            throw new ZrUnSupportCodeError("对实例对象调用无返回值的静态方法", oldTree);
                        } else {
                            final Symbol.ClassSymbol biopClass = syms.getClass(syms.unnamedModule, names.fromString("zircon.BiOp"));
                            final JCTree.JCFieldAccess and = make.Select(make.QualIdent(biopClass), names.fromString("sec"));
                            final JCTree.JCMethodInvocation copy = make.Apply(that.typeargs, that.meth, that.args);
                            that.typeargs = List.nil();
                            that.meth = and;
                            that.args = List.of(selected, copy);
                            this.visitApply(that);
                            return;
                        }
                    }
                }
            }
//            System.err.println("==============\n" + oldTree + "=>\n" + that);
            encl = this.attribTree(that.meth, localEnv, new ResultInfo(kind, methodTemplate, this.resultInfo.checkContext));
        }

        Type restype = encl.getReturnType();
        if (restype.hasTag(TypeTag.WILDCARD)) {
            throw new AssertionError(encl);
        }
        Type qualifier = that.meth.hasTag(JCTree.Tag.SELECT) ? ((JCTree.JCFieldAccess) that.meth).selected.type : this.env.enclClass.sym.type;
        Symbol msym = TreeInfo.symbol(that.meth);
        restype = this.adjustMethodReturnType(msym, qualifier, methName, argtypes, restype);
        this.chk.checkRefTypes(that.typeargs, typeargtypes);
        Type capturedRes = this.resultInfo.checkContext.inferenceContext().cachedCapture(that, restype, true);
        this.result = this.check(that, capturedRes, Kinds.KindSelector.VAL, this.resultInfo);
        this.chk.validate(that.typeargs, localEnv);

    }

    private boolean checkMethodInvocationIsOptionalChaining(JCTree jctree) {
        if (jctree instanceof JCTree.JCMethodInvocation) {
            return findChainHasNullSafeFlag(((JCTree.JCMethodInvocation) jctree).meth, false);
        } else if (jctree instanceof JCTree.JCFieldAccess) {
            return findChainHasNullSafeFlag(((JCTree.JCFieldAccess) jctree).getExpression(), false);
        } else return false;
    }

    private JCTree.JCExpression changeOptionalChainingExpression2Expression(JCTree.JCExpression expr) {
        return changeOptionalChainingExpression2Expression(expr, (e) -> e, make.Literal(TypeTag.BOT, null));
    }

    private JCTree.JCExpression changeOptionalChainingExpression2Expression(JCTree.JCExpression expr, Function<JCTree.JCExpression, JCTree.JCExpression> trueExpr, JCTree.JCExpression elseExpr) {
        final JCTree.JCExpression jcExpression = changeOptionalChainingExpression2Expression(null, expr, null, expr, trueExpr, elseExpr, new ArrayList<>(), false, false);
        if (jcExpression instanceof JCTree.JCConditional && elseExpr.getKind() == TypeTag.BOT.getKindLiteral()) {
            final Symbol.ClassSymbol biopClass = syms.getClass(syms.unnamedModule, names.fromString("zircon.BiOp"));
            final JCTree.JCFieldAccess wrapMethod = make.Select(make.QualIdent(biopClass), names.fromString("wrap"));
            argumentAttr.argumentTypeCache.clear();
            ((JCTree.JCConditional) jcExpression).truepart = make.Apply(List.nil(), wrapMethod, List.of(((JCTree.JCConditional) jcExpression).truepart.setPos(expr.pos + 7)));
        }
        return jcExpression;
    }

    //转三元表达式
    private JCTree.JCExpression changeOptionalChainingExpression2Expression(JCTree.JCExpression expr, JCTree.JCExpression restExpr, Consumer<JCTree.JCExpression> replace, JCTree.JCExpression allExpr, Function<JCTree.JCExpression, JCTree.JCExpression> trueExpr, JCTree.JCExpression elseExpr, java.util.List<JCTree.JCVariableDecl> varDecls, boolean useVarAndSkipWarpByFirst, boolean hasSkip) {
        if (expr == null) {
            expr = allExpr;
        }
        make.at(expr);
        final int pos = expr.pos;
        System.err.println("   + " + expr + "   pos    " + pos + "   rest    " + restExpr + "   all:    " + allExpr);
        if (expr instanceof JCTree.JCMethodInvocation) {//a.b.$$NullSafe().c()
            final JCTree.JCExpression meth = ((JCTree.JCMethodInvocation) expr).meth;//a.b.$$NullSafe().c
            if (meth instanceof JCTree.JCFieldAccess) {
                final JCTree.JCExpression lastExpr = ((JCTree.JCFieldAccess) meth).getExpression();//a.b.$$NullSafe()
                if (isNullSafeMethod(lastExpr)) {
                    final JCTree.JCExpression lastMethodSelect = ((JCTree.JCMethodInvocation) lastExpr).getMethodSelect();//a.b.$$NullSafe
                    System.err.println("   lastMethodSelect=" + lastMethodSelect + "  " + lastMethodSelect.getClass().getName());
                    final JCTree.JCExpression lastExprLeft = ((JCTree.JCFieldAccess) lastMethodSelect).getExpression();//a.b

                    boolean wrap = !useVarAndSkipWarpByFirst || hasSkip;
                    final JCTree.JCLiteral nullLiteral = make.Literal(TypeTag.BOT, null);
                    hasSkip = true;
                    if (wrap) {
//                        if (_lastExprLeft instanceof JCTree.JCParens) {
//                            _lastExprLeft = ((JCTree.JCParens) _lastExprLeft).getExpression();
//                        }
                        final Symbol.ClassSymbol biopClass = syms.getClass(syms.unnamedModule, names.fromString("zircon.BiOp"));
                        final JCTree.JCFieldAccess dup = make.Select(make.QualIdent(biopClass), names.fromString("$$dup"));
                        final JCTree.JCFieldAccess ignore = make.Select(make.QualIdent(biopClass), names.fromString("$$ignore"));


                        final JCTree.JCMethodInvocation copy = make.Apply(List.nil(), ignore, List.of(cleanExprNullSafeFlag(lastExprLeft.setPos(pos + 6))));
//                        final JCTree.JCMethodInvocation copy = make.Apply(List.nil(), ignore, List.of(make.Literal(TypeTag.BOT, null)));
                        final JCTree.JCMethodInvocation lastExprLeftAndDup = make.Apply(List.nil(), dup, List.of(lastExprLeft.setPos(pos + 2)));

                        JCTree.JCExpression copyRestExpr = cleanExprNullSafeFlag(restExpr);

                        ((JCTree.JCFieldAccess) meth).selected = copy;//a.b.c
                        final JCTree.JCConditional conditional;
                        if (elseExpr.getKind() == TypeTag.BOT.getKindLiteral()) {
                            final JCTree.JCFieldAccess $$pop$$useParam2WithParam1Type = make.Select(make.QualIdent(biopClass), names.fromString("$$pop$$useParam2WithParam1Type"));
                            final JCTree.JCMethodInvocation invokeUseParam2WithParam1Type = make.Apply(List.nil(), $$pop$$useParam2WithParam1Type, List.of(copyRestExpr.setPos(pos + 4), elseExpr.setPos(pos + 3)));
                            conditional = make.Conditional(
                                    make.Binary(JCTree.Tag.NE, lastExprLeftAndDup, nullLiteral),
                                    restExpr,
                                    invokeUseParam2WithParam1Type
                            );
                        } else {
                            final JCTree.JCFieldAccess $$pop = make.Select(make.QualIdent(biopClass), names.fromString("$$pop"));
                            final JCTree.JCMethodInvocation invokePop = make.Apply(List.nil(), $$pop, List.of(elseExpr.setPos(pos + 3)));
                            conditional = make.Conditional(
                                    make.Binary(JCTree.Tag.NE, lastExprLeftAndDup, nullLiteral),
                                    restExpr,
                                    invokePop
                            );
                        }


                        System.err.println("     conditional=" + conditional);
                        conditional.pos = expr.pos + 1;
                        if (replace == null) {
                            allExpr = conditional;//a.b==null?null:a.b.c
                        } else {
                            replace.accept(conditional);
                        }
                        replace = (ex) -> {
//                            copy.args = List.of(ex);
                            lastExprLeftAndDup.args = List.of(ex);
                        };
                        restExpr = lastExprLeft;
                        elseExpr = nullLiteral;
                        return changeOptionalChainingExpression2Expression(lastExprLeft, restExpr, replace, allExpr, trueExpr, elseExpr, varDecls, useVarAndSkipWarpByFirst, hasSkip);
                    } else {
                        final Type symsType = syms.objectType;
                        System.err.println("restExpr::" + restExpr + "  " + restExpr.getClass().getName());
                        final JCTree.JCVariableDecl jcVariableDecl = make.VarDef(make.Modifiers(Flags.FINAL), names.fromString("ZROPTIONALCHAINING"), make.Type(symsType), restExpr);
                        jcVariableDecl.type = symsType;
                        //                        jcVariableDecl.accept(this);


                        jcVariableDecl.sym = new Symbol.VarSymbol(
                                Flags.FINAL,
                                jcVariableDecl.name,
                                symsType,
                                env.enclMethod.sym
                        );
//                        env.info.scope.enter(jcVariableDecl.sym);
                        final JCTree.JCExpression ident = make.at(pos + 1).Ident(jcVariableDecl);


                        final Symbol.ClassSymbol biopClass = syms.getClass(syms.unnamedModule, names.fromString("zircon.BiOp"));
                        final JCTree.JCFieldAccess useParam2WithParam1Type = make.Select(make.QualIdent(biopClass), names.fromString("$$useParam2WithParam1Type"));
                        final JCTree.JCMethodInvocation copy = make.Apply(List.nil(), useParam2WithParam1Type, List.of(cleanExprNullSafeFlag(lastExprLeft), ident));

//                        final JCTree.JCTypeCast copy = make.TypeCast(symsType, ident);
                        ((JCTree.JCFieldAccess) meth).selected = copy;//a.b.c
                        varDecls.add(jcVariableDecl);
                        final JCTree.JCBinary binary = make.Binary(JCTree.Tag.NE, ident, nullLiteral);
                        final JCTree.JCConditional conditional = make.Conditional(
                                binary,
                                restExpr,
                                elseExpr
                        );
                        System.err.println("     conditional=" + conditional);
                        conditional.pos = expr.pos + 1;

                        if (replace == null) {
                            allExpr = conditional;//a.b==null?null:a.b.c
                        } else {
                            replace.accept(conditional);
                        }
                        replace = (ex) -> {
//                            copy.args = List.of(ex, copy.args.get(1));
                            jcVariableDecl.init = ex;
                        };
                        restExpr = lastExprLeft;
                        elseExpr = nullLiteral;
                        return changeOptionalChainingExpression2Expression(lastExprLeft, restExpr, replace, allExpr, trueExpr, elseExpr, varDecls, useVarAndSkipWarpByFirst, hasSkip);
                    }


                } else if (lastExpr instanceof JCTree.JCMethodInvocation) {
                    return changeOptionalChainingExpression2Expression(lastExpr, restExpr, replace, allExpr, trueExpr, elseExpr, varDecls, useVarAndSkipWarpByFirst, hasSkip);
                } else if (lastExpr instanceof JCTree.JCFieldAccess) {
                    return changeOptionalChainingExpression2Expression(lastExpr, restExpr, replace, allExpr, trueExpr, elseExpr, varDecls, useVarAndSkipWarpByFirst, hasSkip);
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
        } else if (expr instanceof JCTree.JCFieldAccess) {
            final JCTree.JCExpression lastExpr = ((JCTree.JCFieldAccess) expr).getExpression();
            return changeOptionalChainingExpression2Expression(lastExpr, restExpr, replace, allExpr, trueExpr, elseExpr, varDecls, useVarAndSkipWarpByFirst, hasSkip);
        } else if (expr instanceof JCTree.JCParens) {
            if (replace != null) {
                replace.accept(restExpr);
            }
            ((JCTree.JCParens) expr).expr = changeOptionalChainingExpression2Expression(((JCTree.JCParens) expr).expr);

            System.err.println("find JCParens : all allExpr" + allExpr);

            return allExpr;
        }
        if (replace != null) {
            replace.accept(restExpr);
        }
        return allExpr;
    }

//    @Override
//    public void visitIdent(JCTree.JCIdent tree) {
//        if (tree.getName().contentEquals("ZROPTIONALCHAINING")) {
//            for (Symbol decl : env.info.scope.getSymbols(Scope.LookupKind.NON_RECURSIVE)) {
//                System.err.println("　　　symbol:" + decl.getSimpleName() + " " + decl.getClass().getName() + "  sym.owner:" + decl.owner + "  scope.owner:" + env.info.scope.owner);
//                env.info.scope.enterIfAbsent(tree.sym);
//            }
//        }
//        super.visitIdent(tree);
//        System.err.println("visitIdent " + tree);
//        for (Symbol decl : env.info.scope.getSymbols(Scope.LookupKind.NON_RECURSIVE)) {
//            System.err.println("　　　symbol:" + decl.getSimpleName() + " " + decl.getClass().getName() + "  sym.owner:" + decl.owner + "  scope.owner:" + env.info.scope.owner);
//        }
//        System.err.println("visitIdent : " + tree + "   " + tree.sym + "   " + tree.sym.kind);
//    }

    private JCTree.JCStatement changeOptionalChainingExpression2Call(JCTree.JCExpression allExpr, Env<AttrContext> env) {
        return changeOptionalChainingExpression2Call(allExpr, (trueExpression) -> {
            return make.Exec(trueExpression);
        }, env);
    }

    private JCTree.JCStatement changeOptionalChainingExpression2Call(JCTree.JCExpression allExpr, Function<JCTree.JCExpression, JCTree.JCStatement> action, Env<AttrContext> env) {
        return changeOptionalChainingExpression2Call(allExpr, action, null, env);
    }

    //转语句
    private JCTree.JCStatement changeOptionalChainingExpression2Call(JCTree.JCExpression allExpr, Function<JCTree.JCExpression, JCTree.JCStatement> action, JCTree.JCStatement elseAction, Env<AttrContext> env) {
        make.at(allExpr);
        final ArrayList<JCTree.JCVariableDecl> varDecls = new ArrayList<>();
        final JCTree.JCLiteral nullLiteral = make.Literal(TypeTag.BOT, null);
        final JCTree.JCExpression expression = changeOptionalChainingExpression2Expression(null, allExpr, null, allExpr, e -> e, nullLiteral, varDecls, true, false);
        if (expression instanceof JCTree.JCConditional) {
            List<JCTree.JCStatement> statements = List.nil();
            final JCTree.JCVariableDecl variableDecl = varDecls.get(0);
            if (variableDecl.getInitializer() instanceof JCTree.JCIdent) {
                //防止flow自动内联
                final Symbol.ClassSymbol biopClass = syms.getClass(syms.unnamedModule, names.fromString("zircon.BiOp"));
                final JCTree.JCFieldAccess wrap = make.Select(make.QualIdent(biopClass), names.fromString("wrap"));
                variableDecl.init = make.Apply(List.nil(), wrap, List.of(variableDecl.getInitializer()));
            }
//            variableDecl.accept(this);
//            visitVarDef(variableDecl);
//            env.info.scope.enter(variableDecl.sym);

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
            System.err.println("error changeOptionalChainingExpression2Call " + expression + "   type:" + expression.getClass().getName());
            return make.Exec(expression);
        }

    }

    private boolean findChainHasNullSafeFlag(JCTree.JCExpression expr, boolean found) {
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

    public static Object get(Object obj, String field) {
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);

        }
    }

}
