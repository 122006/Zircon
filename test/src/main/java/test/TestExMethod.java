package test;

import zircon.ExMethod;

import java.math.BigDecimal;
import java.util.List;

public class TestExMethod {
    @ExMethod
    public static String add(String a, String b, String c) {
        System.out.println("success hook method:" + a + "+" + b + "+" + c + "=" + (a + b + c));
        return a + b + c;
    }
    @ExMethod
    public static String add(String a, String b, BigDecimal c) {
        System.out.println("success hook method:" + a + "+" + b + "+" + c + "=" + (a + b + c));
        return a + b + c;
    }

    @ExMethod(ex = {Integer.class, Long.class})
    public static String addStatic(String a) {
        System.out.println("success hook static method:" + a + "=" + (a));
        return a;
    }

    @ExMethod(ex = {TestClass2.Test.class})
    public static String addStatic(String a, String b) {
        System.out.println("success hook static method:" + a + "=" + (a));
        return a;
    }
    @ExMethod
    public static String add(TestClass2.Test test,String a, int b) {
        System.out.println("success hook method:" + a + "+" + b + "=" + (a + b));
        return a + b;
    }
    @ExMethod
    public static String addList(List<String> test, String a) {
        System.out.println("success hook method:" + a  + "=" + (a));
        return a ;
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
