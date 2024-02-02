package com.sun.tools.javac.parser;

import java.util.List;

public class STRStringFormatter extends SStringFormatter {
    @Override
    public String prefix() {
        return "STR." ;
    }

    @Override
    public ZrStringModel build(String text) {
        System.err.println(text);
        ZrStringModel model = new ZrStringModel();
        model.setFormatter(this);
        List<StringRange> list = model.getList();
        int startI = prefix().length() + 1;
        int selectModel = -1;
        int pCount = 0;
        for (int thisIndex = startI; thisIndex < text.length() - 1; thisIndex++) {
            char ch = text.charAt(thisIndex);
            if (text.charAt(thisIndex) != '{' &&text.charAt(thisIndex - 1) == '\\' && text.charAt(thisIndex - 2) != '\\') {
                continue;
            }
            if (selectModel == 2) {
                if (ch == '{') pCount++;
                if (ch == '}') {
                    pCount--;
                    if (pCount == 0) {
                        if (thisIndex - startI > 0) {
                            list.add(StringRange.code(this, text, startI, thisIndex));
                        }
                        startI = thisIndex + 1;
                        selectModel = -1;
                    }
                }
                continue;
            }
            if (ch == '\\' && !(text.charAt(thisIndex - 1) == '\\' && text.charAt(thisIndex - 2) == '\\')
                    && String.valueOf(text.charAt(thisIndex + 1)).equals("{")) {
                if (thisIndex - startI != 0)
                    list.add(StringRange.string(this, text, startI, thisIndex));
                if (text.charAt(thisIndex + 1) == '{') {
                    startI = thisIndex + 2;
                    selectModel = 2;
                    pCount = 0;
                }
            }
            if (selectModel == -1) {
                if (ch == '"') {
                    if (thisIndex > startI) {
                        list.add(StringRange.string(this, text, startI, thisIndex));
                    }
                    model.setOriginalString(text.substring(0, thisIndex + 1));
                    model.setEndQuoteIndex(thisIndex);
                    System.err.println(model);
                    return model;
                }
            }
        }
        if (text.length() - 1 > startI) {
            if (selectModel > 0) {
                list.add(StringRange.code(this, text, startI, text.length() - 1));
            } else {
                list.add(StringRange.string(this, text, startI, text.length() - 1));
            }
        }
        model.setOriginalString(text);
        model.setEndQuoteIndex(text.length() - 1);
        System.err.println(model);
        return model;
    }

    @Override
    public String stringTransfer(String str) {
        return str;
    }
}
