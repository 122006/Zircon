package test;

import java.util.function.Function;

public class TestClass2 {
    public static void main(String[] args) {
        System.out.println(("2131" + "12312").concat("123"));
        System.out.println(Test.add("test", "test2"));
        System.out.println(("2131" + "12312").add("123"));
        System.out.println(("2131".toString()).add("123", "456"));
        System.out.println("2131".add("123"));
        ("2131" + "12312").add("123");

    }

    public static void a() {
        String a = "";
        String b = (a + null).concat("12");
    }

    public static void b() {
        String a = "";
//        String b=Test.add(a+null,"12");
    }

    public static void c() {
        String a = "";
//        Function function= obj -> Test.add(a+null,"12");
    }

    public static void d() {
        String b = "";
        Function<String, String> function = a -> a.concat("12");
    }

    public static class Test {
        @ExMethod
        public static String add(String a, String b) {
            return a + b;
        }
    }

    public static class Test2 {
        public static String add23(String a, String b) {
            return a + b;
        }
    }

    @ExMethod
    public static String add(String a, String b, String c) {
        System.out.println("success hook method:" + a + "+" + b + "+" + c + "=" + (a + b + c));
        return a + b;
    }

    @ExMethod
    public static String add(String a, String b) {
        System.out.println("success hook method:" + a + "+" + b + "=" + (a + b));
        return a + b;
    }

    @interface ExMethod {

    }
}
