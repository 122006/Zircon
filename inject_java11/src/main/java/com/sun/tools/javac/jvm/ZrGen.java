package com.sun.tools.javac.jvm;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.jvm.ByteCodes.*;

@SuppressWarnings("unchecked")
public class ZrGen extends Gen {
    private final Context context;
    private final Types types;
    final Symtab syms;
    private Names names;
    private TreeMaker make;
    private final Type methodType;
    private final Check chk;
    private final Log log;

    protected ZrGen(Context context) {
        super(context);
        this.context = context;
        types = Types.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        names = Names.instance(context);
        chk = Check.instance(context);
        methodType = ReflectionUtil.getDeclaredField(this, Gen.class, "methodType");
    }

    public static ZrGen instance(Context context) {
        Gen res = context.get(genKey);
        if (res instanceof ZrGen) return (ZrGen) res;
        context.put(genKey, (Gen) null);
//        Options.instance(context).put("debug.code", "1");
        final ZrGen zrGen = new ZrGen(context);
        ReflectionUtil.setDeclaredField(JavaCompiler.instance(context), JavaCompiler.class, "gen", zrGen);
        ReflectionUtil.setDeclaredField(StringConcat.instance(context), StringConcat.class, "gen", zrGen);
        return zrGen;
    }

    /**
     * 判断是否为 $$NullSafe 方法调用。
     */
    private boolean isNullSafeMethod(JCTree.JCExpression expr) {
        if (expr instanceof JCTree.JCMethodInvocation) {
            JCTree.JCMethodInvocation invoc = (JCTree.JCMethodInvocation) expr;
            if (invoc.meth instanceof JCTree.JCFieldAccess) {
                final JCTree.JCFieldAccess meth = (JCTree.JCFieldAccess) invoc.meth;
                return meth.name.contentEquals("$$NullSafe");
            }
        }
        return false;
    }

    Map<Integer, ZrGenApplyDepthInfo> applyChains = new HashMap<Integer, ZrGenApplyDepthInfo>();
    int applyDepth = 0;

