package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

/** JDK 24 removed BasicLookupHelper's final lookup/doLookup bridge. */
class ZrLookupHelper extends Resolve.BasicLookupHelper {
    private final ZrResolve zrResolve;

    ZrLookupHelper(ZrResolve zrResolve, Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
        zrResolve.super(name, site, argtypes, typeargtypes);
        this.zrResolve = zrResolve;
    }

    @Override
    Symbol lookup(Env<AttrContext> env, Resolve.MethodResolutionPhase phase) {
        final Symbol bestSoFar = zrResolve.findMethod(env, site, name, argtypes, typeargtypes,
                phase.isBoxingRequired(), phase.isVarargsRequired());
        final Pair<Symbol, ExMethodInfo> method = zrResolve.findMethod2(env, site, name, argtypes, typeargtypes,
                bestSoFar, phase.isBoxingRequired(), phase.isVarargsRequired(), false);
        final Symbol newSymbol = method.fst;
        if ((method.snd != null && newSymbol instanceof Symbol.MethodSymbol && !(bestSoFar instanceof Symbol.MethodSymbol))
                || (newSymbol instanceof Symbol.MethodSymbol && bestSoFar instanceof Symbol.MethodSymbol && newSymbol != bestSoFar)) {
            throw new NeedRedirectMethod(newSymbol, method.snd, site);
        }
        return newSymbol;
    }
}
