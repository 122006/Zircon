package test;

import org.junit.Ignore;

import java.util.function.Function;
public class TestClass2 {
    @Ignore
    public static void main(String[] args) {
        System.out.println("2131".add("123"));
    }
    public static void a(){
        String a="";
        String b=(a+null).concat("12");
    }
    public static void b(){
        String a="";
        String b=Test.add(a+null,"12");
    }
    public static void c(){
        String a="";
        Function function= obj -> Test.add(a+null,"12");
    }
    public static void d(){
        String b="";
        Function<String,String> function= a->a.concat("12");
    }
    public static class Test{
        @ExMethod
        public static String add(String a,String b){
            return a+b;
        }
    }
    public static class Test2{
        public static String add2(String a,String b){
            return a+b;
        }
    }
    @interface ExMethod{

    }
}
