package test;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.by122006.zircon.Magic.$;


public class TextStringFormat {
    static String test="532";


    public static void main(String[] str) {
        {
            String test = $("tes");
            String will = "tes";
            assertEquals(test, will);
        }
        {
            String add = "test";
            String test = $("fwe ${add}");
            String will = "fwe test";
            assertEquals(test, will);
        }
        {
            String test = $("gr ${System.currentTimeMillis()/10}");
            String will = "gr " + System.currentTimeMillis() / 10;
            assertEquals(test, will);
        }
        {
            String test = $("测试 [${1+2}]");
            String will = $("测试 [", 1 + 2 + "]");
            assertEquals(test, will);
        }
        {
            String add = "test2";
            String test = $("test $add test3");
            String will = "test test2 test3";
            assertEquals(test, will);
        }
        {
            String test = $("test ${1+2}${2+3}");
            String will = "test 35";
            assertEquals(test, will);
        }
        {
            String test = $("test ${\"inStr\"}");
            String will = $("test ", "inStr");
            assertEquals(test, will);
        }
        String test="test";
        assertEquals($("do ($test)"), "do (test)");
        assertEquals($("do ($test)"), "do (test)");
        assertEquals($("do ($TextStringFormat.test)"), "do ("+TextStringFormat.test+")");
        assertEquals($("test (${1+2})"), "test (3)");
        assertEquals($("test (${String.format(\"str:[%s]\",\"format\")})"), "test ("+String.format("str:[%s]","format")+")");
        assertEquals($("test ","(${String.format(\"str:[%s]\",\"format\")})"), "test ("+String.format("str:[%s]","format")+")");
        assertEquals($("${\"Test,mode\".substring(0,6)}"," end"), "Test,mode".substring(0,6)+" end");
        assertEquals($("test (${\"te\\nst\"})"), "test (te\nst)");
        assertEquals($("test\n (${\"te\\nst\"})"), "test\n (te\nst)");
        assertEquals($("test (${\"te\\\"st\"})"), "test (te\"st)");
        assertEquals($("(${\"test\"})"), "(test)");
        assertEquals($("({${test}})"), "({test})");
        assertEquals($(test+"("), "test(");
        assertEquals($(test+")"), "test)");
        assertEquals($(test+'('), "test(");
        assertEquals($(test+')'), "test)");
        assertEquals($(test+'{'), "test{");
        assertEquals($(test+'}'), "test}");
        assertEquals($(test+"(}${test}(}"+')'), "test(}test(}"+')');
        assertEquals($(test+')'+"}${test})}"+')'), "test)}test)}"+')');
        assertEquals($(test+"(}"), "test(}");
        assertEquals($("(${})"), "()");
        assertEquals($("${}"), "");
        assertEquals($(), "");
        assertEquals($("(${1})"+"(${2})","(${3})"),"(1)(2)(3)");
        assertEquals($("${')'}"), ")");
        assertEquals($("${'('}"), "(");
        assertEquals($(
                "(${1})"+"(${2})","(${3})"),"(1)(2)(3)");
        assertEquals(
                $("(${1})"+"(${2})","(${3})"),"(1)(2)(3)");
        assertEquals(
                $("(${1})"
                        +"(${2})"
                        ,
                        "(${3})"
                ),"(1)(2)(3)");
        assertEquals($("${\"\\\\'\\'\"}"), "\\''");
//        {
//            String test = $("test ${\"in\\\"Str\"}");
//            String will = $("test ", "in  \"Str");
//            assertEquals(test, will);
//        }
    }

    private static void assertEquals(String test, String will) {
        if (Objects.equals(test, will)) {
            System.out.println($("测试通过: [${will}]"));
            return;
        }
        System.err.println("测试未通过：");
        System.err.println($("test: [${test}]"));
        System.err.println($("will: [${will}]"));
        new RuntimeException("测试未通过").printStackTrace();
    }


    @Test
    public void test1() {


        String string = "world";

        String will = $("hello ${string}");



        assert Objects.equals(will, "hello world");
    }


}
