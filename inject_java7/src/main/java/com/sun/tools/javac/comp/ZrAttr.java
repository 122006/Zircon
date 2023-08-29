package com.sun.tools.javac.comp;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ClassReader;
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
        System.out.println("ZrAttr instance");
        Attr res = context.get(attrKey);
        if (res instanceof ZrAttr) return (ZrAttr) res;
        System.out.println("res:"+res);
//        if (res != null) {
//            System.out.println();
//        }
        context.put(attrKey, (Attr) null);
        final ZrAttr zrAttr = new ZrAttr(context);
        context.put(attrKey, zrAttr);

        ReflectionUtil.setDeclaredField(MemberEnter.instance(context), MemberEnter.class, "attr", zrAttr);

        ReflectionUtil.setDeclaredField(JavacTrees.instance(context), JavacTrees.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(JavaCompiler.instance(context), JavaCompiler.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(DeferredAttr.instance(context), DeferredAttr.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(Resolve.instance(context), Resolve.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(TypeAnnotations.instance(context), TypeAnnotations.class, "attr", zrAttr);
        ReflectionUtil.setDeclaredField(Annotate.instance(context), Annotate.class, "attr", zrAttr);


        return zrAttr;
    }


    JCTree lastTree;



    @Override
    Type attribTree(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        try {
            if (tree instanceof JCTree.JCMethodInvocation) {
                return super.attribTree(tree, env, resultInfo);
            } else if (tree instanceof JCTree.JCMemberReference) {
                final JCTree.JCMemberReference memberReference = (JCTree.JCMemberReference) tree;
//                System.out.println("memberReference: class:" + memberReference.getClass().getName() + " sym:" + memberReference);
                if (resultInfo.pt != null) System.out.println("ptClass :" + resultInfo.pt.getClass());
                final DeferredAttr.AttrMode oldDeferredAttrMode = resultInfo.checkContext.deferredAttrContext().mode;
                final JCTree.JCExpression qualifierExpression = memberReference.getQualifierExpression();
                final Infer.InferenceContext inferenceContext = super.resultInfo.checkContext.inferenceContext();
                try {
                    return super.attribTree(memberReference, env, resultInfo);
                } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
                    redirectMethod.printStackTrace();
//                    System.out.println("inferenceVars=" + inferenceContext.inferenceVars());
                    make.at(memberReference.getStartPosition());
                    final Symbol.MethodSymbol bestSoFar = (Symbol.MethodSymbol) redirectMethod.bestSoFar;
//                    System.out.println("use lambda method :" + bestSoFar + " class:" + bestSoFar.getClass());
                    final List<Attribute.Class> methodStaticExType = ZrResolve.getMethodStaticExType(names, (Symbol.MethodSymbol) bestSoFar);
//                    System.out.println("use lambda method ex:" + methodStaticExType);
                    if (methodStaticExType.isEmpty()) {
                        final JCTree.JCLambda lambda;
                        lambda = createLambdaTree(memberReference, bestSoFar);
                        lambda.pos = memberReference.pos;
                        System.out.println("--------lambda=>" + lambda.toString());
                        System.out.println("--------next.tree==ignorexxxx   [" + env.next.tree.getClass());
                        System.out.println("new mode:" + oldDeferredAttrMode);
                        System.out.println("resultInfo.pt:" + resultInfo.pt + "[" + resultInfo.pt.getClass().getName());
                        System.out.println("resultInfo inferenceContext:" + resultInfo.checkContext.inferenceContext());
                        final ResultInfo newResultInfo = new ResultInfo(Kinds.VAL, resultInfo.pt.hasTag(NONE) ? Type.recoveryType : resultInfo.pt, resultInfo.checkContext);
                        Env<AttrContext> fEnv = env.dup(lambda, env.info.dup());
                        Type type = super.attribTree(lambda, fEnv, newResultInfo);
                        lambda.type = type;
                        result = type;
                        System.out.println("--------lambda type=" + type);
                        System.out.println("--------lambda TypeArguments=" + type.getTypeArguments());
                        System.out.println("--------lambda type.isErroneous()=" + type.isErroneous());
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
        } finally {
            lastTree = tree;
        }
    }

    private JCTree.JCLambda createLambdaTree(JCTree.JCMemberReference memberReference, Symbol.MethodSymbol bestSoFar) {
        final JCTree.JCLambda lambda;
        final TreeMaker maker = TreeMaker.instance(context);
        final Name nameA = names.fromString("$zr$a");
        Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA
                , bestSoFar.params.get(1).type, syms.noSymbol);
        System.out.println("VarSymbol:" + symA);
        final JCTree.JCIdent idA = maker.Ident(symA);
        final List<JCTree.JCExpression> of = List.of(memberReference.getQualifierExpression(), idA);
        System.out.println("memberReference.typeargs:" + memberReference.typeargs);
        final JCTree.JCFieldAccess add = maker.Select(maker.Ident(bestSoFar.owner), bestSoFar.name);
        final JCTree.JCMethodInvocation apply = maker.Apply(memberReference.typeargs, add, of);
//                        apply.setType(bestSoFar.getReturnType());
        System.out.println("JCMethodInvocation:" + apply);
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



}
