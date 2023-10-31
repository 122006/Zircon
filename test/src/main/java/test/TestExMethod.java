package test;

import zircon.ExMethod;

import java.util.*;
import java.util.function.Supplier;

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
    public static <T> T objectTRT(T t) {
        methodNames.add("objectTRT(t");
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

//    @ExMethod
//    public static <T> T objectArraySiteCheckRS(Integer array, T str) {
//        methodNames.add("arraySiteCheckR(Integer,t");
//        return str;
//    }

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

    @ExMethod
    public static <T> List<T> listTRT(List<T> list, T item) {
        methodNames.add("listTRT(int,t");
        return list;
    }

    @ExMethod
    public static <M, N, V> List<V> hashMapRListV(HashMap<M, N> list, M key, N value, V other) {
        methodNames.add("hashMapRListV(h,m,n,v");
        return Arrays.asList(other);
    }

    @ExMethod
    public static <M, N, V, K> HashMap<V, K> hashMapRMapV(HashMap<M, N> list, M key, N value, V vk, K vk2) {
        methodNames.add("hashMapRMapV(h,m,n,v");
        final HashMap<V, K> vkHashMap = new HashMap<>();
        vkHashMap.put(vk, vk2);
        return vkHashMap;
    }
    @ExMethod
    public static <M, N, V, K> HashMap<V, K> hashMapRClassMapV(HashMap<M, N> list, Class<M> key, Class<N> value, Class<V> vk, Class<K> vk2) {
        methodNames.add("hashMapRClassMapV(h,m,n,v");
        final HashMap<V, K> vkHashMap = new HashMap<>();
        return vkHashMap;
    }
    @ExMethod
    public static <M, N, V, K> HashMap<V, K> hashMapRClassMapV2(HashMap<?, N> list, Class<M> key, Class<?> value, Class<V> vk, Class<K> vk2) {
        methodNames.add("hashMapRClassMapV2(h,m,n,v");
        final HashMap<V, K> vkHashMap = new HashMap<>();
        return vkHashMap;
    }
    @ExMethod
    public static <M, N, V, K> HashMap<V, K> hashMapRClassMapV3(HashMap<?, N> list, Class<?> key, Class<N> value, Class<V> vk, Class<K> vk2) {
        methodNames.add("hashMapRClassMapV3(h,m,n,v");
        final HashMap<V, K> vkHashMap = new HashMap<>();
        return vkHashMap;
    }

    @ExMethod(ex = {Object.class})
    public static <T> T supplier(Supplier<T> supplier) {
        methodNames.add("supplier(");
        return supplier.get();
    }

    @ExMethod
    public static Integer toInteger2(String integer) {
        methodNames.add("toInteger2(s");
        return Integer.parseInt(integer);
    }
    @ExMethod
    public static Integer toInteger2(String integer,String str2) {
        methodNames.add("toInteger2(s,s2");
        return Integer.parseInt(integer);
    }

    @ExMethod
    public static boolean isNull2(Object obj) {
        return obj == null;
    }

    @ExMethod(cover = true)
    public static boolean isBlank(String str) {
        methodNames.add("isBlank(s");
        return str == null || str.length() == 0;
    }
    @ExMethod
    public static <T> T nullOr2(T obj, T or) {
        return obj == null ? or : obj;
    }


    @ExMethod
    public static <T> T testGenericTransformMethod(List<T> param1, T param2) {
        return param1.get(0);
    }
    @ExMethod
    public static <E> E testGenericTransformMethod2(List<E> param1, E param2) {
        return param1.get(0);
    }

    @ExMethod
    public static <T,R> R testGenericTransformMethod(HashMap<T,R> param1, T param2) {
        return param1.get(param2);
    }
    @ExMethod
    public static <T,R> Set<T> testGenericTransformMethodRSet(HashMap<T,R> param1, T param2) {
        return param1.keySet();
    }
    @ExMethod
    public static <T,R> Set<T> testGenericTransformMethodRSet(HashMap<T,R> param1, List<T> param2) {
        final Set<T> ts = param1.keySet();
        ts.addAll(param2);
        return ts;
    }
    @ExMethod
    public static <K,R> Set<K> testGenericTransformMethodRSet2(HashMap<K,R> param1, List<K> param2) {
        final Set<K> ts = param1.keySet();
        ts.addAll(param2);
        return ts;
    }
//    @ExMethod
//    public static <K,R> Set<K> testGenericTransformMethodRSet3(HashMap<List<K>,R> param1, List<K> param2) {
//        return null;
//    }

    @ExMethod(ex = {Arrays.class}, cover = true)
    public static <T> List<T> asList(T... strs) {
        methodNames.add("Arrays.asList(t");
        final ArrayList<T> ts = new ArrayList<>();
        Collections.addAll(ts, strs);
        return ts;
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

        public static void oSameNameExtendClass() {
        }

    }

    public static class ChildClass extends FatherClass {
        public static void oSameNameExtendClass() {
        }
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
        methodNames.clear();
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
