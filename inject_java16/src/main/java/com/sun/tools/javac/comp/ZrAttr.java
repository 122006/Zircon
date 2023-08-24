package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import java.lang.reflect.Field;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Flags.INTERFACE;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.Kinds.kindName;
import static com.sun.tools.javac.code.TypeTag.*;

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
    public void visitReference(JCTree.JCMemberReference that) {
        System.out.println("-ZrAttr visitReference " + that + " class:" + that.getClass());
        System.out.println("visitReference inferenceContext:" + resultInfo.checkContext.inferenceContext());
        super.visitReference(that);
    }

    @Override
    Type checkId(JCTree tree, Type site, Symbol sym, Env<AttrContext> env, ResultInfo resultInfo) {
        System.out.println("checkId: tree=" + tree);
        System.out.println("checkId: TypeArguments=" + resultInfo.pt.getTypeArguments());
        if (resultInfo.pt.getParameterTypes() != null) {
            for (Type parameterType : resultInfo.pt.getParameterTypes()) {
                if (parameterType instanceof DeferredAttr.DeferredType) {
                    final JCTree.JCExpression tree1 = ((DeferredAttr.DeferredType) parameterType).tree;
                    System.out.println("checkId: ParameterType=" + tree1 + "[" + parameterType);
                } else
                    System.out.println("checkId: ParameterType=" + parameterType + "[" + parameterType);

            }
        }
        return super.checkId(tree, site, sym, env, resultInfo);
    }

    JCTree lastTree;

    @Override
    Type attribTree(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        System.out.println("attribTree " + tree + "    class:" + tree.getClass().getName());
        System.out.println("attribTree pt:" + resultInfo.pt.getTypeArguments());
        if (super.resultInfo != null && super.resultInfo.checkContext != null) {
            final InferenceContext inferenceContext = super.resultInfo.checkContext.inferenceContext();
            System.out.println("=====inferenceVars=" + inferenceContext.inferenceVars());
            System.out.println("=====inferenceVars2=" + resultInfo.checkContext.inferenceContext().inferenceVars());
        }
        try {
            if (tree instanceof JCTree.JCMethodInvocation) {
                System.out.println("---ZrAttr-attribTree = " + tree.toString());
                final JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation) tree;
                final JCTree.JCExpression meth = invocation.meth;
                System.out.println("----attribArg = " + invocation.getTypeArguments().toString());
                if (resultInfo.pt instanceof Type.ForAll) {
                    final List<Type> typeArguments = ((Type.ForAll) resultInfo.pt).getTypeArguments();
                    System.out.println("----typeArguments ForAll = " + typeArguments.toString());
                } else {
                    System.out.println("----typeArguments = " + resultInfo.pt.getReturnType());
                }
                return super.attribTree(tree, env, resultInfo);
            } else if (tree instanceof JCTree.JCMemberReference) {
                final JCTree.JCMemberReference memberReference = (JCTree.JCMemberReference) tree;
                System.out.println("memberReference: class:" + memberReference.getClass().getName() + " sym:" + memberReference);
                System.out.println("pt :" + resultInfo.pt);
                if (resultInfo.pt != null) System.out.println("ptClass :" + resultInfo.pt.getClass());
                final DeferredAttr.AttrMode oldDeferredAttrMode = resultInfo.checkContext.deferredAttrContext().mode;
                System.out.println("old mode:" + oldDeferredAttrMode);
//                Resolve.MethodResolutionContext prevContext = rs.currentResolutionContext;
                final JCTree.JCExpression qualifierExpression = memberReference.getQualifierExpression();
                final InferenceContext inferenceContext = super.resultInfo.checkContext.inferenceContext();
                System.out.println("inferenceVars=" + inferenceContext.inferenceVars());
                try {
                    return super.attribTree(memberReference, env, resultInfo);
                } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
                    redirectMethod.printStackTrace();
                    System.out.println("inferenceVars=" + inferenceContext.inferenceVars());
//                    rs.currentResolutionContext = prevContext;
//                    rs.attrRecover.doRecovery();
                    make.at(memberReference.getStartPosition());
                    final Symbol.MethodSymbol bestSoFar = (Symbol.MethodSymbol) redirectMethod.bestSoFar;
                    System.out.println("use lambda method :" + bestSoFar + " class:" + bestSoFar.getClass());
                    final List<Attribute.Class> methodStaticExType = ZrResolve.getMethodStaticExType(names, (Symbol.MethodSymbol) bestSoFar);
                    System.out.println("use lambda method ex:" + methodStaticExType);
                    if (methodStaticExType.isEmpty()) {
                        final JCTree.JCLambda lambda;
                        lambda = createLambdaTree(memberReference, bestSoFar);
//                        lambda.target = bestSoFar.getReturnType();
//                        lambda.type = super.resultInfo.pt;
                        lambda.pos = memberReference.pos;
                        System.out.println("--------lambda=>" + lambda.toString());
//                        env.tree = lambda;
//                        super.resultInfo = super.resultInfo.dup(bestSoFar.getReturnType());
//                        super.resultInfo = resultInfo.dup(resultInfo.pt, new FunctionalReturnContext(resultInfo.checkContext), CheckMode.NORMAL);
                        System.out.println("--------next.tree==ignorexxxx   [" + env.next.tree.getClass());
                        System.out.println("new mode:" + oldDeferredAttrMode);
                        System.out.println("resultInfo.pt:" + resultInfo.pt + "[" + resultInfo.pt.getClass().getName());
                        System.out.println("resultInfo inferenceContext:" + resultInfo.checkContext.inferenceContext());
                        final ResultInfo newResultInfo = new ResultInfo(Kinds.KindSelector.VAL, resultInfo.pt.hasTag(NONE) ? Type.recoveryType : resultInfo.pt, resultInfo.checkContext, CheckMode.NORMAL);
                        Env<AttrContext> fEnv = env.dup(lambda, env.info.dup());
                        Type type = super.attribTree(lambda, fEnv, newResultInfo);
//                        type = fEnv.info.returnResult.checkContext.inferenceContext().asUndetVar(type);
//                        super.resultInfo = resultInfo.dup(type);
//                        final Type check = check(lambda, type, Kinds.KindSelector.VAL, newResultInfo);
                        lambda.type = type;
                        result = type;
//                        result = checkId(lambda, bestSoFar.owner.type, bestSoFar.type.tsym, fEnv, newResultInfo);
                        System.out.println("--------lambda type=" + type);
                        System.out.println("--------lambda TypeArguments=" + type.getTypeArguments());
                        System.out.println("--------lambda type.isErroneous()=" + type.isErroneous());
                        if (true) {
                            final RuntimeException runtimeException = new RuntimeException("搜索到被拓展的非静态方法引用："+tree+"\n暂不支持该拓展形式,请替换为lambda表达式：\n" + lambda);
                            runtimeException.setStackTrace(new StackTraceElement[0]);
                            throw runtimeException;
                        }
                        return result;
                    }
                    //                } else {
                    ////                    final Name nameA = names.fromString("a");
                    ////                    Symbol.VarSymbol symA = new Symbol.VarSymbol(PARAMETER, nameA
                    ////                            , methodStaticExType.head.type, syms.noSymbol);
                    ////                    System.out.println("VarSymbol:" + symA);
                    ////                    final JCTree.JCIdent idA = maker.Ident(symA);
                    ////                    final JCTree.JCMethodInvocation apply = maker.App(add, List.of(idA));
                    ////                    System.out.println("JCMethodInvocation:" + apply);
                    ////                    JCTree.JCVariableDecl a = make.VarDef(symA, null);
                    ////                    final JCTree.JCLambda lambda = maker.Reference(List.of(a), apply);
                    ////                    lambda.target = memberReference.target;
                    ////                    lambda.type = memberReference.type;
                    ////                    lambda.pos = memberReference.pos;
                    ////                    System.out.println("--------lambda=>" + lambda.toString());
                    //////                    super.resultInfo = super.resultInfo.dup(bestSoFar.getReturnType());
                    ////                    final Type type = super.attribTree(lambda, env, resultInfo);
                    ////                    System.out.println("--------lambda type=" + result);
                    //                    final JCTree.JCMemberReference reference = maker.Reference(memberReference.mode, bestSoFar.name, maker.Ident(methodStaticExType.head.getValue().tsym), null);
                    //                    System.out.println("--------lambda=>" + reference.toString());
                    //                    final Type type = super.attribTree(reference, env, resultInfo);
                    //                    System.out.println("--------lambda type=" + result);
                    //                    return type;
                    //                }

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

    //    @Override
    public void visitLambda2(final JCTree.JCLambda that) {
        System.out.println("===============================");
        System.out.println("visitLambda: " + that);
        System.out.println("visitLambda: type=" + that.type);
        System.out.println("visitLambda: isErroneous=" + pt().isErroneous());
        System.out.println("visitLambda: pt=" + pt());
        System.out.println("visitLambda: env.info.enclVar=" + env.info.enclVar);
        System.out.println("visitLambda: env.info.enclVar.type.isErroneous()=" + env.info.enclVar.type.isErroneous());
        boolean wrongContext = false;
        if (pt().isErroneous() || (pt().hasTag(NONE) && pt() != Type.recoveryType)) {
            if (pt().hasTag(NONE) && (env.info.enclVar == null || !env.info.enclVar.type.isErroneous())) {
                //lambda only allowed in assignment or method invocation/cast context
                log.error(that.pos(), CompilerProperties.Errors.UnexpectedLambda);
            }
            resultInfo = recoveryInfo;
            wrongContext = true;
        }
        //create an environment for attribution of the lambda expression
        final Env<AttrContext> localEnv = lambdaEnv(that, env);
        boolean needsRecovery =
                resultInfo.checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.CHECK;
        try {
            if (needsRecovery && isSerializable(pt())) {
                localEnv.info.isSerializable = true;
                localEnv.info.isSerializableLambda = true;
            }
            List<Type> explicitParamTypes = null;
            if (that.paramKind == JCTree.JCLambda.ParameterKind.EXPLICIT) {
                //attribute lambda parameters
                attribStats(that.params, localEnv);
                explicitParamTypes = TreeInfo.types(that.params);
            }

            System.out.println("visitLambda: resultInfo=" + resultInfo);
            System.out.println("visitLambda: explicitParamTypes=" + explicitParamTypes);
            TargetInfo targetInfo = getTargetInfo(that, resultInfo, explicitParamTypes);

            Type currentTarget = targetInfo.target;
            Type lambdaType = targetInfo.descriptor;
            System.out.println("visitLambda: currentTarget=" + currentTarget);
            System.out.println("visitLambda: .isErroneous()=" + currentTarget.isErroneous());
            System.out.println("visitLambda: lambdaType=" + lambdaType);
            System.out.println("visitLambda: lambdaType.getReturnType()=" + lambdaType.getReturnType());

            if (currentTarget.isErroneous()) {
                result = that.type = currentTarget;
                return;
            }

            setFunctionalInfo(localEnv, that, pt(), lambdaType, currentTarget, resultInfo.checkContext);

            if (lambdaType.hasTag(FORALL)) {
                System.out.println("visitLambda: hasTag(FORALL)=" + lambdaType);

                //lambda expression target desc cannot be a generic method
                JCDiagnostic.Fragment msg = CompilerProperties.Fragments.InvalidGenericLambdaTarget(lambdaType,
                        kindName(currentTarget.tsym),
                        currentTarget.tsym);
                resultInfo.checkContext.report(that, diags.fragment(msg));
                result = that.type = types.createErrorType(pt());
                return;
            }

            if (that.paramKind == JCTree.JCLambda.ParameterKind.IMPLICIT) {
                System.out.println("visitLambda: paramKind=implicit");

                //add param type info in the AST
                List<Type> actuals = lambdaType.getParameterTypes();
                List<JCTree.JCVariableDecl> params = that.params;

                boolean arityMismatch = false;

                while (params.nonEmpty()) {
                    if (actuals.isEmpty()) {
                        //not enough actuals to perform lambda parameter inference
                        arityMismatch = true;
                    }
                    //reset previously set info
                    Type argType = arityMismatch ?
                            syms.errType :
                            actuals.head;
                    if (params.head.isImplicitlyTyped()) {
//                        super.setSyntheticVariableType(params.head, argType);
                    }
                    params.head.sym = null;
                    actuals = actuals.isEmpty() ?
                            actuals :
                            actuals.tail;
                    params = params.tail;
                }

                //attribute lambda parameters
                attribStats(that.params, localEnv);

                if (arityMismatch) {
                    System.out.println("visitLambda: arityMismatch=" + arityMismatch);
                    resultInfo.checkContext.report(that, diags.fragment(CompilerProperties.Fragments.IncompatibleArgTypesInLambda));
                    result = that.type = types.createErrorType(currentTarget);
                    return;
                }
            }

            //from this point on, no recovery is needed; if we are in assignment context
            //we will be able to attribute the whole lambda body, regardless of errors;
            //if we are in a 'check' method context, and the lambda is not compatible
            //with the target-type, it will be recovered anyway in Attr.checkId
            needsRecovery = false;

            ResultInfo bodyResultInfo = localEnv.info.returnResult =
                    lambdaBodyResult(that, lambdaType, resultInfo);

            if (that.getBodyKind() == JCTree.JCLambda.BodyKind.EXPRESSION) {
                System.out.println("visitLambda: getBodyKind=EXPRESSION");
                attribTree(that.getBody(), localEnv, bodyResultInfo);
            } else {
                JCTree.JCBlock body = (JCTree.JCBlock) that.body;
                if (resultInfo.checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.CHECK) {
                    breakTreeFound(copyEnv(localEnv));
                }
                attribStats(body.stats, localEnv);
            }
            System.out.println("visitLambda: resultInfo=" + resultInfo);

            result = check(that, currentTarget, Kinds.KindSelector.VAL, resultInfo);

            System.out.println("visitLambda: result=" + result);
            boolean isSpeculativeRound =
                    resultInfo.checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.SPECULATIVE;

            preFlow(that);
            flow.analyzeLambda(env, that, make, isSpeculativeRound);

            that.type = currentTarget; //avoids recovery at this stage
            checkLambdaCompatible(that, lambdaType, resultInfo.checkContext);

            if (!isSpeculativeRound) {
                //add thrown types as bounds to the thrown types free variables if needed:
                if (resultInfo.checkContext.inferenceContext().free(lambdaType.getThrownTypes())) {
                    List<Type> inferredThrownTypes = flow.analyzeLambdaThrownTypes(env, that, make);
                    if (!checkExConstraints(inferredThrownTypes, lambdaType.getThrownTypes(), resultInfo.checkContext.inferenceContext())) {
                        log.error(that, CompilerProperties.Errors.IncompatibleThrownTypesInMref(lambdaType.getThrownTypes()));
                    }
                }

//                super.checkAccessibleTypes(that, localEnv, resultInfo.checkContext.inferenceContext(), lambdaType, currentTarget);
            }
            result = wrongContext ? that.type = types.createErrorType(pt())
                    : check(that, currentTarget, Kinds.KindSelector.VAL, resultInfo);
            System.out.println("visitLambda: result2=" + result);

        } catch (Types.FunctionDescriptorLookupError ex) {
            ex.printStackTrace();
            JCDiagnostic cause = ex.getDiagnostic();
            resultInfo.checkContext.report(that, cause);
            result = that.type = types.createErrorType(pt());
            return;
        } catch (Symbol.CompletionFailure cf) {
            cf.printStackTrace();
            chk.completionError(that.pos(), cf);
        } catch (Throwable t) {
            t.printStackTrace();
            //when an unexpected exception happens, avoid attempts to attribute the same tree again
            //as that would likely cause the same exception again.
            needsRecovery = false;
            throw t;
        } finally {
            localEnv.info.scope.leave();
            if (needsRecovery) {
                Type prevResult = result;
                try {
                    attribTree(that, env, recoveryInfo);
                } finally {
                    if (result == Type.recoveryType) {
                        result = prevResult;
                    }
                }
            }
        }
    }

    /**
     * Check kind and type of given tree against protokind and prototype.
     * If check succeeds, store type in tree and return it.
     * If check fails, store errType in tree and return it.
     * No checks are performed if the prototype is a method type.
     * It is not necessary in this case since we know that kind and type
     * are correct.
     *
     * @param tree       The tree whose kind and type is checked
     * @param found      The computed type of the tree
     * @param ownkind    The computed kind of the tree
     * @param resultInfo The expected result of the tree
     */
    Type check2(final JCTree tree,
                final Type found,
                final Kinds.KindSelector ownkind,
                final ResultInfo resultInfo) {
        InferenceContext inferenceContext = resultInfo.checkContext.inferenceContext();
        Type owntype;
        boolean shouldCheck = !found.hasTag(ERROR) &&
                !resultInfo.pt.hasTag(METHOD) &&
                !resultInfo.pt.hasTag(FORALL);

        System.out.println("==============");
        System.out.println("check: tree=" + tree);
        System.out.println("check: found=" + found);
        System.out.println("check: shouldCheck=" + shouldCheck);
        System.out.println("check: ownkind=" + ownkind.kindNames());
        System.out.println("check: resultInfo.pkind=" + resultInfo.pkind.kindNames());
        System.out.println("check: ownkind.subset(resultInfo.pkind)=" + ownkind.subset(resultInfo.pkind));
        System.out.println("check: inferenceVars=" + inferenceContext.inferenceVars());
        System.out.println("check: inferenceContext.free(found)=" + inferenceContext.free(found));
        System.out.println("check: allowPoly=" + allowPoly);
        if (shouldCheck && !ownkind.subset(resultInfo.pkind)) {
            log.error(tree.pos(),
                    CompilerProperties.Errors.UnexpectedType(resultInfo.pkind.kindNames(),
                            ownkind.kindNames()));
            owntype = types.createErrorType(found);
        } else if (allowPoly && inferenceContext.free(found)) {
            //delay the check if there are inference variables in the found type
            //this means we are dealing with a partially inferred poly expression
            owntype = shouldCheck ? resultInfo.pt : found;
            if (resultInfo.checkMode.installPostInferenceHook()) {
                inferenceContext.addFreeTypeListener(List.of(found),
                        instantiatedContext -> {
                            ResultInfo pendingResult =
                                    resultInfo.dup(inferenceContext.asInstType(resultInfo.pt));
                            check(tree, inferenceContext.asInstType(found), ownkind, pendingResult);
                        });
            }
        } else {
            owntype = shouldCheck ?
                    resultInfo.check(tree, found) :
                    found;

        }
        System.out.println("check: owntype=" + owntype);
        if (resultInfo.checkMode.updateTreeType()) {
            tree.type = owntype;
        }
        System.out.println("check: tree.type=" + tree.type);
        return owntype;
    }

    @Override
    void checkLambdaCompatible(JCTree.JCLambda tree, Type descriptor, Check.CheckContext checkContext) {
        System.out.println("checkLambdaCompatible tree:" + tree);
        System.out.println("checkLambdaCompatible checkContext:" + checkContext.inferenceContext());
        Type returnType = checkContext.inferenceContext().asUndetVar(descriptor.getReturnType());
        System.out.println("checkLambdaCompatible returnType:" + returnType);
        System.out.println("checkLambdaCompatible tree.canCompleteNormally:" + tree.canCompleteNormally);

        if (tree.getBodyKind() == JCTree.JCLambda.BodyKind.STATEMENT && tree.canCompleteNormally &&
                !returnType.hasTag(VOID) && returnType != Type.recoveryType) {
            System.out.println("checkLambdaCompatible error");
        }
        super.checkLambdaCompatible(tree, descriptor, checkContext);
    }

    @Override
    void checkReferenceCompatible(JCTree.JCMemberReference tree, Type descriptor, Type refType, Check.CheckContext checkContext, boolean speculativeAttr) {
        System.out.println("checkReferenceCompatible checkContext:" + checkContext.inferenceContext());
        super.checkReferenceCompatible(tree, descriptor, refType, checkContext, speculativeAttr);
    }

    private void setFunctionalInfo(final Env<AttrContext> env, final JCTree.JCFunctionalExpression fExpr,
                                   final Type pt, final Type descriptorType, final Type primaryTarget, final Check.CheckContext checkContext) {
        if (checkContext.inferenceContext().free(descriptorType)) {
            checkContext.inferenceContext().addFreeTypeListener(List.of(pt, descriptorType),
                    inferenceContext -> setFunctionalInfo(env, fExpr, pt, inferenceContext.asInstType(descriptorType),
                            inferenceContext.asInstType(primaryTarget), checkContext));
        } else {
            if (pt.hasTag(CLASS)) {
                fExpr.target = primaryTarget;
            }
            if (checkContext.deferredAttrContext().mode == DeferredAttr.AttrMode.CHECK &&
                    pt != Type.recoveryType) {
                //check that functional interface class is well-formed
                try {
                    /* Types.makeFunctionalInterfaceClass() may throw an exception
                     * when it's executed post-inference. See the listener code
                     * above.
                     */
                    Symbol.ClassSymbol csym = types.makeFunctionalInterfaceClass(env,
                            names.empty, fExpr.target, ABSTRACT);
                    if (csym != null) {
                        chk.checkImplementations(env.tree, csym, csym);
                        try {
                            //perform an additional functional interface check on the synthetic class,
                            //as there may be spurious errors for raw targets - because of existing issues
                            //with membership and inheritance (see JDK-8074570).
                            csym.flags_field |= INTERFACE;
                            types.findDescriptorType(csym.type);
                        } catch (Types.FunctionDescriptorLookupError err) {
                            resultInfo.checkContext.report(fExpr,
                                    diags.fragment(CompilerProperties.Fragments.NoSuitableFunctionalIntfInst(fExpr.target)));
                        }
                    }
                } catch (Types.FunctionDescriptorLookupError ex) {
                    JCDiagnostic cause = ex.getDiagnostic();
                    resultInfo.checkContext.report(env.tree, cause);
                }
            }
        }
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation that) {
        try {
            super.visitApply(that);
        } catch (ZrResolve.NeedRedirectMethod redirectMethod) {
            final Symbol bestSoFar = redirectMethod.bestSoFar;
            final TreeMaker maker = TreeMaker.instance(context);
            System.out.println("use method :" + bestSoFar);
            final JCTree.JCFieldAccess add = maker.Select(maker.Ident(bestSoFar.owner), bestSoFar.name);
            final List<Attribute.Class> methodStaticExType = ZrResolve.getMethodStaticExType(names, (Symbol.MethodSymbol) bestSoFar);
            System.out.println("use method ez:" + methodStaticExType);
            that.args = methodStaticExType.isEmpty() ? that.args.prepend(((JCTree.JCFieldAccess) that.meth).selected) : that.args;
            that.meth = add;
            System.out.println("--------=>" + that.toString());
            super.visitApply(that);
            System.out.println("--------type=" + result);
        }
    }


    @Override
    public Type attribExpr(JCTree tree, Env<AttrContext> env) {
        if (tree instanceof JCTree.JCMethodInvocation) {
            JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation) tree;
            System.out.println("meth=" + invocation.meth.toString());
            System.out.println("tree=" + tree.toString());
            System.out.println("invocation=" + invocation.getTree().toString());
            System.out.println("invocation.sym=" + TreeInfo.symbol(invocation.getTree()));
            System.out.println("invocation.sym2=" + TreeInfo.symbol(invocation.getTree()));
            final Type type = super.attribExpr(tree, env);
            return type;
        } else if (tree instanceof JCTree.JCFieldAccess) {
            JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) tree;
            System.out.println("meth=" + fieldAccess.sym);
            System.out.println("tree=" + tree);
            System.out.println("invocation=" + fieldAccess.getTree().toString());
            System.out.println("invocation.sym=" + TreeInfo.symbol(fieldAccess.getTree()));
            final Type type = super.attribExpr(tree, env);
            System.out.println("invocation.sym2=" + TreeInfo.symbol(fieldAccess.getTree()));
            return type;
        }
        return super.attribExpr(tree, env);

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

    @Override
    public void attribClass(JCDiagnostic.DiagnosticPosition pos, Symbol.ClassSymbol c) {
        System.out.println("开始解析符号：" + c.toString());
        super.attribClass(pos, c);
        System.out.println("完成解析符号：" + c.toString());
    }
}
