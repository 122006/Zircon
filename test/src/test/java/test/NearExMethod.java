package test;

import zircon.ExMethod;

public class NearExMethod {
    @ExMethod
    public static void testSameMethod1(Object object,Number arg0, String arg1) {
        TestExMethod.methodNames.add("NearExMethod,testSameMethod1");
    }

    @ExMethod
    public static void testSameMethod2(Object object,Number arg0, String arg1) {
        TestExMethod.methodNames.add("NearExMethod,testSameMethod2");
    }
}
