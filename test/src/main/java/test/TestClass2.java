package test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import zircon.BiOp;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestClass2 {
    @SpringBootApplication
    public static class GradleDemoApplication {
        public static void main(String[] args) {
            SpringApplication.run(GradleDemoApplication.class, args);
        }
    }

    public static void main(String[] args) {
//        String nullStr = null;
//        System.out.println(("2131" + "12312").concat("123"));
//        System.out.println(Test.addStatic("test", "test2"));
//        System.out.println(new Test().add("test", 2));
//        System.out.println(new TestChildren().add("test", 2));
//        System.out.println(("2131" + "12312").add("123"));
//        System.out.println(("2131".toString()).add("123", "456"));
//        System.out.println(("2131").add("123", BigDecimal.ZERO));
//        System.out.println("".isEmpty());
//        System.out.println(nullStr.isEmpty());
//        System.out.println("2131".add("12").add("123type2").add("4112").add(1).add("123type3"));
////        System.out.println("2131".add(1));
//        System.out.println(Integer.addStatic("a"));
//        System.out.println((Integer.valueOf("1")).addStatic("a"));
//        System.out.println(((Integer.valueOf("1") == null ? null : (Integer.valueOf("2")).addStatic("a"))).addStatic("a"));
//        (Test.getIntegerReturn("123")).addStaticRVoid("a");
//        System.out.println(java.lang.String.addStatic("a"));
////        System.out.println((Integer.valueOf("1")).toString(1,1));
//
//        Test testOi = new Test() {
//            public Object apply(Object b) {
//                return super.addForTest("123", 1);
//            }
//        };
//        ("2131" + "12312").add("123");
////        Function<String, String> function = a -> Integer.addStatic(a);
////        function.apply("function");
//        new ArrayList<String>().addList("12", "32");
//        System.out.println((new String[1]).add("123")[1]);
//        Integer testString = 456;
//        final List<String> collect2 = Stream.of(123, 543).map(Test::addStatic2).list();
//        Stream.of("1").map(TestExMethod::add3).collect(Collectors.toList());
//        Stream.of("1").map(String::add3).collect(Collectors.toList());
//        Stream.of("1").map(String::add2).collect(Collectors.toList());
////        System.out.println("lambda2=" + collect2);
//        BiFunction<String, Integer, ?> a = Test::addStatic;
//        System.out.println("lambda3=" + a.apply("12", 3));
//        final List<PrintStream> collect4 = Stream.of("122", "132").map(System.out::append).list();
//        System.out.println("lambda4=" + collect4);
////        Function<String, String> b = ""::addStatic;
////        System.out.println("test lambda b=" + b);
//        final Stream<String> stringStream = Stream.of("12", "13");
////        final Stream<Integer> mapStream = stringStream.map(""::toInteger);
////        final List<String> collect = mapStream.collect(Collectors.toList());
////        System.out.println("lambda1="+collect);
//        final Stream<String> mapStream2 = stringStream.map((String str) -> "concat".concat(str));
//        final List<String> collect5 = mapStream2.list();
//        System.out.println("lambda5=" + collect5);
//    }
//
//    private static String apply(String a) {
//        return a.add("12");
//    }
//
//    public static class TestChildren extends Test {
//        public static void testStaticMethod() {
//        }
//
//        public void test() {
//            TestChildren.testStaticMethod();
//        }
//    }
//
//
//    public static void a() {
//        String a = "";
//        String b = (a + null).concat("12");
//    }
//
//    public static void b() {
//        String a = "";
////        String b=Test.add(a+null,"12");
//    }
//
//    public static void c() {
//        String a = "";
////        Function function= obj -> Test.add(a+null,"12");
//    }
//
//    public static void d() {
//        String b = "";
//        Function<String, String> function = (String a) -> a.concat("12");
//    }
//
//    public static class Test {
//        public static void testStaticMethod() {
//        }
//
//    }
//
//
//    public static class Test2 {
//        public static String add23(String a, String b) {
//            return a + b;
//        }
    }

}
