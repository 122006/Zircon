package com.sun.tools.javac.comp;

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.parser.ZrUnSupportCodeError;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.*;

import java.util.Objects;

import static com.sun.tools.javac.code.TypeTag.VOID;

public class ZrAttr extends Attr {
    private final Context context;
    ZrAttrEx zrAttrEx;

    protected ZrAttr(Context context) {
        super(context);
        this.context = context;
        this.zrAttrEx = new ZrAttrEx(this);
    }

    public static ZrAttr instance(Context context) {
        Attr res = context.get(attrKey);
        if (res instanceof ZrAttr) return (ZrAttr) res;
        context.put(attrKey, (Attr) null);
        final ZrAttr zrAttr = new ZrAttr(context);
        ReflectionUtil.setDeclaredField(MemberEnter.instance(context), MemberEnter.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(JavacTrees.instance(context), JavacTrees.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(JavaCompiler.instance(context), JavaCompiler.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(DeferredAttr.instance(context), DeferredAttr.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(Resolve.instance(context), Resolve.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(TypeAnnotations.instance(context), TypeAnnotations.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(Annotate.instance(context), Annotate.class, "attr", zrAttr);
        return zrAttr;
    }

    @Override
    <T extends JCTree> void attribStats(List<T> trees, Env<AttrContext> env) {

        final Pair<Boolean, List<JCTree>> booleanListPair = zrAttrEx.treeTranslator(trees, env);
        if (booleanListPair.fst) {
            if (env.tree instanceof JCTree.JCBlock) {
//                argumentAttr.argumentTypeCache.clear();
                ((JCTree.JCBlock) env.tree).stats = zrAttrEx.listMap(booleanListPair.snd, a -> (JCTree.JCStatement) a);
                super.attribStats(((JCTree.JCBlock) env.tree).stats, env);

                return;
            }
        }
        super.attribStats(trees, env);
    }

    public void visitVarDef(JCTree.JCVariableDecl that) {
        try {
            super.visitVarDef(that);
        } catch (NeedReplaceLambda needReplaceLambda) {
            JCTree.JCExpression initializer = TreeInfo.skipParens(that.getInitializer());
            if (Objects.equals(initializer.getStartPosition(), needReplaceLambda.memberReference.getStartPosition())) {
                that.init = needReplaceLambda.bestSoFar;
            }
            super.visitVarDef(that);
        }
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        Name methName = TreeInfo.name(that.meth);
        boolean isConstructorCall = methName == this.names._this || methName == this.names._super;
        if (isConstructorCall) {
            super.visitApply(that);
        } else {
            visitNoConstructorApply(that);
        }
    }


    @Override
    Type attribTree(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {

        if (tree instanceof JCTree.JCVariableDecl) {
            ((JCTree.JCVariableDecl) tree).init = zrAttrEx.treeTranslator(((JCTree.JCVariableDecl) tree).init);
        } else if (tree instanceof JCTree.JCBinary) {
            ((JCTree.JCBinary) tree).lhs = zrAttrEx.treeTranslator(((JCTree.JCBinary) tree).lhs);
            ((JCTree.JCBinary) tree).rhs = zrAttrEx.treeTranslator(((JCTree.JCBinary) tree).rhs);
        } else if (tree instanceof JCTree.JCParens) {
            ((JCTree.JCParens) tree).expr = zrAttrEx.treeTranslator(((JCTree.JCParens) tree).expr);
        } else if (tree instanceof JCTree.JCIf) {
            ((JCTree.JCIf) tree).cond = zrAttrEx.treeTranslator(((JCTree.JCIf) tree).cond);
        } else if (tree instanceof JCTree.JCMethodInvocation) {
            ((JCTree.JCMethodInvocation) tree).args = zrAttrEx.listMap(((JCTree.JCMethodInvocation) tree).args, a -> zrAttrEx.treeTranslator(a));
            return super.attribTree(tree, env, resultInfo);
        } else if (tree instanceof JCTree.JCMemberReference) {
            ((JCTree.JCMemberReference) tree).expr = zrAttrEx.treeTranslator(((JCTree.JCMemberReference) tree).expr);
        } else if (tree instanceof JCTree.JCSwitch) {
            ((JCTree.JCSwitch) tree).selector = zrAttrEx.treeTranslator(((JCTree.JCSwitch) tree).selector);
        } else if (tree instanceof JCTree.JCDoWhileLoop) {
            ((JCTree.JCDoWhileLoop) tree).cond = zrAttrEx.treeTranslator(((JCTree.JCDoWhileLoop) tree).cond);
        } else if (tree instanceof JCTree.JCConditional) {
            ((JCTree.JCConditional) tree).cond = zrAttrEx.treeTranslator(((JCTree.JCConditional) tree).cond);
            ((JCTree.JCConditional) tree).truepart = zrAttrEx.treeTranslator(((JCTree.JCConditional) tree).truepart);
            ((JCTree.JCConditional) tree).falsepart = zrAttrEx.treeTranslator(((JCTree.JCConditional) tree).falsepart);
        } else if (tree instanceof JCTree.JCAssert) {
            ((JCTree.JCAssert) tree).cond = zrAttrEx.treeTranslator(((JCTree.JCAssert) tree).cond);
        } else if (tree instanceof JCTree.JCArrayAccess) {
            ((JCTree.JCArrayAccess) tree).index = zrAttrEx.treeTranslator(((JCTree.JCArrayAccess) tree).index);
            ((JCTree.JCArrayAccess) tree).indexed = zrAttrEx.treeTranslator(((JCTree.JCArrayAccess) tree).indexed);
        } else if (tree instanceof JCTree.JCReturn) {

            final JCTree.JCExpression expr = zrAttrEx.treeTranslator(((JCTree.JCReturn) tree).expr);

            ((JCTree.JCReturn) tree).expr = expr;
        } else if (tree instanceof JCTree.JCLambda) {
            JCTree body = ((JCTree.JCLambda) tree).body;
            final LambdaExpressionTree.BodyKind bodyKind = ((JCTree.JCLambda) tree).getBodyKind();


            if (bodyKind == LambdaExpressionTree.BodyKind.STATEMENT) {
                final Pair<Boolean, List<JCTree>> pair = zrAttrEx.treeTranslator(((JCTree.JCBlock) body).stats, env);
                if (pair.fst) {
                    ((JCTree.JCLambda) tree).body = make.at(tree.pos).Block(0, zrAttrEx.listMap(pair.snd, a -> (JCTree.JCStatement) a));

                }
            } else if (bodyKind == LambdaExpressionTree.BodyKind.EXPRESSION) {
                final JCTree.JCExpression expression = (JCTree.JCExpression) body;
                if (zrAttrEx.findChainHasNullSafeFlag(expression, false)) {
                    boolean returnVoid = false;
                    if (expression instanceof JCTree.JCFieldAccess) {
                        returnVoid = false;
                    } else if (expression instanceof JCTree.JCMethodInvocation) {
                        final JCTree.JCExpression jcExpression = zrAttrEx.cleanExprNullSafeFlag(expression);
//                            jcExpression.accept(this);
                        final Type type = zrAttrEx.getType(env, jcExpression);


                        returnVoid = type == syms.voidType;
                    }
                    if (returnVoid) {
                        JCTree.JCStatement expressionStatement = make.at(tree.pos).Exec(expression);
                        final Pair<Boolean, List<JCTree>> pair = zrAttrEx.treeTranslator(List.of(expressionStatement), env);
                        if (pair.fst) {
                            ((JCTree.JCLambda) tree).body = make.at(tree.pos).Block(0, zrAttrEx.listMap(pair.snd, a -> (JCTree.JCStatement) a));
                        }
                    } else {
                        ((JCTree.JCLambda) tree).body = zrAttrEx.treeTranslator(expression);
                    }
                } else {
                    final JCTree.JCExpression jcExpression = zrAttrEx.treeTranslatorExpressionWithReturnType(expression);
                    if (jcExpression != expression) {
                        ((JCTree.JCLambda) tree).body = jcExpression;
                    }
                }
            }

        }

        return super.attribTree(tree, env, resultInfo);
    }


    @Override
    public void visitLambda(JCTree.JCLambda that) {
        super.visitLambda(that);
    }

    @Override
    public void visitTypeTest(JCTree.JCInstanceOf tree) {
        super.visitTypeTest(tree);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void visitNoConstructorApply(JCTree.JCMethodInvocation that) {
        Env<AttrContext> localEnv = this.env.dup(that, ((AttrContext) this.env.info).dup());
        Name methName = TreeInfo.name(that.meth);
        ListBuffer<Type> argtypesBuf = new ListBuffer();
        int kind;
        try {
            kind = this.attribArgs(Kinds.VAL, that.args, localEnv, argtypesBuf);
        } catch (NeedReplaceLambda needReplaceLambda) {
            List<JCTree.JCExpression> newList = List.nil();
            for (int i = 0; i < that.args.size(); i++) {
                JCTree.JCExpression argument = TreeInfo.skipParens(that.args.get(i));
                if (Objects.equals(argument.getStartPosition(), needReplaceLambda.memberReference.getStartPosition())) {
                    newList = newList.append(needReplaceLambda.bestSoFar);
                } else {
                    newList = newList.append(argument);
                }
            }
            that.args = newList;
            kind = this.attribArgs(Kinds.VAL, that.args, localEnv, argtypesBuf);
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
                            final Symbol.ClassSymbol biopClass = syms.classes.get(names.fromString("zircon.BiOp"));
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
            encl = this.attribTree(that.meth, localEnv, new ResultInfo(kind, methodTemplate, this.resultInfo.checkContext));
        }

        Type restype = encl.getReturnType();
        if (restype.hasTag(TypeTag.WILDCARD)) {
            throw new AssertionError(encl);
        }
        Type qualifier = that.meth.hasTag(JCTree.Tag.SELECT) ? ((JCTree.JCFieldAccess) that.meth).selected.type : this.env.enclClass.sym.type;
        restype = this.adjustMethodReturnType(qualifier, methName, argtypes, restype);
        this.chk.checkRefTypes(that.typeargs, typeargtypes);
        Type capturedRes = this.resultInfo.checkContext.inferenceContext().cachedCapture(that, restype, true);
        this.result = this.check(that, capturedRes, Kinds.VAL, this.resultInfo);
        this.chk.validate(that.typeargs, localEnv);

    }


}
