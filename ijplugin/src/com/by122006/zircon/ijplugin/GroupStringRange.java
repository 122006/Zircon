package com.by122006.zircon.ijplugin;

import java.util.ArrayList;
import java.util.List;

public class GroupStringRange {
    public static List<StringRange> build(String text) {
        List<StringRange> list = new ArrayList<>();
        int startI = 2;
        int selectModel = -1;
        int pCount = 0;
        for (int thisIndex = 2; thisIndex < text.length() - 1; thisIndex++) {
            char ch = text.charAt(thisIndex);
            if (text.charAt(thisIndex - 1) == '\\' && text.charAt(thisIndex - 2) != '\\') {
                continue;
            }
            if (selectModel == 2) {
                if (ch == '{') pCount++;
                if (ch == '}') {
                    pCount--;
                    if (pCount == 0) {
                        if (thisIndex - startI > 0)
                            list.add(StringRange.of(true, startI, thisIndex));
                        startI = thisIndex + 1;
                        selectModel = -1;
                    }
                }
                continue;
            }
            if (selectModel == 1) {
                if (String.valueOf(ch).matches(pCount>0?"[^)]{1}":"[A-Za-z0-9_\\u4e00-\\u9fa5.$]{1}")) continue;
                if (ch == '(') {
                    if (text.substring(thisIndex).matches("^\\([^)]*\\).*")) {
                        pCount++;
                        continue;
                    }
                } else if (ch == ')') {
                    if (pCount > 0) {
                        if (text.substring(thisIndex).matches("^\\)\\.[A-Za-z_\\u4e00-\\u9fa5$]+.*")) {
                            pCount--;
                            continue;
                        }
                        thisIndex++;
                    }
                }
                list.add(StringRange.of(true, startI, thisIndex));
                startI = thisIndex;
                selectModel = -1;
                continue;
            }
            if (ch == '$' && !(text.charAt(thisIndex - 1) == '\\' && text.charAt(thisIndex - 2) == '\\')
                    && String.valueOf(text.charAt(thisIndex + 1)).matches("[A-Za-z_\\u4e00-\\u9fa5{$]{1}")) {
                if (thisIndex - startI != 0)
                    list.add(StringRange.of(false, startI, thisIndex));
                if (text.charAt(thisIndex + 1) == '{') {
                    startI = thisIndex + 2;
                    selectModel = 2;
                    pCount = 0;
                } else {
                    startI = thisIndex + 1;
                    selectModel = 1;
                    pCount = 0;
                }
            }
        }
        if (text.length() - 1 > startI)
            list.add(StringRange.of(selectModel > 0, startI, text.length() - 1));
        return list;
    }

    public static class StringRange {
        static StringRange of(boolean isJavaCode, int startIndex, int endIndex) {
            StringRange stringRange = new StringRange();
            stringRange.endIndex = endIndex;
            stringRange.isJavaCode = isJavaCode;
            stringRange.startIndex = startIndex;
            return stringRange;
        }

        boolean isJavaCode = false;
        int startIndex;
        int endIndex;
    }
}
