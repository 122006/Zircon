package com.sun.tools.javac.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZrConstants {
    public static List<String> exMethodIgnorePackages = new ArrayList<>();

    static {
        exMethodIgnorePackages.addAll(Arrays.asList("com.sun", "sun.", "jdk.", "org.junit.", "java.", "androidx.", "javax.", "org.junit.", "junit.", "kotlin."));
    }
}
