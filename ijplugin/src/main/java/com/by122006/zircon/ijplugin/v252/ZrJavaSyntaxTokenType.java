package com.by122006.zircon.ijplugin.v252;

import com.by122006.zircon.ijplugin.ZrJavaTokenType;
import com.intellij.java.frontback.psi.impl.syntax.BasicJavaElementTypeConverterKt;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.psi.ElementTypeConverter;
import com.intellij.platform.syntax.psi.ElementTypeConverterImpl;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import com.intellij.psi.tree.IElementType;
import com.sun.tools.javac.parser.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @ClassName: ZrJavaTokenType
 * @Author: 122006
 * @Date: 2025/7/1 15:34
 * @Description:
 */
@Slf4j
public class ZrJavaSyntaxTokenType {
    //    static {
//        final ElementTypeConverter basicJavaElementTypeConverter = BasicJavaElementTypeConverterKt.getBasicJavaElementTypeConverter();
//    }
//
    static SyntaxElementType ELVIS = new SyntaxElementType("ELVIS", null, null, false, null);

    static {
        {
            final ElementTypeConverter converter = BasicJavaElementTypeConverterKt.getBasicJavaElementTypeConverter();
            if (converter instanceof ElementTypeConverterImpl) {
                final com.intellij.util.fastutil.ints.Int2ObjectOpenHashMap<IElementType> syntaxToOld = ReflectionUtil.getDeclaredField(converter, ElementTypeConverterImpl.class, "syntaxToOld");
                syntaxToOld.put(ELVIS.getIndex(), ZrJavaTokenType.ELVIS);
                final com.intellij.util.fastutil.ints.Int2ObjectOpenHashMap<SyntaxElementType> oldToSyntax = ReflectionUtil.getDeclaredField(converter, ElementTypeConverterImpl.class, "oldToSyntax");
                oldToSyntax.put(ZrJavaTokenType.ELVIS.getIndex(), ELVIS);
            } else {
                log.warn("unknown ElementTypeConverter[BasicJavaElementTypeConverterKt]:" + converter.getClass().getSimpleName());
            }
        }
        {
            final ElementTypeConverter converter = ElementTypeConverters.getConverter(JavaLanguage.INSTANCE);
            if (converter instanceof ElementTypeConverterImpl) {
                final com.intellij.util.fastutil.ints.Int2ObjectOpenHashMap<IElementType> syntaxToOld = ReflectionUtil.getDeclaredField(converter, ElementTypeConverterImpl.class, "syntaxToOld");
                syntaxToOld.put(ELVIS.getIndex(), ZrJavaTokenType.ELVIS);
                final com.intellij.util.fastutil.ints.Int2ObjectOpenHashMap<SyntaxElementType> oldToSyntax = ReflectionUtil.getDeclaredField(converter, ElementTypeConverterImpl.class, "oldToSyntax");
                oldToSyntax.put(ZrJavaTokenType.ELVIS.getIndex(), ELVIS);
            } else {
                log.warn("unknown ElementTypeConverter[ElementTypeConverters]:" + converter.getClass().getSimpleName());
            }
        }
    }


}