    private boolean isValueOfMethod(Symbol.MethodSymbol msym) {
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

    void intoNewApplyDepthAndNoAcceptNull() {
        applyDepth++;
    }

    void leaveCurrentApplyDepth() {
        applyChains.remove(applyDepth);
        applyDepth--;
    }


    Type getTopStackType(Code.State state) {
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


    void pop() {
        final Code code = getCode();
        if (code.pendingJumps != null) code.resolvePending();
        final Type topStackType = getTopStackType(code.state);
        if (Code.width(topStackType) == 1) {
            code.emitop0(pop);
        }
        if (Code.width(topStackType) == 2) {
            code.emitop0(pop2);
        }
    }

    public void _visitAssign(JCTree.JCAssign tree) {
        final Code code = getCode();

        intoNewApplyDepthAndNoAcceptNull();

        final ZrGenApplyDepthInfo zrGenApplyDepthInfo = new ZrGenApplyDepthInfo(applyDepth, code.state);

        applyChains.put(applyDepth, zrGenApplyDepthInfo);

        Items.Item l = genExpr(tree.lhs, tree.lhs.type);

        final Code.Chain nullChain = zrGenApplyDepthInfo.nullChain;

        leaveCurrentApplyDepth();

        if (nullChain != null) {
            genExpr(tree.rhs, tree.lhs.type).load();

            if (tree.rhs.type.hasTag(BOT)) {
                code.state.forceStackTop(tree.lhs.type);
            }

            getItems().makeAssignItem(l).load();

            Code.Chain thenExit = chainCreate(goto_);
            chainJoin(nullChain, tree.lhs);
            pop();

            genExpr(tree.rhs, tree.lhs.type).load();

            if (tree.rhs.type.hasTag(BOT)) {
                code.state.forceStackTop(tree.lhs.type);
            }
            chainJoin(thenExit, tree.rhs);
            result = getItems().makeStackItem(tree.lhs.type);
        } else {
            genExpr(tree.rhs, tree.lhs.type).load();


            if (tree.rhs.type.hasTag(BOT)) {
                code.state.forceStackTop(tree.lhs.type);
            }

            result = getItems().makeAssignItem(l);
        }
    }

    @Override
    public Items.Item genExpr(JCTree tree, Type pt) {
        try {
            if (tree instanceof JCTree.JCAssign) {
                Type prevPt = this.pt;
                try {
                    this.pt = pt;
                    _visitAssign((JCTree.JCAssign) tree);
                    return result.coerce(pt);
                } catch (Symbol.CompletionFailure ex) {
                    chk.completionError(tree.pos(), ex);
                    getCode().state.stacksize = 1;
                    return getItems().makeStackItem(pt);
                } finally {
                    this.pt = prevPt;
                }
            }
            return super.genExpr(tree, pt);
        } catch (Exception e) {
            CommonUtil.logError(log, tree, "genExpr fail:[" + e.getClass().getSimpleName() + "]" + e.getMessage());
            e.printStackTrace();
            throw (RuntimeException) (e);
        }
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        try {
            final Code code = getCode();
            if (applyDepth == 0) applyDepth++;
            final int currentApplyDepth = applyDepth;
            ZrGenApplyDepthInfo nowChains = applyChains.get(currentApplyDepth);
            boolean isFirstDepth = nowChains == null;
            if (isFirstDepth) {
                applyChains.put(currentApplyDepth, nowChains = new ZrGenApplyDepthInfo(currentApplyDepth, code.state));
            }
            super.visitSelect(tree);

            if (isFirstDepth && nowChains.nullChain != null) {
                //单链第一个方法引用，需要承接跳转
                Code.Chain thenExit = chainCreate(goto_);
                final Code.Chain nullChain = nowChains.nullChain;
                chainJoin(nullChain, tree);

                pop();

                applyChains.remove(currentApplyDepth);
                if (!(result instanceof Items.StaticItem)) {
                    final boolean resultIsPrimitive = result.typecode < 8;
                    if (pt != null && pt != syms.voidType) {
                        if (pt.isPrimitive() && resultIsPrimitive) {
                            throwNullPointerException("When the expected result value is a primitive type (" + result + ")" +
                                    ", the final field in the optional chain cannot also return a primitive type (" + pt + ")." +
                                    " Consider providing a default value through an Elvis expression instead."
                            );
                        } else {
                            code.emitop0(ByteCodes.aconst_null);
                            result = getItems().makeStackItem(syms.botType).coerce(pt).load();
                        }
                    }
                }


                chainJoin(thenExit, tree);

            }
            if (isFirstDepth && applyDepth == 1) {
                leaveCurrentApplyDepth();
            }
        } catch (Exception e) {
            CommonUtil.logError(log, tree, "visitSelect fail:[" + e.getClass().getSimpleName() + "]" + e.getMessage());
            e.printStackTrace();
            throw (RuntimeException) (e);
        }
    }


    public void visitApply(JCTree.JCMethodInvocation tree) {
        final Code code = getCode();
        try {
            if (applyDepth == 0) applyDepth++;
            final int currentApplyDepth = applyDepth;
            ZrGenApplyDepthInfo currentChains = applyChains.get(currentApplyDepth);
            boolean isFirstDepth = currentChains == null;
            if (isFirstDepth) {
                applyChains.put(currentApplyDepth, currentChains = new ZrGenApplyDepthInfo(currentApplyDepth, code.state));
            }
            _setTypeAnnotationPositions(tree.pos);

            Symbol.MethodSymbol msym = (Symbol.MethodSymbol) TreeInfo.symbol(tree.meth);
            if (isNullSafeMethod(tree)) {
                final JCTree.JCExpression head = tree.args.head;
                if (head instanceof JCTree.JCMethodInvocation || head instanceof JCTree.JCFieldAccess) {
                    genExpr(head, msym.externalType(types).getParameterTypes().head).load();
                } else {
                    intoNewApplyDepthAndNoAcceptNull();
                    genExpr(head, msym.externalType(types).getParameterTypes().head).load();
                    leaveCurrentApplyDepth();
                }

                code.emitop0(ByteCodes.dup);
                Code.Chain nonnull = chainCreate(if_acmp_nonnull);
                while (code.state.stacksize - currentChains.backState.stacksize > 0) {
                    pop();
                }
                code.emitop0(ByteCodes.aconst_null);
                Code.Chain _nullSafe = chainCreate(goto_);
                currentChains.nullChain = Code.mergeChains(currentChains.nullChain, _nullSafe);

                chainJoin(nonnull, tree);
            } else {
                boolean isExMethod = msym.getAnnotationMirrors().stream()
                        .anyMatch(annotation -> annotation.type.toString().equals("zircon.ExMethod"))
                        && tree.getArguments().size() >= 1 && tree.getArguments().get(0).pos < tree.pos;
                final boolean isValueOfMethod = isValueOfMethod(msym);

                if (!isExMethod && !isValueOfMethod) {
                    final Items.Item item = genExpr(tree.meth, methodType);
                    List<Type> pts = msym.externalType(types).getParameterTypes();
                    List<JCTree.JCExpression> args = tree.args;
                    for (List<JCTree.JCExpression> l = args; l.nonEmpty(); l = l.tail) {
                        intoNewApplyDepthAndNoAcceptNull();
                        genExpr(l.head, pts.head).load();
                        leaveCurrentApplyDepth();
                        pts = pts.tail;
                    }
                    result = item.invoke();
                } else {
                    final Items.Item item = genExpr(tree.meth, methodType);
                    List<Type> pts = msym.externalType(types).getParameterTypes();
                    List<JCTree.JCExpression> args = tree.args;
                    if (args.size() > 0) {
                        //拓展方法及包装器方法的首个参数不深度+1
                        genExpr(args.head, pts.head).load();
                        args = args.tail;
                        pts = pts.tail;
                        for (List<JCTree.JCExpression> l = args; l.nonEmpty(); l = l.tail) {
                            intoNewApplyDepthAndNoAcceptNull();
                            genExpr(l.head, pts.head).load();
                            leaveCurrentApplyDepth();
                            pts = pts.tail;
                        }
                    }
                    result = item.invoke();
                }
                if (!msym.isDynamic()) {
                    code.statBegin(tree.pos);
                }
            }
            if (tree.type != syms.voidType)
                result = getItems().makeStackItem(tree.type).coerce(pt).load();

            if (isFirstDepth && currentChains.nullChain != null) {
                //单链第一个方法引用，需要承接跳转
                Code.Chain thenExit = chainCreate(goto_);
                final Code.Chain nullChain = currentChains.nullChain;
                chainJoin(nullChain, tree);
                pop();
                applyChains.remove(currentApplyDepth);
                final boolean resultIsPrimitive = result.typecode < 8;
                if (pt != null && pt != syms.voidType) {
                    if (pt.isPrimitive() && resultIsPrimitive) {
                        throwNullPointerException("When the expected result value is a primitive type (" + result + ")" +
                                ", the final method in the optional chain cannot also return a primitive type (" + pt + ")." +
                                " Consider providing a default value through an Elvis expression instead.");
                    } else {
                        code.emitop0(ByteCodes.aconst_null);
                        getItems().makeStackItem(syms.botType).coerce(pt);
                    }
                }
                chainJoin(thenExit, tree);
            }
            if (isFirstDepth && applyDepth == 1) {
                leaveCurrentApplyDepth();
            }
            result = getItems().makeStackItem(pt);
        } catch (Error e) {
            CommonUtil.logError(log, tree, "genApply fail:[" + e.getClass().getSimpleName() + "]" + e.getMessage()
                    + "\ncode.stack[" + code.state.stacksize + "]:" + Arrays.toString(code.state.stack));
            e.printStackTrace();
            throw e;
        }

    }

    private Code.Chain chainCreate(int bit) {
        return getCode().branch(bit);
    }

    private void chainJoin(Code.Chain thenExit, JCDiagnostic.DiagnosticPosition pos) {
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

    private void _setTypeAnnotationPositions(int pos) {
        ReflectionUtil.invokeMethod(this, Gen.class, "setTypeAnnotationPositions", pos);
    }

    @Override
    public Items.CondItem genCond(JCTree _tree, boolean markBranches) {
        if (_tree instanceof JCTree.JCConditional) {
            final JCTree.JCConditional tree = (JCTree.JCConditional) _tree;
            if (tree.cond instanceof JCTree.JCMethodInvocation) {
                final JCTree.JCMethodInvocation cond = (JCTree.JCMethodInvocation) tree.cond;
                final Symbol.MethodSymbol sym = (Symbol.MethodSymbol) TreeInfo.symbol(cond.meth);
                if (sym.name.contentEquals("$$elvisExpr")) {
                    final Name qualifiedName = sym.getEnclosingElement().getQualifiedName();
                    if (qualifiedName.contentEquals("zircon.BiOp")) {
                        Items.CondItem result = genExpr(_tree, syms.booleanType).mkCond();
                        if (markBranches) result.tree = _tree;
                        return result;
                    }
                }
            }
        }
        return super.genCond(_tree, markBranches);
    }


    @Override
    public void visitConditional(JCTree.JCConditional tree) {
        final Code code = getCode();

        try {
            if (tree.cond instanceof JCTree.JCMethodInvocation) {
                final JCTree.JCMethodInvocation cond = (JCTree.JCMethodInvocation) tree.cond;
                final Symbol.MethodSymbol sym = (Symbol.MethodSymbol) TreeInfo.symbol(cond.meth);
                if (sym.name.contentEquals("$$elvisExpr")) {
                    final Name qualifiedName = sym.getEnclosingElement().getQualifiedName();
                    if (qualifiedName.contentEquals("zircon.BiOp")) {
                        final int currentApplyDepth = applyDepth + 1;
                        applyDepth = currentApplyDepth;
                        if (applyChains.get(currentApplyDepth) == null) {
                            applyChains.put(currentApplyDepth, new ZrGenApplyDepthInfo(currentApplyDepth, code.state));
                        }

                        final JCTree.JCExpression second = tree.getFalseExpression();
                        final JCTree.JCExpression head = tree.getTrueExpression();


                        code.statBegin(head.pos);
                        result = genExpr(head, head.type).load().coerce(pt);
                        code.state.forceStackTop(pt);
                        Code.Chain nullChain = applyChains.get(currentApplyDepth).nullChain;
                        leaveCurrentApplyDepth();
                        if (!head.type.isPrimitive()) {
                            code.emitop0(dup);
                            Code.Chain elseChain = chainCreate(if_acmp_null);
                            nullChain = Code.mergeChains(nullChain, elseChain);
                        }
                        if (nullChain != null) {
                            //单链第一个方法引用，需要承接跳转
                            Code.Chain thenExit = chainCreate(goto_);
                            chainJoin(nullChain, tree.truepart);

                            pop();

                            {
                                code.statBegin(second.pos);
                                result = genExpr(second, second.type).load().coerce(pt);
                                code.state.forceStackTop(pt);
                            }
                            chainJoin(thenExit, tree.falsepart);
                        }

                        return;
                    }
                }
            }
        } catch (Error e) {
            CommonUtil.logError(log, tree, "visitConditional fail:[" + e.getClass().getSimpleName() + "]" + e.getMessage()
                    + "\ncode.stack[" + code.state.stacksize + "]:" + Arrays.toString(code.state.stack));
            e.printStackTrace();
            throw e;
        }
        super.visitConditional(tree);
    }

    private void throwNullPointerException(String str) {
        final Symbol.ClassSymbol classSymbol = syms.enterClass(syms.java_base, names.fromString(NullPointerException.class .getName()));
        Code code = getCode();
        code.emitop2(new_, ReflectionUtil.<Gen, Pool>getDeclaredField(this, Gen.class, "pool").put(classSymbol.type));
        code.emitop0(dup);
        getItems().makeImmediateItem(syms.stringType, str).load();
        final Iterable<Symbol> symbolsByName = classSymbol.members().getSymbolsByName(names.fromString("<init>"));
        getItems().makeMemberItem(symbolsByName.iterator().next(), true).invoke();
        ReflectionUtil.invokeMethod(code, Code.class, "emitop", athrow);
        code.state.pop(1);
        code.markDead();
    }
}
