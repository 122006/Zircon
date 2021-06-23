package com.sun.tools.javac.parser;

import javax.xml.stream.FactoryConfigurationError;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
                        if (thisIndex - startI > 0) {
                            String substring = text.substring(startI, thisIndex);
                            if (substring.startsWith( "%")&&text.startsWith("f")) {
                                int splitChar = substring.indexOf( ":");
                                if (splitChar == -1) {
                                    list.add(StringRange.of(1, startI, thisIndex));
                                } else {
                                    list.add(StringRange.of(2, startI, startI + splitChar));
                                    list.add(StringRange.of(1, startI + splitChar + 1, thisIndex));
                                }
                            } else {
                                list.add(StringRange.of(1, startI, thisIndex));
                            }


                        }
                        startI = thisIndex + 1;
                        selectModel = -1;
                    }
                }
                continue;
            }
            if (selectModel == 1) {
                if (String.valueOf(ch).matches(pCount > 0 ? "[^)]{1}" : "[A-Za-z0-9_\\u4e00-\\u9fa5.$]{1}")) continue;
                if (ch == '(') {
                    if (text.substring(thisIndex).matches( "^\\([^)]*\\).*")) {
                        pCount++;
                        continue;
                    }
                } else if (ch == ')') {
                    if (pCount > 0) {
                        if (text.substring(thisIndex).matches( "^\\)\\.[A-Za-z_\\u4e00-\\u9fa5$]+.*")) {
                            pCount--;
                            continue;
                        }
                        thisIndex++;
                    }
                }
                list.add(StringRange.of(1, startI, thisIndex));
                startI = thisIndex;
                selectModel = -1;
                continue;
            }
            if (ch == '$' && !(text.charAt(thisIndex - 1) == '\\' && text.charAt(thisIndex - 2) == '\\')
                    && String.valueOf(text.charAt(thisIndex + 1)).matches( "[A-Za-z_\\u4e00-\\u9fa5{$]{1}")) {
                if (thisIndex - startI != 0)
                    list.add(StringRange.of(0, startI, thisIndex));
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
            list.add(StringRange.of(selectModel > 0 ? 1 : 0, startI, text.length() - 1));
        return list;
    }

    public static String map2FormatString(String text, List<StringRange> ranges) {
        StringRange formatRange = null;
        StringBuilder stringBuilder = new StringBuilder();
        for (StringRange range : ranges) {
            if (range.codeStyle == 2) formatRange = range;
            else if (range.codeStyle == 0) {
                stringBuilder.append(text.substring(range.startIndex, range.endIndex));
            } else if (range.codeStyle == 1) {
                if (formatRange == null) {
                    stringBuilder.append( "%s");
                } else {
                    stringBuilder.append(text.substring(formatRange.startIndex, formatRange.endIndex));
                    formatRange = null;
                }
            }
        }
        return stringBuilder.toString();
    }

    public static class StringRange {
        static StringRange of(int codeStyle, int startIndex, int endIndex) {
            StringRange stringRange = new StringRange();
            stringRange.endIndex = endIndex;
            stringRange.codeStyle = codeStyle;
            stringRange.startIndex = startIndex;
            return stringRange;
        }

        /**
         * -1 ignore space <br>
         * 0 hard String  <br>
         * 1 java code<br>
         * 2 format info
         */
        int codeStyle = -1;
        int startIndex;
        int endIndex;
    }
}
