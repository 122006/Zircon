package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

import static com.sun.tools.javac.code.TypeTag.NONE;

/**
 * @ClassName: ZrMethodReferenceLookupHelper
 * @Author: zwh
 * @Date: 2025/4/16 9:55
 * @Description:
 */
class ZrMethodReferenceLookupHelper extends Resolve.ReferenceLookupHelper {

    Resolve.MethodReferenceLookupHelper helper;
    Type oSite;
    ExMethodInfo info;
    ZrResolve zrResolve;


    ZrMethodReferenceLookupHelper(ZrResolve zrResolve, JCTree.JCMemberReference referenceTree, Name name, Type site, List<Type> argtypes, List<Type> typeargtypes, Resolve.MethodResolutionPhase maxPhase) {
        zrResolve.super(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        this.zrResolve = zrResolve;
        oSite = site;
        helper = zrResolve.new MethodReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    final Symbol lookup(Env<AttrContext> env, Resolve.MethodResolutionPhase phase) {
        final Symbol method = zrResolve.findMethod(env, site, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired());
        if (!TreeInfo.isStaticSelector(referenceTree.expr, zrResolve.names)) {
            for (ExMethodInfo methodInfo : zrResolve.findRedirectMethod(name, zrResolve.methodSymbolEnable(method))) {
                final List<Symbol.VarSymbol> nParams = methodInfo.methodSymbol.params();
                if (nParams.size() == 0) continue;
                if (!zrResolve.types.isCastable(site, nParams.get(0).type)) {
                    continue;
                }
                final JCTree.JCLambda lambdaTree = zrResolve.createLambdaTree(referenceTree, methodInfo);
                throw new NeedReplaceLambda(lambdaTree, referenceTree, methodInfo);
            }
            return method;
        } else {
            for (ExMethodInfo methodInfo : zrResolve.findRedirectMethod(name, zrResolve.methodSymbolEnable(method))) {
                if (!methodInfo.siteCopyByClassHeadArgMethod) continue;
                final List<Symbol.VarSymbol> nParams = methodInfo.methodSymbol.params();
                if (nParams.size() == 0) continue;
                if (!zrResolve.types.isCastable(site, methodInfo.targetClass.head)) {
                    continue;
                }
                final JCTree.JCLambda lambdaTree = zrResolve.createLambdaTree(referenceTree, methodInfo);
                throw new NeedReplaceLambda(lambdaTree, referenceTree, methodInfo);
            }
        }
        Pair<Symbol, ExMethodInfo> method2 = zrResolve.findMethod2(env, oSite, name, argtypes, typeargtypes, method, phase.isBoxingRequired(), phase.isVarargsRequired(), true);
        if (!zrResolve.methodSymbolEnable(method2.fst)) {
            info = null;
            return method;
        } else {
            info = method2.snd;
            return method2.fst;
        }
    }


    @Override
    Resolve.ReferenceLookupHelper unboundLookup(InferenceContext inferenceContext) {
        if (info != null && (TreeInfo.isStaticSelector(referenceTree.expr, zrResolve.names) && !info.siteCopyByClassHeadArgMethod)) {
            if (argtypes.nonEmpty() && (argtypes.head.hasTag(NONE) ||
                    zrResolve.types.isSubtypeUnchecked(inferenceContext.asUndetVar(argtypes.head), oSite))) {
                return this;
            }
        }
        return helper.unboundLookup(inferenceContext);
    }

    @Override
    JCTree.JCMemberReference.ReferenceKind referenceKind(Symbol sym) {
        return helper.referenceKind(sym);
    }
}
