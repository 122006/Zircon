package com.by122006.zircon;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes("org.junit.Ignore")
public class ZirconAnnotationProcessor  extends AbstractProcessor {

    private JavacTrees javacTrees;
    private TreeMaker treeMaker;
    private Names names;

    /**
     * 从Context中初始化JavacTrees，TreeMaker，Names
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        javacTrees = JavacTrees.instance(processingEnv);
        treeMaker = TreeMaker.instance(context);
        names = Names.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getRootElements();
        for (Element element : set) {
            // 获取当前类的抽象语法树
            JCTree tree = javacTrees.getTree(element);
            // 获取抽象语法树的所有节点
            // Visitor 抽象内部类，内部定义了访问各种语法节点的方法
            tree.accept(new TreeTranslator() {
                @Override
                public void visitApply(JCTree.JCMethodInvocation tree) {
                    super.visitApply(tree);
//                    tree.args.forEach(a->{
//                        System.out.println(TreeInfo.symbol(a.getTree()));
//                    });
//                    System.out.println("getKind:"+tree.getKind()+"; "+"getTypeArguments:"+tree.getTypeArguments()+"; "+"args:"+tree.args+"; "+"getMethodSelect:"+tree.getMethodSelect());
                }

//                @Override
//                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
//                    jcClassDecl.defs.stream()
//                            // 过滤，只处理变量类型
//                            .filter(it -> it.getKind().equals(Tree.Kind.VARIABLE))
//                            // 类型强转
//                            .map(it -> (JCTree.JCVariableDecl) it)
//                            .forEach(it -> {
//                                // 添加get方法
//                                jcClassDecl.defs = jcClassDecl.defs.prepend(genGetterMethod(it));
//                                // 添加set方法
//                                jcClassDecl.defs = jcClassDecl.defs.prepend(genSetterMethod(it));
//                            });
//
//                    super.visitClassDef(jcClassDecl);
//                }
            });

        }
        return true;
    }
}
