package test;

import com.by122006.jsf.Magic;
import org.junit.Test;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.by122006.jsf.Magic.$;

public class TextStringFormat {


    public static void main(String[] str) {
        {
            String test = $("tes");
            String will = "tes";
            assertEquals(test, will);
        }
        {
            String add = "test";
            String test = $("test ${add}");
            String will = $("test ", add);
            assertEquals(test, will);
        }
        {
            String test = $("test ${1+2}");
            String will = $("test ", 1+2);
            assertEquals(test, will);
        }
        {
            String test = $("test ${1+2}${2+3}");
            String will = $("test ", 1+2,5);
            assertEquals(test, will);
        }
        {
            String test = $("test ${Integer.valueOf(12)}${2+3}");
            String will = $("test ", Integer.valueOf(12),5);
            assertEquals(test, will);
        }
        {
            String test = $("test ${\"inStr\"}");
            String will = $("test ", "inStr");
            assertEquals(test, will);
        }
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
        String add = "test";
        String test = $("1234 ${add}");
        String will = $("1234 ", add);
        assert Objects.equals(test, will);
    }


}
