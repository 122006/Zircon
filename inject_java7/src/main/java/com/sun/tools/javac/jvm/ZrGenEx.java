package com.sun.tools.javac.jvm;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.*;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public class ZrGenEx extends Gen{
    protected final Log log;

    protected ZrGenEx(Context context) {
        super(context);
        log = Log.instance(context);
    }
    Code getCode() {
        return ReflectionUtil.getDeclaredField(this, Gen.class, "code");
    }

    Items getItems() {
        return ReflectionUtil.getDeclaredField(this, Gen.class, "items");
    }



    protected Code.Chain chainCreate(int bit) {
        return getCode().branch(bit);
    }

    protected void chainJoin(Code.Chain thenExit, JCDiagnostic.DiagnosticPosition pos) {
        final Code code = getCode();
        try {
            code.resolve(thenExit);
        } catch (Error e) {
            CommonUtil.logError(log, pos, "chainJoin fail:[" + e.getClass().getSimpleName() + "]" + e.getMessage()
                    + "\nchain.stack[" + thenExit.state.stacksize + "]:" + Arrays.toString(thenExit.state.stack)
                    + "\ncode.stack[" + code.state.stacksize + "]:" + Arrays.toString(code.state.stack));
            e.printStackTrace();
            throw e;
        }
    }

    protected void _setTypeAnnotationPositions(int pos) {
        ReflectionUtil.invokeMethod(this, Gen.class, "setTypeAnnotationPositions", pos);
    }

    public static boolean isValueOfMethod(Symbol.MethodSymbol msym) {
        if (!msym.getQualifiedName().contentEquals("valueOf")) return false;
        final Name qualifiedName = msym.getEnclosingElement().getQualifiedName();
        if (msym.getParameters().size() != 1) {
            return false;
        }
        if (!msym.getParameters().get(0).type.isPrimitive()) {
            return false;
        }
        if (qualifiedName.contentEquals("java.lang.Integer") || qualifiedName.contentEquals("java.lang.Long")
                || qualifiedName.contentEquals("java.lang.Short") || qualifiedName.contentEquals("java.lang.Byte")
                || qualifiedName.contentEquals("java.lang.Character") || qualifiedName.contentEquals("java.lang.Boolean")
                || qualifiedName.contentEquals("java.lang.Float") || qualifiedName.contentEquals("java.lang.Double")) {
            return true;
        }
        return false;

    }
    /**
     * 判断是否为 $$NullSafe 方法调用。
     */
    public boolean isNullSafeMethod(JCTree.JCExpression expr) {
        if (expr instanceof JCTree.JCMethodInvocation) {
            JCTree.JCMethodInvocation invoc = (JCTree.JCMethodInvocation) expr;
            if (invoc.meth instanceof JCTree.JCFieldAccess) {
                final JCTree.JCFieldAccess meth = (JCTree.JCFieldAccess) invoc.meth;
                return meth.name.contentEquals("$$NullSafe");
            }
        }
        return false;
    }

    static Type getTopStackType(Code.State state) {
        if (state.stacksize == 0) {
            return null;
        }
        if (state.stacksize == 1)
            return state.stack[0];
        final Type type = state.stack[state.stacksize - 1];
        if (type != null) {
            return type;
        }
        final Type type2 = state.stack[state.stacksize - 2];
        final int width = Code.width(type2);
        if (width <= 1) {
            return type;
        }
        if (width == 2) {
            return type2;
        }
        throw new AssertionError();
    }
}
