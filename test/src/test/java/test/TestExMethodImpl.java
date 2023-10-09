package test;

import org.junit.jupiter.api.Test;
import test.TestExMethod;
import test.TestExMethod.ChildEnv;
import zircon.example.ExCollection;
import zircon.example.ExObject;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.function.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestExMethodImpl {
    @Test
    public void test() {
        checkMethodInvokes(
                () -> {
                    "123".emptyStringRString();
                }, () -> {
                    final String s = TestExMethod.emptyStringRString("123");
                    assertEquals(s, "123");
                });
        checkMethodInvokes(
                () -> {
                    "123".emptyStringRVoid();
                }, () -> {
                    TestExMethod.emptyStringRVoid("123");
                });
        checkMethodInvokes(
                () -> {
                    String[] strs = {"123"};
                    strs.emptyStringArrayRVoid();
                }, () -> {
                    String[] strs = {"123"};
                    TestExMethod.emptyStringArrayRVoid(strs);
                });
        checkMethodInvokes(
                () -> {
                    String[] strs = {"123"};
                    return strs.emptyStringArrayRString();
                }, () -> {
                    String[] strs = {"123"};
                    return TestExMethod.emptyStringArrayRString(strs);
                });
        checkMethodInvokes(
                () -> {
                    String[] strs = {"123"};
                    return strs.emptyStringArrayRStringArray();
                }, () -> {
                    String[] strs = {"123"};
                    return TestExMethod.emptyStringArrayRStringArray(strs);
                });
        checkMethodInvokes(
                () -> {
                    String[] strs = {"123"};
                    return strs.emptyStringArrayRStringArray().emptyStringArrayRStringArray1().emptyStringArrayRStringArray2();
                }, () -> {
                    String[] strs = {"123"};
                    return TestExMethod.emptyStringArrayRStringArray2(TestExMethod.emptyStringArrayRStringArray1(TestExMethod.emptyStringArrayRStringArray(strs)));
                });
        checkMethodInvokes(
                () -> "123".string2ArrayRString("456"),
                () -> TestExMethod.string2ArrayRString("123", "456"));

        TestExMethod.FatherClass fatherClass = TestExMethod.FatherClass.createNew();
        checkMethodInvokes(
                () -> fatherClass.fatherStringRString("456"),
                () -> TestExMethod.fatherStringRString(fatherClass, "456"));
        checkMethodInvokes(
                () -> TestExMethod.FatherClass.staticFatherStringRString("123"),
                () -> TestExMethod.staticFatherStringRString("123"));
        checkMethodInvokes(
                () -> TestExMethod.FatherClass.staticFatherIntegerRInteger(12),
                () -> TestExMethod.staticFatherIntegerRInteger(12));
        checkMethodInvokes(
                () -> fatherClass.staticFatherIntegerRInteger(12),
                () -> TestExMethod.staticFatherIntegerRInteger(12));

        checkMethodInvokes(
                () -> TestExMethod.FatherClass.createNew().staticFatherIntegerRInteger(12),
                () -> {
                    TestExMethod.createNew();
                    return TestExMethod.staticFatherIntegerRInteger(12);
                });
        checkMethodInvokes(
                () -> TestExMethod.FatherClass.createNew().createNew().staticFatherIntegerRInteger(12),
                () -> {
                    TestExMethod.createNew().createNew();
                    return TestExMethod.staticFatherIntegerRInteger(12);
                });
        checkMethodInvokes(
                () -> TestExMethod.FatherClass.createNew().fatherStringRString("123"),
                () -> {
                    final TestExMethod.FatherClass aNew = TestExMethod.createNew();
                    return TestExMethod.fatherStringRString(aNew, "123");
                });
        TestExMethod.ChildClass childClass = new TestExMethod.ChildClass();
        checkMethodInvokes(
                () -> childClass.fatherStringRString("123"),
                () -> TestExMethod.fatherStringRString(childClass, "123"));
        checkMethodInvokes(
                () -> childClass.fatherStringRString("456"),
                () -> TestExMethod.fatherStringRString(childClass, "456"));
        checkMethodInvokes(
                () -> TestExMethod.ChildClass.staticFatherStringRString("123"),
                () -> TestExMethod.staticFatherStringRString("123"));
        checkMethodInvokes(
                () -> TestExMethod.ChildClass.staticFatherIntegerRInteger(12),
                () -> TestExMethod.staticFatherIntegerRInteger(12));
        checkMethodInvokes(
                () -> childClass.staticFatherIntegerRInteger(12),
                () -> TestExMethod.staticFatherIntegerRInteger(12));
        checkMethodInvokes(
                () -> (new TestExMethod.ChildClass()).fatherStringRString("123"),
                () -> {
                    final TestExMethod.ChildClass aNew = (new TestExMethod.ChildClass());
                    return TestExMethod.fatherStringRString(aNew, "123");
                });
        checkMethodInvokes(
                () -> childClass.staticFatherTRT(12),
                () -> TestExMethod.staticFatherTRT(12));
        checkMethodInvokes(
                () -> "123".objectTRT(),
                () -> TestExMethod.objectTRT("123"));
        checkMethodInvokes(
                () -> childClass.fatherTRT(12),
                () -> TestExMethod.fatherTRT(childClass, 12));
        checkMethodInvokes(
                () -> childClass.staticFatherMExtendRT(12),
                () -> TestExMethod.staticFatherMExtendRT(12));
        checkMethodInvokes(
                () -> childClass.fatherMExtendRT(12),
                () -> TestExMethod.fatherMExtendRT(childClass, 12));
        checkMethodInvokes(
                () -> new TestExMethod.ChildClass().fatherMExtendRT(12),
                () -> TestExMethod.fatherMExtendRT(childClass, 12));
        checkMethodInvokes(
                () -> new TestExMethod.FatherClass[]{childClass}.fatherTArrayExtendRT(childClass),
                () -> TestExMethod.fatherTArrayExtendRT(new TestExMethod.FatherClass[]{childClass}, childClass));
        checkMethodInvokes(
                () -> new TestExMethod.FatherClass[]{childClass}.fatherTArrayExtendArrayRT(childClass, childClass, childClass, childClass),
                () -> TestExMethod.fatherTArrayExtendArrayRT(new TestExMethod.FatherClass[]{childClass}, childClass, childClass, childClass, childClass));
        checkMethodInvokes(
                () -> new TestExMethod.FatherClass[]{childClass}.fatherTArrayExtendArrayRT("123"),//数组调用动态拓展方法会丢弃掉泛型值，导致泛型退化为Object，但定义泛型数组可以方便推导其他参数
                () -> TestExMethod.fatherTArrayExtendArrayRT(new TestExMethod.FatherClass[]{childClass}, "123"));
        checkMethodInvokes(
                () -> {
                    return new TestExMethod.ChildClass() {
                        <T> T test() {
                            return (T) fatherTRT(123);
                        }
                    }.test();
                }, () -> TestExMethod.fatherTRT(childClass, 123));
        checkMethodInvokes(
                () -> {
                    return new TestExMethod.ChildClass() {
                        <T> T test() {
                            return (T) fatherMExtendRT(123);
                        }
                    }.test();
                }, () -> TestExMethod.fatherMExtendRT(childClass, 123)
        );
        checkMethodInvokes(
                () -> {
                    return new TestExMethod.ChildClass() {
                        <T> T test() {
                            return (T) staticFatherMExtendRT(123);
                        }
                    }.test();
                }, () -> TestExMethod.staticFatherMExtendRT(123)
        );
        checkMethodInvokes(
                () -> {
                    return new TestExMethod.ChildClass() {
                        <T> T test() {
                            return (T) createNew().fatherTRT(123);
                        }
                    }.test();
                }, () -> {
                    TestExMethod.createNew();
                    return TestExMethod.fatherTRT(childClass, 123);
                });
        checkMethodInvokes(
                () -> {
                    return new TestExMethod.ChildClass() {
                        <T> T test() {
                            return (T) createNew().fatherMExtendRT(123);
                        }
                    }.test();
                }, () -> {
                    TestExMethod.createNew();
                    return TestExMethod.fatherMExtendRT(childClass, 123);
                }
        );
        checkMethodInvokes(
                () -> {
                    return new TestExMethod.ChildClass() {
                        <T> T test() {
                            return (T) createNew().staticFatherMExtendRT(123);
                        }
                    }.test();
                }, () -> {
                    TestExMethod.createNew();
                    return TestExMethod.staticFatherMExtendRT(123);
                }
        );
        checkMethodInvokes(
                () -> {
                    new TestExMethod.ChildClass() {
                        void test() {
                            createNew().fatherTRT(123);
                            createNew().fatherMExtendRT(123);
                            createNew().staticFatherMExtendRT(123);
                        }
                    }.test();
                }, () -> {
                    TestExMethod.createNew();
                    TestExMethod.fatherTRT(childClass, 123);
                    TestExMethod.createNew();
                    TestExMethod.fatherMExtendRT(childClass, 123);
                    TestExMethod.createNew();
                    TestExMethod.staticFatherMExtendRT(123);
                });
        checkMethodInvokes(
                () -> {
                    new TestExMethod.ChildClass() {
                        void test() {
//                            createNew().staticFatherMExtendRV(123); //对实例对象调用无返回值的静态方法
                            createNew();
                            TestExMethod.staticFatherMExtendRV(123);
                            createNew().fatherMExtendRT(123);
                            createNew().fatherMExtendRT(123, "456");
                        }
                    }.test();
                }, () -> {
                    TestExMethod.createNew();
                    TestExMethod.staticFatherMExtendRV(123);
                    TestExMethod.createNew();
                    TestExMethod.fatherMExtendRT(childClass, 123);
                    TestExMethod.createNew();
                    TestExMethod.fatherMExtendRT(childClass, 123, "456");
                });
        checkMethodInvokes(
                () -> {
                    Function<String, String> a = TestExMethod.ChildClass::staticFatherMExtendRT;
                    return a.apply("123");
                }, () -> TestExMethod.ChildClass.staticFatherMExtendRT("123"));
        checkMethodInvokes(
                () -> {
                    Function<String, String> a = t -> TestExMethod.ChildClass.staticFatherMExtendRT(t);
                    return a.apply("123");
                }, () -> TestExMethod.ChildClass.staticFatherMExtendRT("123"));

        checkMethodInvokes(
                () -> {
                    Consumer<TestExMethod.ChildClass> a = TestExMethod.ChildClass::fatherSingleMExtendRV;
                    a.accept(childClass);
                }, () -> TestExMethod.fatherSingleMExtendRV(childClass));
        checkMethodInvokes(
                () -> {
                    Consumer<TestExMethod.ChildClass> a = t -> t.fatherSingleMExtendRV();
                    a.accept(childClass);
                }, () -> TestExMethod.fatherSingleMExtendRV(childClass));
        checkMethodInvokes(
                () -> {
                    Function<TestExMethod.ChildClass, TestExMethod.ChildClass> a = TestExMethod.FatherClass::fatherSingleTRT;
                    return a.apply(childClass);
                }, () -> TestExMethod.fatherSingleTRT(childClass));
        checkMethodInvokes(
                () -> {
                    Function<TestExMethod.ChildClass, TestExMethod.ChildClass> a = t -> t.fatherSingleTRT();
                    return a.apply(childClass);
                }, () -> TestExMethod.fatherSingleTRT(childClass));
        checkMethodInvokes(
                () -> {
                    BiFunction<TestExMethod.ChildClass, String, String> a = TestExMethod.ChildClass::staticFatherTExtendRM;
                    return a.apply(childClass, "123");
                }, () -> TestExMethod.staticFatherTExtendRM(childClass, "123"));
        checkMethodInvokes(
                () -> {
                    BiFunction<TestExMethod.ChildClass, String, String> a = (t, t2) -> TestExMethod.ChildClass.staticFatherTExtendRM(t, t2);
                    return a.apply(childClass, "123");
                }, () -> TestExMethod.staticFatherTExtendRM(childClass, "123"));
        checkMethodInvokes(
                () -> {
                    Function<TestExMethod.ChildClass, String> a = TestExMethod.ChildClass::staticFatherTExtendRM;
                    return a.apply(childClass);
                }, () -> TestExMethod.staticFatherTExtendRM(childClass));
        checkMethodInvokes(
                () -> {
                    Function<TestExMethod.ChildClass, String> a = (t) -> TestExMethod.ChildClass.staticFatherTExtendRM(t);
                    return a.apply(childClass);
                }, () -> TestExMethod.staticFatherTExtendRM(childClass));
        checkMethodInvokes(
                () -> {
                    BiConsumer<TestExMethod.ChildClass, String> a = (t, t2) -> TestExMethod.ChildClass.staticFatherTExtendRV(t, t2);
                    a.accept(childClass, "123");
                }, () -> TestExMethod.staticFatherTExtendRV(childClass, "123"));
        checkMethodInvokes(
                () -> TestExMethod.FatherClass.staticSameNameExtendClass(),
                () -> TestExMethod.staticSameNameExtendClass());
        checkMethodInvokes(
                () -> TestExMethod.ChildClass.staticSameNameExtendClass(),
                () -> TestExMethod.ChildEnv.staticSameNameExtendClass());
        checkMethodInvokes(
                () -> {
                    Supplier<String> supplier = TestExMethod.FatherClass::staticSameNameExtendClass;
                    return supplier.get();
                },
                () -> TestExMethod.staticSameNameExtendClass());
        checkMethodInvokes(
                () -> {
                    Supplier<String> supplier = TestExMethod.ChildClass::staticSameNameExtendClass;
                    return supplier.get();
                },
                () -> TestExMethod.ChildEnv.staticSameNameExtendClass());
        checkMethodInvokes(
                () -> {
                    Supplier<String> supplier = () -> TestExMethod.FatherClass.staticSameNameExtendClass();
                    return supplier.get();
                },
                () -> TestExMethod.staticSameNameExtendClass());
        checkMethodInvokes(
                () -> {
                    Supplier<String> supplier = () -> TestExMethod.ChildClass.staticSameNameExtendClass();
                    return supplier.get();
                },
                () -> TestExMethod.ChildEnv.staticSameNameExtendClass());
        checkMethodInvokes(
                () -> {
                    Supplier<String> supplier = () -> fatherClass.staticSameNameExtendClass();
                    return supplier.get();
                },
                () -> TestExMethod.staticSameNameExtendClass());
        checkMethodInvokes(
                () -> {
                    Supplier<String> supplier = () -> childClass.staticSameNameExtendClass();
                    return supplier.get();
                },
                () -> TestExMethod.ChildEnv.staticSameNameExtendClass());
        Integer[] integers = new Integer[0];
        int[] ints = new int[0];
        checkMethodInvokes(
                () -> integers.objectArraySiteCheckRS(),
                () -> TestExMethod.objectArraySiteCheckRS(integers));
        checkMethodInvokes(
                () -> integers.objectArraySiteCheckRS("123"),
                () -> TestExMethod.objectArraySiteCheckRS(integers, "123"));
        checkMethodInvokes(
                () -> ints.objectArraySiteCheckRS("123"),
                () -> TestExMethod.objectArraySiteCheckRS(ints, "123"));
        checkMethodInvokes(
                () -> {
                    return supplier(() -> "123");
                },
                () -> TestExMethod.supplier(() -> "123"));
        checkMethodInvokes(
                () -> {
                    return this.supplier(() -> "456");
                },
                () -> TestExMethod.supplier(() -> "456"));
        checkMethodInvokes(
                () -> "123".toInteger2(),
                () -> TestExMethod.toInteger2("123"));
        String nullStr = null;
        TestExMethod.ChildClass.oSameNameExtendClass();
        Function<String, Boolean> isNullFunc = String::isNull;
        checkMethodInvokes(
                () -> isNullFunc.apply(nullStr),
                () -> nullStr.isNull());
        checkMethodInvokes(
                () -> nullStr.isNull(),
                () -> TestExMethod.isNull2(nullStr));
        checkMethodInvokes(
                () -> Arrays.asList("123", "456", "789").find(a -> a.equals("123")),
                () -> zircon.example.ExCollection.find(Arrays.asList("123", "456", "789"), a -> a.equals("123")));
        String bString="456";
        checkMethodInvokes(
                () -> Arrays.asList("123", "456", "789").forEach((bString::toInteger2)),
                () -> Arrays.asList("123", "456", "789").forEach(t -> bString.toInteger2(t)));
        checkMethodInvokes(
                () -> Arrays.asList("123", "456", "789").forEach(( "456"::toInteger2)),
                () -> Arrays.asList("123", "456", "789").forEach(t -> "456".toInteger2(t)));
        checkMethodInvokes(
                () -> {
                    final Function<String,Integer> testLambda = bString::toInteger2;
                },
                () -> {
                    final Function<String,Integer> testLambda3 = str2 -> bString.toInteger2(str2);
                });
        checkMethodInvokes(
                () -> {
                    final Consumer<String> testLambda2 = (("456"::toInteger2));
                },
                () -> {
                    final Consumer<String> testLambda4 = ((str2) -> "456".toInteger2(str2));
                });
        zircon.example.ExObject.nullOr("31231", "123");
        checkMethodInvokes(
                () -> "31231".nullOr(123),
                () -> zircon.example.ExObject.nullOr("31231", "123"));
        checkMethodInvokes(
                () -> nullStr.isBlank(),
                () -> TestExMethod.isBlank(nullStr));
        checkMethodInvokes(
                () -> Arrays.asList(123, 456),
                () -> TestExMethod.asList(123, 456));
        checkMethodInvokes(
                () -> Arrays.asList("123", "456"),
                () -> TestExMethod.asList("123", "456"));
        List<String> str = Arrays.asList("123", "456");
        checkMethodInvokes(
                () -> str.listTRT("567"),
                () -> TestExMethod.listTRT(str, "567"));
        HashMap<String, Integer> hashMap = new HashMap<>();
        checkMethodInvokes(
                () -> hashMap.hashMapRListV("abc", 22, BigDecimal.ZERO),
                () -> TestExMethod.hashMapRListV(hashMap, "abc", 22, BigDecimal.ZERO));
        Date date = new Date();
        checkMethodInvokes(
                () -> hashMap.hashMapRMapV("abc", 22, date, BigDecimal.ZERO),
                () -> TestExMethod.hashMapRMapV(hashMap, "abc", 22, date, BigDecimal.ZERO));
        checkMethodInvokes(
                () -> hashMap.hashMapRClassMapV(String.class, int.class, Date.class, BigDecimal.class),
                () -> TestExMethod.hashMapRClassMapV(hashMap, String.class, int.class, Date.class, BigDecimal.class));
        checkMethodInvokes(
                () -> hashMap.hashMapRClassMapV3(String.class, int.class, Date.class, BigDecimal.class),
                () -> TestExMethod.hashMapRClassMapV3(hashMap, String.class, int.class, Date.class, BigDecimal.class));
        checkMethodInvokes(
                () -> "123".testNoEncounteredMethod(),
                () -> "123"
        );
        testEnd();
    }

    public void throwAssertionFailedError(String message, Object expected, Object actual) {
        assertEquals(expected, actual);
    }
}
