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
        String add="test";
        assertEquals($("do ($add)"), "do (test)");
        assertEquals($("do ($add)"), "do (test)");
        assertEquals($("do ($TextStringFormat.test)"), "do ("+TextStringFormat.test+")");
        assertEquals($("test (${1+2})"), "test (3)");
        assertEquals($("test (${String.format(\"str:[%s]\",\"format\")})"), "test ("+String.format("str:[%s]","format")+")");
        assertEquals($("test ","(${String.format(\"str:[%s]\",\"format\")})"), "test ("+String.format("str:[%s]","format")+")");
        assertEquals($("${\"Test,mode\".substring(0,6)}"," end"), "Test,mode".substring(0,6)+" end");
        assertEquals($("test (${\"te\\nst\"})"), "test (te\nst)");
        assertEquals($("test\n (${\"te\\nst\"})"), "test\n (te\nst)");
        assertEquals($("test (${\"te\\\"st\"})"), "test (te\"st)");
        assertEquals($("(${\"test\"})"), "(test)");
        assertEquals($("({${add}})"), "({test})");
        assertEquals($(add+"("), "test(");
        assertEquals($(add+")"), "test)");
        assertEquals($(add+'('), "test(");
        assertEquals($(add+')'), "test)");
        assertEquals($(add+'{'), "test{");
        assertEquals($(add+'}'), "test}");
        assertEquals($(add+"(}${add}(}"+')'), "test(}test(}"+')');
        assertEquals($(add+')'+"}${add})}"+')'), "test)}test)}"+')');
        assertEquals($(add+"(}"), "test(}");
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
        assertEquals($("${\"\\\\n\\n\"}"), "\\n\n");
        assertEquals($("normal \\$char"), "normal $char");
        assertEquals($("normal \\"), "normal \\");
        assertEquals($("normal \\$"), "normal $");
//        assertEquals($("normal \\\\$"), "normal \\$");
        assertEquals($("$add@"), "test@");
        assertEquals($("\r"), "\r");
        assertEquals($("\\normal"), "\\normal");
        assertEquals($("\normal"), "\normal");
        assertEquals($("\n\\$normal"), "\n$normal");
        assertEquals($("\\"), "\\");
        assertEquals($("\\"), "\\");
        assertEquals($("\n${add}"), "\ntest");
        assertEquals($("\\${add}"), "${add}");
        assertEquals($("\\n${add}"), "\\ntest");
        assertEquals($(
                "\\n${add}"), "\\ntest");
        assertEquals($("${1==1?'通过':\"驳回\"}"), "通过");

        assertEquals($("审批${0==0?\"通过\":'驳回'} [${add}]\n $add"), "审批通过 [test]\n test");
        assertEquals($("$add"+(2+3)+"$add"+1+"$add"), "test5test1test");
        assertEquals($("$add ${add}"), "test test");
        assertEquals($("${String.valueOf('testString')}"), "testString");
        assertEquals($("${'testString'}"), "testString");
        assertEquals($("${(int)\'s\'}"), ""+(int)'s');
        assertEquals($("${''+'123'+String.valueOf(\'C\')+''}"), "123C");

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
