package test;

import zircon.ExMethod;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class TestExMethod {
    @ExMethod(cover = false)
    public static String add(String a, String b, String c) {
        System.out.println("success hook method:" + a + "+" + b + "+" + c + "=" + (a + b + c));
        return a + b + c;
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
    @ExMethod(ex = {PrintStream.class},cover = true)
    public static void println(String a) {
        System.out.print("\n ex:  success hook static method:" + a + "=" + (a));
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
        System.out.println("success toBoolean:" + a + "," + b );
        return !Objects.equals(a,0);
    }

    @ExMethod
    public static <T> String add(String test, T a, T b) {
        System.out.println("success hook method:" + a + "+" + b + "=");
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
    public static <T> T addList(T[] test, T a) {
        System.out.println("success hook Array:" + a + "=" + (a));
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
    public static String add(String a, String b) {
        System.out.println("success hook method:" + a + "+" + b + "=" + (a + b));
        return a + b;
    }

    @ExMethod
    public static String add(String a) {
        System.out.println("success hook method: =" + a);
        return a;
    }
}
