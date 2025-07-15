package com.sun.tools.javac.jvm;

/**
 * @ClassName: ZrGenChainInfo
 * @Author: zwh
 * @Date: 2025/7/9 9:24
 * @Description:
 */
public class ZrGenApplyDepthInfo {
    Code.Chain nullChain = null;
    Code.State backState;
    int depth;

    ZrGenApplyDepthInfo(int depth, Code.State backState) {
        this.backState = backState.dup();
        this.depth = depth;
    }

}
