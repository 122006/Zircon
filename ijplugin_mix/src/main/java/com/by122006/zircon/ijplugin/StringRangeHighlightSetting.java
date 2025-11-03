package com.by122006.zircon.ijplugin;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import com.sun.tools.javac.parser.ReflectionUtil;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Objects;

/**
 * StringRange高亮设置类，用于配置每种类型的颜色和字体样式
 */
@NoArgsConstructor
public class StringRangeHighlightSetting {

    public static int getAllCount() {
        return 5;
    }

    public static String[] getColorName() {
        return new String[]{"硬编码", "代码", "格式化", "其他1", "其他2"};
    }

    /**
     * 前景色 - 白天模式
     */
    public Integer foregroundColorLight;

    /**
     * 前景色 - 夜间模式
     */
    public Integer foregroundColorDark;

    /**
     * 背景色 - 白天模式
     */
    public Integer backgroundColorLight;

    /**
     * 背景色 - 夜间模式
     */
    public Integer backgroundColorDark;

    /**
     * 效果色（如下划线颜色）- 白天模式
     */
    public Integer effectColorLight;

    /**
     * 效果色（如下划线颜色）- 夜间模式
     */
    public Integer effectColorDark;

    /**
     * 效果类型索引
     */
    public Integer effectType;

    /**
     * 字体类型（0=PLAIN, 1=BOLD, 2=ITALIC, 3=BOLD+ITALIC）
     */
    public int fontType;


    /**
     * 完整构造函数
     *
     * @param foregroundColorLight 前景色-白天模式
     * @param foregroundColorDark  前景色-夜间模式
     * @param backgroundColorLight 背景色-白天模式
     * @param backgroundColorDark  背景色-夜间模式
     * @param effectColorLight     效果色-白天模式
     * @param effectColorDark      效果色-夜间模式
     * @param effectType           效果类型
     * @param fontType             字体类型
     */
    public StringRangeHighlightSetting(
            Integer foregroundColorLight, Integer foregroundColorDark,
            Integer backgroundColorLight, Integer backgroundColorDark,
            Integer effectColorLight, Integer effectColorDark,
            Integer effectType, int fontType) {
        this.foregroundColorLight = foregroundColorLight;
        this.foregroundColorDark = foregroundColorDark;
        this.backgroundColorLight = backgroundColorLight;
        this.backgroundColorDark = backgroundColorDark;
        this.effectColorLight = effectColorLight;
        this.effectColorDark = effectColorDark;
        this.effectType = effectType;
        this.fontType = fontType;
    }


    /**
     * 创建4种默认设置的数组
     *
     * @return 默认设置数组
     */
    @SuppressWarnings("UseJBColor")
    public static StringRangeHighlightSetting[] createDefaults() {
        final StringRangeHighlightSetting[] stringRangeHighlightSettings = new StringRangeHighlightSetting[StringRangeHighlightSetting.getAllCount()];
        stringRangeHighlightSettings[0] = new StringRangeHighlightSetting(null, null, new Color(255, 255, 255, 0).getRGB(), new Color(0, 0, 0, 0).getRGB(), 0x999999, 0x696969, 0, Font.PLAIN);
        stringRangeHighlightSettings[1] = new StringRangeHighlightSetting(0x00627A, 0x78BDB0, null, null, 0x999999, 0x696969, 0, Font.ITALIC);
        stringRangeHighlightSettings[2] = new StringRangeHighlightSetting(0xbbbbbb, 0x696969, null, null, 0x999999, 0x696969, 0, Font.PLAIN);

        stringRangeHighlightSettings[3] = new StringRangeHighlightSetting(0x00627A, 0x78BDB0, null, null, 0x999999, 0x696969, 0, Font.ITALIC);
        stringRangeHighlightSettings[4] = new StringRangeHighlightSetting(0x00627A, 0x78BDB0, null, null, 0x999999, 0x696969, 0, Font.ITALIC);
        return stringRangeHighlightSettings;
    }

    private static EffectType[] EFFECT_TYPES = {
            EffectType.LINE_UNDERSCORE,
            EffectType.BOLD_LINE_UNDERSCORE,
            EffectType.WAVE_UNDERSCORE,
            EffectType.STRIKEOUT,
            EffectType.BOXED,
            EffectType.ROUNDED_BOX,
            null
    };

    private static EffectType getEffectTypeByIndex(Integer index) {
        if (index != null && index >= 0 && index < EFFECT_TYPES.length) {
            return EFFECT_TYPES[index];
        }
        return EffectType.LINE_UNDERSCORE; // 默认值
    }

    /**
     * 获取效果类型的索引
     *
     * @param effectType 效果类型
     * @return 索引值
     */
    public static int getEffectTypeIndex(EffectType effectType) {
        for (int i = 0; i < EFFECT_TYPES.length; i++) {
            if (Objects.equals(EFFECT_TYPES[i], effectType)) {
                return i;
            }
        }
        return EFFECT_TYPES.length - 1; // 默认为NONE
    }

    /**
     * 将当前设置转换为TextAttributesKey对象
     *
     * @param externalName 外部名称
     * @return TextAttributesKey对象
     */
    public TextAttributesKey toTextAttributesKey(@NotNull String externalName) {
        // 创建前景色和背景色，使用JBColor支持白天/夜间模式
        JBColor foreground = foregroundColorLight != null && foregroundColorDark != null ?
                new JBColor(foregroundColorLight, foregroundColorDark) : null;
        JBColor background = backgroundColorLight != null && backgroundColorDark != null ?
                new JBColor(backgroundColorLight, backgroundColorDark) : null;
        JBColor effect = effectColorLight != null && effectColorDark != null ?
                new JBColor(effectColorLight, effectColorDark) : null;

        // 创建TextAttributes
        TextAttributes textAttributes = new TextAttributes(
                foreground,
                background,
                effect,
                getEffectTypeByIndex(effectType),
                fontType
        );
        final TextAttributesKey textAttributesKey = TextAttributesKey.find(externalName);
        ReflectionUtil.setDeclaredField(textAttributesKey, TextAttributesKey.class, "myDefaultAttributes", textAttributes);
        return textAttributesKey;
    }

    public TextAttributesKey toTextAttributesKey(int index) {
        return toTextAttributesKey("ZrStringColor" + index);
    }
}