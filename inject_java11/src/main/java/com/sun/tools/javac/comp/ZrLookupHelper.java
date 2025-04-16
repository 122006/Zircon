package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

/**
 * @ClassName: ZrLookupHelper
 * @Author: zwh
 * @Date: 2025/4/16 9:58
 * @Description:
 */
class ZrLookupHelper extends Resolve.BasicLookupHelper {

    ZrResolve zrResolve;

    ZrLookupHelper(ZrResolve zrResolve, Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
        zrResolve.super(name, site, argtypes, typeargtypes);
        this.zrResolve = zrResolve;
    }

    @Override
    Symbol doLookup(Env<AttrContext> env, Resolve.MethodResolutionPhase phase) {
        final Symbol bestSoFar = zrResolve.findMethod(env, site, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired());
        final Pair<Symbol, ExMethodInfo> method2 = zrResolve.findMethod2(env, site, name, argtypes, typeargtypes, bestSoFar, phase.isBoxingRequired(), phase.isVarargsRequired(), false);
        final Symbol newSymbol = method2.fst;
        if (method2.snd != null && (newSymbol instanceof Symbol.MethodSymbol && !(bestSoFar instanceof Symbol.MethodSymbol)) || ((newSymbol instanceof Symbol.MethodSymbol) && (bestSoFar instanceof Symbol.MethodSymbol) && newSymbol != bestSoFar)) {
            throw new NeedRedirectMethod(method2.fst, method2.snd);
        } else {
            return newSymbol;
        }
    }

}
