package test;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class TextStringFormat {
    static String test = "532";


    public static void main(String[] str) {
        {
            String test = f"tes";
            String will = "tes";
            assertEquals(test, will);
        }
        {
            String add = "test";
            String test = f"fwe ${add}";
            String will = "fwe test";
            assertEquals(test, will);
        }
        {
            String test = f"gr ${System.currentTimeMillis()/10}";
            String will = "gr " + System.currentTimeMillis() / 10;
            assertEquals(test, will);
        }
        {
            String test = f"测试 [${1+2}]";
            String will = f"测试 [3]";
            assertEquals(test, will);
        }

        {
            String add = "test2";
            String test = $"test $add test3";
            String will = "test test2 test3";
            assertEquals(test, will);
        }
        {
            String test = f"test ${1+2}${2+3}";
            String will = "test 35";
            assertEquals(test, will);
        }
        {
            String test = f"test ${\"inStr\"}";
            String will = "test inStr";
            assertEquals(test, will);
        }
        String add = "test";
        assertEquals( f"do ($add)" , "do (test)");
        assertEquals( f"do ($add)" , "do (test)");
        assertEquals( f"do ($TextStringFormat.test)" , "do (" + TextStringFormat.test + ")");
        assertEquals( f"test (${1+2})" , "test (3)");
        assertEquals( f"test (${String.format(\"str:[%s]\",\"format\")})" , "test (" + String.format( "str:[%s]" , "format") + ")");
        assertEquals( f"test (${String.format(\"str:[%s]\",\"format\")})" , "test (" + String.format( "str:[%s]" , "format") + ")");
        assertEquals( f"${\"Test,mode\".substring(0,6)} end" , "Test,mode".substring(0, 6) + " end");
        assertEquals( f"test (${\"te\\nst\"})" , "test (te\nst)");
        assertEquals( f"test\n (${\"te\\nst\"})" , "test\n (te\nst)");
        assertEquals( f"test (${\"te\\\"st\"})" , "test (te\"st)");
        assertEquals( f"(${\"test\"})" , "(test)");
        assertEquals( f"({${add}})" , "({test})");
        assertEquals( f"({${%03d:12}})" , "({012})");
        assertEquals( $"test (${String.valueOf(\"123\")})" , "test (123)");
        assertEquals( $"test (${String.valueOf('123')})" , "test (123)");
        assertEquals( f"(${})" , "()");
        assertEquals( f"${}" , "");
        assertEquals( f"" , "");
        assertEquals( f"(${1})" + $"(${2})" +f"(${3})" , "(1)(2)(3)");
        assertEquals( f"${')'}" , ")");
        assertEquals( f"${'('}" , "(");
        assertEquals( $"(${1})" + $"(${2})(${3})" , "(1)(2)(3)");
        assertEquals( f"(${1})" + f"(${2})(${3})" , "(1)(2)(3)");
        assertEquals(
                $"(${1})"
                                + $"(${2})"
                        +
                        $"(${3})" , "(1)(2)(3)");
        assertEquals( f"$$12()" , $12());
        assertEquals( f"$ $" , "$ $");
        assertEquals( f"$.$" , "$.$");
        assertEquals( f"$String.valueOf(add).length()" , "4");
        assertEquals( f"   $" , "   $");
        assertEquals( f"${\"\\\\n\\n\"}" , "\\n\n");
        assertEquals( f"normal \\$char" , "normal $char");
        assertEquals( f"normal \\" , "normal \\");
        assertEquals( f"normal \\$" , "normal $");
//        assertEquals(f"normal \\\\$", "normal \\$");
        assertEquals( f"$add@" , "test@");
        assertEquals( f"\r" , "\r");
        assertEquals( f"\\normal" , "\\normal");
        assertEquals( f"\normal" , "\normal");
        assertEquals( $"\n\\$normal" , "\n$normal");
        assertEquals( f"\\" , "\\");
        assertEquals( f"\\" , "\\");
        assertEquals( f"\n${add}" , "\ntest");
        assertEquals( f"\\${add}" , "${add}");
        assertEquals( f"\\n${add}" , "\\ntest");
        assertEquals(f"\\n${add}", "\\ntest");
        assertEquals( f"${1==1?'通过':\"驳回\"}" , "通过");

        assertEquals( f"审批${0==0?\"通过\":'驳回'} [${add}]\n $add" , "审批通过 [test]\n test");
        assertEquals( f"$add" + (2 + 3) + f"$add" + 1 + f"$add" , "test5test1test");
        assertEquals( f"$add ${add}" , "test test");
        assertEquals( f"${String.valueOf('testString')}" , "testString");
        assertEquals( f"${'testString'}" , "testString");
        assertEquals( f"${(int)\'s\'}" , "" + (int) 's');
        assertEquals( f"${''+'123'+String.valueOf(\'C\')+''}" , "123C");
        assertEquals( f"${add}(${add})" , "test(test)");


        String text=f" this is F-$String.class.getSimpleName() ";

        assert Objects.equals($"Zircon: [ ${text.trim()} ]","Zircon: [ "+text.trim()+" ]");



//        {
//            String test = f"test ${\"in\\\"Str\"}";
//            String will = f"test ", "in  \"Str";
//            assertEquals(test, will);
//        }
    }

    private static void assertEquals(String test, String will) {
        if (Objects.equals(test, will)) {
            System.out.println( f"测试通过: [${will}]");
            return;
        }
        System.err.println( "测试未通过：");
        System.err.println( f"test: [${test}]");
        System.err.println( f"will: [${will}]");
        new RuntimeException( "测试未通过").printStackTrace();
    }

    public static String $12(){
        return "12";
    }


    @Test
    public void test1() {


        String string = "world";

        String will = f"hello ${string}";


        assert Objects.equals(will, "hello world");
    }


}