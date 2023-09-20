package test;

import zircon.ExMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyPair;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TestExMethod {
    static List<String> methodNames = new ArrayList<>();

    @ExMethod
    public static void emptyStringRVoid(String str) {
        methodNames.add("emptyStringRVoid(String");
    }

    @ExMethod
    public static String emptyStringRString(String str) {
        methodNames.add("emptyStringRString(String");
        return str;
    }

    @ExMethod
    public static void emptyStringArrayRVoid(String[] str) {
        methodNames.add("emptyStringArrayRVoid([String");
    }

    @ExMethod
    public static String emptyStringArrayRString(String[] str) {
        methodNames.add("emptyStringArrayRString([String");
        return str[0];
    }

    @ExMethod
    public static String[] emptyStringArrayRStringArray(String[] str) {
        methodNames.add("emptyStringArrayRStringArray([String");
        return str;
    }

    @ExMethod
    public static String[] emptyStringArrayRStringArray1(String[] str) {
        methodNames.add("emptyStringArrayRStringArray1([String");
        return str;
    }

    @ExMethod
    public static String[] emptyStringArrayRStringArray2(String[] str) {
        methodNames.add("emptyStringArrayRStringArray2([String");
        return str;
    }

    @ExMethod
    public static String string2ArrayRString(String str, String str2) {
        methodNames.add("string2ArrayRString(String,String");
        return str;
    }

    @ExMethod
    public static String fatherStringRString(FatherClass father, String str) {
        methodNames.add("fatherStringRString(String");
        return str;
    }

    @ExMethod(ex = FatherClass.class)
    public static String staticFatherStringRString(String str) {
        methodNames.add("staticFatherStringRString(String");
        return str;
    }

    @ExMethod(ex = {FatherClass.class})
    public static Integer staticFatherIntegerRInteger(Integer integer) {
        methodNames.add("staticFatherIntegerRInteger(Integer");
        return integer;
    }

    @ExMethod(ex = {FatherClass.class})
    public static <T> T staticFatherTRT(T t) {
        methodNames.add("staticFatherTRT(t");
        return t;
    }

    @ExMethod
    public static <T> T fatherTRT(FatherClass fatherClass, T t) {
        methodNames.add("fatherTRT(fatherClass,t");
        return t;
    }

    @ExMethod
    public static <T extends FatherClass> T fatherSingleTRT(FatherClass fatherClass) {
        methodNames.add("fatherSingleTRT(t");
        return (T) fatherClass;
    }

    @ExMethod
    public static <T, M extends FatherClass> T fatherMExtendRT(M fatherClass, T t) {
        methodNames.add("fatherMExtendRT(t");
        return t;
    }

    @ExMethod(ex = {FatherClass.class})
    public static <T, M extends FatherClass> T staticFatherMExtendRT(T t) {
        methodNames.add("staticFatherMExtendRT(t");
        return t;
    }

    @ExMethod
    public static <T, M extends FatherClass> void fatherMExtendRV(M fatherClass, T t) {
        methodNames.add("fatherMExtendRT(t");
    }

    @ExMethod
    public static <T, M extends FatherClass> T fatherMExtendRT(M fatherClass, T t, String str) {
        methodNames.add("fatherMExtendRT(t,str");
        return t;
    }

    @ExMethod
    public static <M extends FatherClass> void fatherSingleMExtendRV(M fatherClass) {
        methodNames.add("fatherSingleMExtendRV(fatherClass");
    }

    @ExMethod(ex = {FatherClass.class})
    public static <T, M extends FatherClass> void staticFatherMExtendRV(T t) {
        methodNames.add("staticFatherMExtendRV(t");
    }

    @ExMethod(ex = {FatherClass.class})
    public static <T extends FatherClass, M extends FatherClass> void staticFatherTExtendRV(T t, String a) {
        methodNames.add("staticFatherTExtendRV(t,str");
    }

    @ExMethod(ex = {FatherClass.class})
    public static <T extends FatherClass, M> M staticFatherTExtendRM(T t, M m) {
        methodNames.add("staticFatherTExtendRM(t,m");
        return m;
    }

    @ExMethod(ex = {FatherClass.class})
    public static <T extends FatherClass, M> M staticFatherTExtendRM(T t) {
        methodNames.add("staticFatherTExtendRM(t");
        return (M) "";
    }

    @ExMethod(ex = {FatherClass.class})
    public static <T extends FatherClass, M> M staticFatherTExtendRM(T t, M m, String a) {
        methodNames.add("staticFatherTExtendRM(t,m,a");
        return m;
    }

    @ExMethod
    public static <T extends FatherClass> T fatherTArrayExtendRT(T[] fatherClass, T t) {
        methodNames.add("fatherTArrayExtendRT([t");
        return t;
    }

    @ExMethod
    public static <T extends FatherClass> T fatherTArrayExtendArrayRT(T[] fatherClass, T... t) {
        methodNames.add("fatherTArrayExtendArrayRT([t" + t.length);
        return t[0];
    }

    @ExMethod
    public static <T extends FatherClass> T fatherTArrayExtendArrayRT(T[] fatherClass, String t) {
        methodNames.add("fatherTArrayExtendArrayRT([t,t");
        return fatherClass[0];
    }

    public static class ChildEnv {
        @ExMethod(ex = {ChildClass.class})
        public static String staticSameNameExtendClass() {
            methodNames.add("[ChildClass]staticSameNameExtendClass(");
            return "ChildClass";
        }

        @ExMethod
        public static String sameNameExtendClass(ChildClass childClass) {
            methodNames.add("[ChildClass]sameNameExtendClass(");
            return "ChildClass";
        }
    }

    @ExMethod(ex = {FatherClass.class})
    public static String staticSameNameExtendClass() {
        methodNames.add("[FatherClass]staticSameNameExtendClass(");
        return "FatherClass";
    }

    @ExMethod
    public static String objectArraySiteCheckRS(Integer[] array) {
        methodNames.add("arraySiteCheckRS([Integer");
        return "arraySiteCheckRS";
    }

    @ExMethod
    public static String objectArraySiteCheckRS(Integer array) {
        methodNames.add("arraySiteCheckRS(Integer");
        return "arraySiteCheckRS";
    }

    @ExMethod
    public static <T> T objectArraySiteCheckRS(Integer[] array, T str) {
        methodNames.add("arraySiteCheckR([Integer,t");
        return str;
    }

    @ExMethod
    public static <T> T objectArraySiteCheckRS(Integer array, T str) {
        methodNames.add("arraySiteCheckR(Integer,t");
        return str;
    }

    @ExMethod
    public static <T> T objectArraySiteCheckRS(int[] array, T str) {
        methodNames.add("arraySiteCheckR([int,t");
        return str;
    }

    @ExMethod
    public static <T> T objectArraySiteCheckRS(int array, T str) {
        methodNames.add("arraySiteCheckR(int,t");
        return str;
    }
    @ExMethod(ex = {Object.class})
    public static <T> T supplier(Supplier<T> supplier) {
        methodNames.add("supplier(");
        return supplier.get();
    }

    @ExMethod
    public static Integer toInteger(String integer) {
        methodNames.add("toInteger(s");
        return Integer.parseInt(integer);
    }

    @ExMethod
    public static boolean isNull2(Object obj) {
        return obj == null;
    }
    @ExMethod(cover = true)
    public static boolean isEmpty(String str) {
        methodNames.add("isEmpty(s");
        return str == null || str.length() == 0;
    }

    @ExMethod(ex = {FatherClass.class})
    public static FatherClass createNew() {
        methodNames.add("staticFatherCreateNew(");
        return new FatherClass();
    }


    public static class FatherClass {
        @Deprecated
        public static void testStaticMethod() {
        }

    }

    public static class ChildClass extends FatherClass {

    }

    @ExMethod
    public static String sameNameExtendClass(FatherClass fatherClass) {
        methodNames.add("[FatherClass]sameNameExtendClass(");
        return "FatherClass";
    }

//    public static class TestClass<E> {
//        public <M extends E> E a(Function<List<E>, ?> function) {
//            return null;
//        }
//    }

    @ExMethod
    public static void checkMethodInvokes(Object object, Runnable runnable1, Runnable runnable2) {
        methodNames.clear();
        runnable1.run();
        final String collect = String.join("\n=>\n", methodNames);
        methodNames.clear();
        runnable2.run();
        final String collect2 = String.join("\n=>\n", methodNames);
        if (!Objects.equals(collect, collect2)) {
            final AssertionError assertionError = new AssertionError("\nv1:\n" + collect + "\n-----------\nv2:\n" + collect2 + "\n");
            errorSave.add(assertionError.getMessage() + "\n" + assertionError.getStackTrace()[1]);
        }
    }

    public static List<String> errorSave = new ArrayList<>();

    @ExMethod
    public static void testEnd(Object object) {
        if (errorSave.isEmpty()) {
            System.out.println("测试成功");
            return;
        }
        System.err.println(errorSave.size() + "个测试用例失败！");
        for (int i = 0; i < errorSave.size(); i++) {
            System.err.println("=========================");
            String s = errorSave.get(i);
            System.err.println("#" + i + " error:\n" + s);
        }
        final RuntimeException exception = new RuntimeException(errorSave.size() + "个测试用例失败！");
        exception.setStackTrace(new StackTraceElement[0]);
        throw exception;
    }

    @ExMethod
    public static <T> void checkMethodInvokes(Object object, Supplier<T> supplier, Supplier<T> supplier2) {
        methodNames.clear();
        final T t = supplier.get();
        final String collect = String.join("\n=>\n", methodNames);
        methodNames.clear();
        final T t2 = supplier2.get();
        final String collect2 = String.join("\n=>\n", methodNames);
        if (!Objects.equals(collect, collect2)) {
            final AssertionError assertionError = new AssertionError("\nv1:\n" + collect + "\n-----------\nv2:\n" + collect2 + "\n");
            errorSave.add(assertionError.getMessage() + "\n" + assertionError.getStackTrace()[1]);
        }
        if (!Objects.deepEquals(t, t2)) {
            final AssertionError assertionError = new AssertionError("\nt1:\n" + t + "\n-----------\nt2:\n" + t2 + "\n");
            errorSave.add(assertionError.getMessage() + "\n" + assertionError.getStackTrace()[1]);
        }
    }

}
