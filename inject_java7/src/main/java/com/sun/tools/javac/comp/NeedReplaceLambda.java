package com.sun.tools.javac.comp;

import com.sun.tools.javac.tree.JCTree;

/**
 * @ClassName: NeedReplaceLambda
 * @Author: 122006
 * @Date: 2025/4/16 9:52
 * @Description:
 */
public class NeedReplaceLambda extends RuntimeException {
    public NeedReplaceLambda(JCTree.JCLambda bestSoFar, JCTree.JCMemberReference memberReference, ExMethodInfo methodInfo) {
        super("搜索到不支持且被拓展的非静态方法引用：" + memberReference + "\n暂不支持该拓展形式,请替换为lambda表达式：\n" + bestSoFar + "\n请至github联系开发者以修复该情况");
        this.bestSoFar = bestSoFar;
        this.memberReference = memberReference;
        this.methodInfo = methodInfo;
    }

    JCTree.JCLambda bestSoFar;
    JCTree.JCMemberReference memberReference;
    ExMethodInfo methodInfo;
}
