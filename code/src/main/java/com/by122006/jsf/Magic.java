package com.by122006.jsf;


public class Magic {
    public static String $(String s) {
        return s;
    }
    public static String $(Object... s) {
        if (s.length==0) return "";
        if (s.length==1) return String.valueOf(s[0]);
        StringBuilder stringBuilder=new StringBuilder();
        for(Object o:s){
            stringBuilder.append(o);
        }
        return stringBuilder.toString();
    }
}
