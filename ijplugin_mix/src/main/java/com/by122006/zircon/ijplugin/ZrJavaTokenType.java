package com.by122006.zircon.ijplugin;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;

/**
 * @ClassName: ZrJavaTokenType
 * @Author: 122006
 * @Date: 2025/7/1 15:34
 * @Description:
 */
public interface ZrJavaTokenType extends JavaTokenType {

    IElementType ELVIS = new IJavaElementType("ELVIS");

}
