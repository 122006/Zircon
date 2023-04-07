package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.Kinds.Kind.ABSENT_MTH;

public class ZrResolve extends Resolve {
    protected ZrResolve(Context context) {
        super(context);
    }
    public static ZrResolve instance(Context context) {
        System.out.println("ZrResolve instance()");
        Resolve res = context.get(resolveKey);
        if (res instanceof ZrResolve) return (ZrResolve)res;
        context.put(resolveKey, (Resolve)null);
        return new ZrResolve(context);
    }
    final Symbol methodNotFound = new SymbolNotFoundError(ABSENT_MTH);

    @Override
    Symbol resolveMethod(JCDiagnostic.DiagnosticPosition pos, Env<AttrContext> env, Name name, List<Type> argtypes, List<Type> typeargtypes) {
        System.out.println("resolveMethod:name="+name);
        final Symbol fun = super.resolveMethod(pos, env, name, argtypes, typeargtypes);
        return fun;
    }

    Symbol findFun(Env<AttrContext> env, Name name,
                   List<Type> argtypes, List<Type> typeargtypes,
                   boolean allowBoxing, boolean useVarargs){
        final Symbol fun = super.findFun(env, name, argtypes, typeargtypes, allowBoxing, useVarargs);
        System.out.println("findFun:name="+name);
        return fun;
    }
    @Override
    Symbol findMethod(Env<AttrContext> env,
                      Type site,
                      Name name,
                      List<Type> argtypes,
                      List<Type> typeargtypes,
                      boolean allowBoxing,
                      boolean useVarargs) {
        System.out.println("name="+name+";"+"site="+site);
        Symbol bestSoFar = super.findMethod(env, site, name, argtypes, typeargtypes, allowBoxing, useVarargs);
        if (bestSoFar.kind.ordinal() >= Kinds.Kind.ERR.ordinal()) {
            if (name.toString().equals("add")&&argtypes.size()==2){
                final List<Type> tail = argtypes.tail;
                tail.add(0,site);
                bestSoFar = findMethod(env, findType(env,names.fromString("test.TestClass2.Test")).type, names.fromString("add"), tail, null, true, false);
            }
        }
        return bestSoFar;
    }
}
