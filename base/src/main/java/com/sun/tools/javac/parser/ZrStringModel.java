package com.sun.tools.javac.parser;

import java.util.ArrayList;
import java.util.List;

public class ZrStringModel {
    List<StringRange> list= new ArrayList<StringRange>();
    Formatter formatter;
    String originalString;
    int endQuoteIndex=-1;

    public List<StringRange> getList() {
        return list;
    }

    public void setList(List<StringRange> list) {
        this.list = list;
    }

    public Formatter getFormatter() {
        return formatter;
    }

    public void setFormatter(Formatter formatter) {
        this.formatter = formatter;
    }

    public String getOriginalString() {
        return originalString;
    }

    public void setOriginalString(String originalString) {
        this.originalString = originalString;
    }

    public int getEndQuoteIndex() {
        return endQuoteIndex;
    }

    public void setEndQuoteIndex(int endQuoteIndex) {
        this.endQuoteIndex = endQuoteIndex;
    }
}
