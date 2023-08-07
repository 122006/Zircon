package test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestClass2 {
    public static void main(String[] args) {
        System.out.println(("2131" + "12312").concat("123"));
        System.out.println(Test.addStatic("test", "test2"));
        System.out.println(new Test().add("test", 2));
        System.out.println(new TestChildren().add("test", 2));
        System.out.println(("2131" + "12312").add("123"));
        System.out.println(("2131".toString()).add("123", "456"));
        System.out.println(("2131").add("123", BigDecimal.ZERO));
        System.out.println("2131".add("12").add("123type2").add("123type3"));
        System.out.println("2131".add(1));
        System.out.println(Integer.addStatic("a"));
        ("2131" + "12312").add("123");
        Function<String, String> function = a -> Integer.addStatic(a);
        function.apply("function");
        (new ArrayList<String>()).addList("1");
//        final List<String> collect = Stream.of("12", "13").map(String::add).collect(Collectors.toList());
//        System.out.println("lambda="+collect);
    }

    private static String apply(String a) {
        return a.add("12");
    }

    public static class TestChildren extends Test{

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

    }


    public static class Test2 {
        public static String add23(String a, String b) {
            return a + b;
        }
    }

}
