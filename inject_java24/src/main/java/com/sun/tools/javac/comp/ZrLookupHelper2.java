package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

/** Unqualified calls use lookup directly on JDK 24+, including ordinary Java calls. */
class ZrLookupHelper2 extends Resolve.BasicLookupHelper {
    private final ZrResolve zrResolve;

    ZrLookupHelper2(ZrResolve zrResolve, Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
        zrResolve.super(name, site, argtypes, typeargtypes);
        this.zrResolve = zrResolve;
    }

    @Override
    Symbol lookup(Env<AttrContext> env, Resolve.MethodResolutionPhase phase) {
        final Symbol bestSoFar = zrResolve.findFun(env, name, argtypes, typeargtypes,
                phase.isBoxingRequired(), phase.isVarargsRequired());
        final Pair<Symbol, ExMethodInfo> method = zrResolve.findMethod2(env, site, name, argtypes, typeargtypes,
                bestSoFar, phase.isBoxingRequired(), phase.isVarargsRequired(), false);
        if (method.snd != null && method.fst != bestSoFar && zrResolve.methodSymbolEnable(method.fst)) {
            throw new NeedRedirectMethod(method.fst, method.snd, site);
        }
        return bestSoFar;
    }
}
