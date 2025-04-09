package com.sun.tools.javac.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ZrStringModel that = (ZrStringModel) o;

        if (endQuoteIndex != that.endQuoteIndex) return false;
        if (!Objects.equals(list, that.list)) return false;
        if (!Objects.equals(formatter, that.formatter)) return false;
        return Objects.equals(originalString, that.originalString);
    }

    @Override
    public int hashCode() {
        int result = list != null ? list.hashCode() : 0;
        result = 31 * result + (formatter != null ? formatter.hashCode() : 0);
        result = 31 * result + (originalString != null ? originalString.hashCode() : 0);
        result = 31 * result + endQuoteIndex;
        return result;
    }
}
