package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.Code;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.jvm.Items;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ReflectionUtil;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Names;

import java.lang.reflect.Method;

import static com.sun.tools.javac.jvm.ByteCodes.*;

@SuppressWarnings("unchecked")
public class ZrGen extends Gen {
    private final Context context;
    private final Types types;
    final Symtab syms;
    private Names names;
    private TreeMaker make;


    protected ZrGen(Context context) {
        super(context);
        this.context = context;
        types = Types.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        names = Names.instance(context);
    }

    public static ZrGen instance(Context context) {
        Gen res = context.get(genKey);
        if (res instanceof ZrGen) return (ZrGen) res;
        context.put(genKey, (Gen) null);
//        Options.instance(context).put("debug.code", "1");
        final ZrGen zrGen = new ZrGen(context);
        ReflectionUtil.setDeclaredField(JavaCompiler.instance(context), JavaCompiler.class, "gen", zrGen);
        return zrGen;
    }

    int makeRef(JCDiagnostic.DiagnosticPosition pos, Type type) {
        try {
            final Method makeRef = Gen.class.getDeclaredMethod("makeRef", JCDiagnostic.DiagnosticPosition.class, Type.class);
            makeRef.setAccessible(true);
            return (int) makeRef.invoke(this, pos, type);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    <T> void loadItem(T item) {
        try {
            final Method makeRef = item.getClass().getDeclaredMethod("load");
            makeRef.setAccessible(true);
            final T returnV = (T) makeRef.invoke(item);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void visitTree(JCTree that) {
        super.visitTree(that);
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        if (tree.meth instanceof JCTree.JCFieldAccess) {
            final JCTree.JCExpression selected = ((JCTree.JCFieldAccess) tree.meth).selected;
            if (selected.type != null && "zircon.BiOp".equals(selected.type.toString())) {
                if (((JCTree.JCFieldAccess) tree.meth).name.contentEquals("$$dup")) {
                    final JCTree.JCExpression head = tree.getArguments().head;
                    loadItem(genExpr(head, head.type));
                    updateResult();
                    getCode().emitop0(dup);
                    return;
                }
                if (((JCTree.JCFieldAccess) tree.meth).name.contentEquals("$$ignore")) {
                    updateResult();
                    return;
                }
                if (((JCTree.JCFieldAccess) tree.meth).name.contentEquals("$$useParam2WithParam1Type")) {
                    final JCTree.JCExpression second = tree.getArguments().get(1);
                    final JCTree.JCExpression head = tree.getArguments().get(0);
                    loadItem(genExpr(second, head.type));
                    getCode().emitop0(pop);
                    return;
                }

                if (((JCTree.JCFieldAccess) tree.meth).name.contentEquals("$$pop")) {
                    getCode().emitop0(pop);
                    if (tree.args.isEmpty()) {
                        getCode().emitop0(iconst_1);
                    } else {
                        final JCTree.JCExpression head = tree.getArguments().get(0);
                        loadItem(genExpr(head, head.type));
                        updateResult();
                    }
                    return;
                }
                if (((JCTree.JCFieldAccess) tree.meth).name.contentEquals("$$wrap")) {
                    final JCTree.JCExpression head = tree.getArguments().get(0);
                    loadItem(genExpr(head, head.type));
                    updateResult();
                    return;
                }
                if (((JCTree.JCFieldAccess) tree.meth).name.contentEquals("$$elvisExpr")) {
                    final JCTree.JCExpression second = tree.getArguments().get(1);
                    final JCTree.JCExpression head = tree.getArguments().get(0);
                    //模拟三元
                    Code code = getCode();
                    code.statBegin(head.pos);
                    loadItem(genExpr(head, head.type));
                    code.emitop0(dup);
                    Code.Chain elseChain = code.branch(if_acmp_null);
                    Code.Chain thenExit = code.branch(goto_);
                    {
                        code.resolve(elseChain);
                        code.statBegin(second.pos);
                        code.emitop0(pop);
                        loadItem(genExpr(second, second.type));
                    }
                    code.resolve(thenExit);
                    updateResult();
                    return;
                }

                if (((JCTree.JCFieldAccess) tree.meth).name.contentEquals("$$pop$$useParam2WithParam1Type")) {
                    getCode().emitop0(pop);
                    final JCTree.JCExpression second = tree.getArguments().get(1);
                    final JCTree.JCExpression head = tree.getArguments().get(0);
                    loadItem(genExpr(second, head.type));
                    updateResult();
                    return;
                }
            }
        }
        super.visitApply(tree);
    }

    private Code getCode() {
        return ReflectionUtil.getDeclaredField(this, Gen.class, "code");
    }

    private void updateResult() {
        final Items items = ReflectionUtil.getDeclaredField(this, Gen.class, "items");
        final Type pt = ReflectionUtil.getDeclaredField(this, Gen.class, "pt");
        final Object[] stackItem = ReflectionUtil.getDeclaredField(items, Items.class, "stackItem");
        ReflectionUtil.setDeclaredField(this, Gen.class, "result", stackItem[Code.typecode(pt)]);
    }

}
