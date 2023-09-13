package test;

import zircon.ExMethod;
import zircon.example.ExArray;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestExMethod {
    @ExMethod(cover = false)
    public static String add(String a, String b, String c) {
        System.out.println("success hook method:" + a + "+" + b + "+" + c + "=" + (a + b + c));
        return a + b + c;
    }

    @ExMethod(cover = true)
    public static boolean isEmpty(String a) {
        return a == null || a.length() == 0;
    }

    @ExMethod
    public static boolean isEmpty(String a, int b) {
        throw new RuntimeException("no this method");
    }

    @ExMethod
    public static String add(String a, String b, BigDecimal c) {
        System.out.println("success hook method:" + a + "+" + b + "+" + c + "=" + (a + b + c));
        return a + b + c;
    }

    @ExMethod(ex = {Integer.class, Long.class, TestClass2.Test.class})
    public static String addStatic(String a) {
        System.out.println("success hook static method:" + a + "=" + (a));
        return a;
    }

    @ExMethod(ex = {Integer.class, Long.class, TestClass2.Test.class})
    public static String addStatic(Integer a) {
        System.out.println("success hook static method:" + a + "=" + (a));
        return "";
    }

    @ExMethod(ex = {TestClass2.Test.class})
    public static String addStatic(String a, String b) {
        System.out.println("success hook static method:" + a + "=" + (a));
        return a;
    }
    @ExMethod(cover = true)
    public static void println(PrintStream out, String a) {
        System.out.print("\n ex:  success hook static method:" + a);
    }

    @ExMethod(ex = {PrintStream.class}, cover = true)
    public static void println(Integer a) {
        System.out.print("\n ex:  success hook static method:" + a);
    }

    @ExMethod(ex = {PrintStream.class}, cover = true)
    public static void println(Integer a, String b) {
        System.out.print("\n ex:  success hook static method:" + a);
    }

    @ExMethod(ex = {TestClass2.Test.class})
    public static String addStatic(String a, Integer b) {
        System.out.println("success hook static method:" + a + "=" + (a));
        return a;
    }

    @ExMethod
    public static String add(TestClass2.Test test, String a, int b) {
        System.out.println("success hook method:" + a + "+" + b + "=" + (a + b));
        return a + b;
    }

    @ExMethod
    public static boolean toBoolean(Number a, int b) {
        System.out.println("success toBoolean:" + a + "," + b);
        return !Objects.equals(a, 0);
    }

    @ExMethod
    public static <T> String add(String test, T a, T b) {
        System.out.println("" + "success hook method:" + a + "+" + b + "=");
        return String.valueOf(a);
    }

    @ExMethod
    public static <T> T addList(List<String> test, T a) {
        System.out.println("success hook List:" + a + "=" + (a));
        return a;
    }

    @ExMethod
    public static <T extends String> T addList(List<String> test, T a, T b) {
        System.out.println("success hook List:" + a + "=" + (a));
        return a;
    }


    @ExMethod
    public static String add(Integer a, String b) {
        System.out.println("success hook method:" + a + "+" + b + "=" + (a + b));
        return a + b;
    }

    @ExMethod
    public static String add(String a, int b) {
        System.out.println("success hook method:" + a + "+" + b + "=" + (a + b));
        return a + b;
    }

    @ExMethod
    public static String add3(String a) {
        System.out.println("success hook method: =" + a);
        return a;
    }

    @ExMethod
    public static String add3(Integer a) {
        System.out.println("success hook method: =" + a);
        return "";
    }

    @ExMethod
    public static String add3(Integer a, String b) {
        System.out.println("success hook method: =" + a);
        return "";
    }

    @ExMethod(ex = {String.class})
    public static String add2(String a) {
        System.out.println("success hook method: =");
        return "";
    }

    @ExMethod(ex = {String.class})
    public static String add2(Integer a) {
        System.out.println("success hook method: =");
        return "";
    }

    @ExMethod(ex = {String.class})
    public static <T> String add2(T a) {
        System.out.println("success hook method: =");
        return "";
    }

    @ExMethod(ex = {String.class}, cover = true)
    public static String add2(Double a) {
        System.out.println("success hook method: =");
        return "";
    }

    @ExMethod
    public static Integer toInteger(String str) {
        if (str == null) return null;
        return Integer.valueOf(str);
    }

    @ExMethod
    public static String add(String a, Object... b) {
        for (Object o : b) {
            a += o;
        }
        return a;
    }


}
