package com.by122006.zircon.ijplugin252;

import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.psi.JavaTokenType;

/**
 * @ClassName: ZrJavaTokenType
 * @Author: 122006
 * @Date: 2025/7/1 15:34
 * @Description:
 */
public interface ZrJavaSyntaxTokenType extends JavaTokenType {

    SyntaxElementType ELVIS = new SyntaxElementType("ELVIS", null, null, true,null);

}
