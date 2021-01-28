package test;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TextStringFormat {


    public static void main(String[] str){
        String add="test";
        String las=$("1234 ${add}");
        String las2=$("1234 ",12);
        System.out.println(las);
    }
    @Test
    public void test1(){

    }


    private static String $(Object... s) {
        return Stream.of(s).map(String::valueOf).collect(Collectors.joining());
    }
}
