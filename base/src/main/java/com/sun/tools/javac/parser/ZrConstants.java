package com.sun.tools.javac.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZrConstants {
    public static List<String> exMethodIgnorePackages = new ArrayList<>();

    static {
        final String zrExMethodIgnorePackages = System.getenv().get("zr_igp");
        if (zrExMethodIgnorePackages != null) {
            System.out.println("use env property [zr_igp]:" + zrExMethodIgnorePackages);
            exMethodIgnorePackages.addAll(Arrays.asList(zrExMethodIgnorePackages.split(",")));
        } else {
            exMethodIgnorePackages.addAll(Arrays.asList("org.graalvm.", "com.sun.", "org.springframework.", "org.apache.", "sun.", "jdk.", "org.junit.", "java.", "androidx.", "javax.", "org.junit.", "junit.", "kotlin."));
        }
    }
}
