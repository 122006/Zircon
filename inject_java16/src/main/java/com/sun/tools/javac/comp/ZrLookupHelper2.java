package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

/**
 * @ClassName: ZrLookupHelper2
 * @Author: 122006
 * @Date: 2025/4/16 9:53
 * @Description:
 */
class ZrLookupHelper2 extends Resolve.BasicLookupHelper {

    private final ZrResolve zrResolve;

    ZrLookupHelper2(ZrResolve zrResolve, Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
        zrResolve.super(name, site, argtypes, typeargtypes);
        this.zrResolve = zrResolve;
    }

    @Override
    Symbol doLookup(Env<AttrContext> env, Resolve.MethodResolutionPhase phase) {
        final Symbol bestSoFar = zrResolve.findFun(env, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired());
        final Pair<Symbol, ExMethodInfo> pair = zrResolve.findMethod2(env, site, name, argtypes, typeargtypes, bestSoFar, phase.isBoxingRequired(), phase.isVarargsRequired(), false);
        final Symbol method2 = pair.fst;
        if (pair.snd != null && method2 != bestSoFar && zrResolve.methodSymbolEnable(method2)) {
            throw new NeedRedirectMethod(pair.fst, pair.snd, site);
        }
        return bestSoFar;
    }

}
