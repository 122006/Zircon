package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
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
        {
            final TypeEnter instance = TypeEnter.instance(context);
            if (get(instance, "attr") != null) {
                set(instance, "attr", zrAttr);
            }
        }
        {
            final ArgumentAttr instance = ArgumentAttr.instance(context);
            if (get(instance, "attr") != null) {
                set(instance, "attr", zrAttr);
            }
        }
        {
            final Resolve instance = Resolve.instance(context);
            if (get(instance, "attr") != null) {
                set(instance, "attr", zrAttr);
            }
        }
        {
            final DeferredAttr instance = DeferredAttr.instance(context);
            if (get(instance, "attr") != null) {
                set(instance, "attr", zrAttr);
            }
        }
        context.put(attrKey, zrAttr);

        return zrAttr;
    }


    @Override
    Type attribTree(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        if (tree instanceof JCTree.JCMethodInvocation) {
            return super.attribTree(tree, env, resultInfo);
        } else if (tree instanceof JCTree.JCMemberReference) {
            final JCTree.JCMemberReference memberReference = (JCTree.JCMemberReference) tree;
            final DeferredAttr.AttrMode oldDeferredAttrMode = resultInfo.checkContext.deferredAttrContext().mode;
            final JCTree.JCExpression qualifierExpression = memberReference.getQualifierExpression();
            final InferenceContext inferenceContext = super.resultInfo.checkContext.inferenceContext();
            try {
                return super.attribTree(memberReference, env, resultInfo);
            } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
                redirectMethod.printStackTrace();
                make.at(memberReference.getStartPosition());
                System.out.println("tree0:"+memberReference);
                final Symbol.MethodSymbol bestSoFar = (Symbol.MethodSymbol) redirectMethod.bestSoFar;
                System.out.println("tree:"+bestSoFar);
                final List<Attribute.Class> methodStaticExType = ZrResolve.getMethodStaticExType(names, (Symbol.MethodSymbol) bestSoFar);
                System.out.println("tree2:"+bestSoFar);
                if (methodStaticExType.isEmpty()) {
                    final JCTree.JCLambda lambda;
                    lambda = createLambdaTree(memberReference, bestSoFar);
                    lambda.pos = memberReference.pos;
                    final ResultInfo newResultInfo = new ResultInfo(Kinds.KindSelector.VAL, resultInfo.pt.hasTag(NONE) ? Type.recoveryType : resultInfo.pt, resultInfo.checkContext, CheckMode.NORMAL);
                    Env<AttrContext> fEnv = env.dup(lambda, env.info.dup());
                    Type type = super.attribTree(lambda, fEnv, newResultInfo);
                    lambda.type = type;
                    result = type;
                    if (true) {//todo
                        final RuntimeException runtimeException = new RuntimeException("搜索到被拓展的非静态方法引用：" + tree + "\n暂不支持该拓展形式,请替换为lambda表达式：\n" + lambda);
                        runtimeException.setStackTrace(new StackTraceElement[0]);
                        throw runtimeException;
                    }
                    return result;
                }

            }

        }
        return super.attribTree(tree, env, resultInfo);
    }

    private JCTree.JCLambda createLambdaTree(JCTree.JCMemberReference memberReference, Symbol.MethodSymbol bestSoFar) {
        final JCTree.JCLambda lambda;
        final TreeMaker maker = TreeMaker.instance(context);
        final Name nameA = names.fromString("$zr$a");
        Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA
                , bestSoFar.params.get(1).type, syms.noSymbol);
        final JCTree.JCIdent idA = maker.Ident(symA);
        final List<JCTree.JCExpression> of = List.of(memberReference.getQualifierExpression(), idA);
        final JCTree.JCFieldAccess add = maker.Select(maker.Ident(bestSoFar.owner), bestSoFar.name);
        final JCTree.JCMethodInvocation apply = maker.Apply(memberReference.typeargs, add, of);
        JCTree.JCVariableDecl a = make.VarDef(symA, null);
        lambda = maker.Lambda(List.of(a), apply);
        return lambda;
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
