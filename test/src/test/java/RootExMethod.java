import test.TestExMethod;
import zircon.ExMethod;

public class RootExMethod {
    @ExMethod
    public static void testSameMethod1(Object object,Number arg0, String arg1) {
        TestExMethod.methodNames.add("RootExMethod,testSameMethod1");
    }

    @ExMethod
    public static void testSameMethod2(Object object,Number arg0, String arg1) {
        TestExMethod.methodNames.add("RootExMethod,testSameMethod2");
    }
}
