package test;

import org.junit.jupiter.api.Test;
import test.TestExMethod;
import test.TestExMethod.ChildEnv;
import test.TestExMethodImpl;
import test.TestNoEncounteredMethod;
import zircon.ExMethod;
import zircon.data.ThrowConsumer;
import zircon.data.ThrowPredicate;
import zircon.example.ExCollection;
import zircon.example.ExObject;
import zircon.example.ExString;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({"Convert2MethodRef", "ResultOfMethodCallIgnored", "MismatchedReadAndWriteOfArray", "CodeBlock2Expr", "unused"})
public class TestExMethodImpl {
    @SuppressWarnings("AccessStaticViaInstance")
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
                    return strs.emptyStringArrayRStringArray().emptyStringArrayRStringArray1()
                               .emptyStringArrayRStringArray2();
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
                () -> new TestExMethod.FatherClass[]{childClass}.fatherTArrayExtendArrayRT("123"),
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
                () -> Arrays.asList("123", "456", "789").subList(0, 3).find(a -> a.endsWith("123")),
                () -> ExCollection.find(Arrays.asList("123", "456", "789").subList(0, 3), a -> a.endsWith("123")));
        String bString = "456";
        checkMethodInvokes(
                () -> Arrays.asList("123", "456", "789").forEach((bString::toInteger2)),
                () -> Arrays.asList("123", "456", "789").forEach(t -> bString.toInteger2(t)));
        checkMethodInvokes(
                () -> Arrays.asList("123", "456", "789").forEach(("456"::toInteger2)),
                () -> Arrays.asList("123", "456", "789").forEach(t -> "456".toInteger2(t)));
        checkMethodInvokes(
                () -> {
                    final Function<String, Integer> testLambda = bString::toInteger2;
                },
                () -> {
                    final Function<String, Integer> testLambda3 = str2 -> bString.toInteger2(str2);
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
                () -> "31231".nullOr("2432"),
                () -> zircon.example.ExObject.nullOr("31231", "123"));
        checkMethodInvokes(
                () -> nullStr.isBlank(),
                () -> TestExMethod.isBlank(nullStr));
        checkMethodInvokes(
                () -> Arrays.asList(123, 456),
                () -> TestExMethod.asList(123, 456));
        checkMethodInvokes(
                () -> Arrays.asList(123, 456).sort(),
                () -> TestExMethod.asList(123, 456).sort());
        checkMethodInvokes(
                () -> Arrays.asList(123, 456).sort(Comparator.comparing(a->a)),
                () -> TestExMethod.asList(123, 456).sort(Comparator.comparing(a->a)));
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
        checkMethodInvokes(
                () -> str.testGenericTransformMethod("123"),
                () -> TestExMethod.testGenericTransformMethod(str, "123"));
        checkMethodInvokes(
                () -> str.testGenericTransformMethod2("123"),
                () -> TestExMethod.testGenericTransformMethod2(str, "123"));
        checkMethodInvokes(
                () -> hashMap.testGenericTransformMethod("123"),
                () -> TestExMethod.testGenericTransformMethod(hashMap, "123"));
        checkMethodInvokes(
                () -> hashMap.testGenericTransformMethodRSet("123"),
                () -> TestExMethod.testGenericTransformMethodRSet(hashMap, "123"));
        checkMethodInvokes(
                () -> hashMap.testGenericTransformMethodRSet(str),
                () -> TestExMethod.testGenericTransformMethodRSet(hashMap, str));
        checkMethodInvokes(
                () -> hashMap.testGenericTransformMethodRSet2(str),
                () -> TestExMethod.testGenericTransformMethodRSet2(hashMap, str));
        hashMap.put("1", 2);
        checkMethodInvokes(
                () -> {
                    Consumer<Consumer<? extends String>> consumer = s -> {

                    };
                    final Consumer<? extends String> t = this.$throw2((a) -> {
                        a.length();
//                        System.out.println(a.getClass());
                    });
//
                    final Consumer<? extends String> t2 = self(this.$throw2((a) -> {
                        a.length();
//                        System.out.println(a.getClass());
                    }));
                    final Consumer<? extends String> t3 = self("123".$throw3((a) -> {
                        a.length();
                    }));
                    hashMap.keySet().forEach($throw2(a -> {
                        a.length();
                    }));
//                    consumer.accept(t);
                },
                () -> hashMap.keySet().forEach(ExObject.$throw(a -> {
                    a.length();
                }))
        );
        final Runnable runnable = () -> hashMap.keySet().forEach(ExObject.$throw(a -> {
            a.length();
        }));
        checkMethodInvokes(
                () -> {
                    hashMap.keySet().findAll($throw((ThrowPredicate<String>) a -> {
                        return true;
                    }));
                },
                () -> hashMap.keySet().findAll(Object.<String>$throw((ThrowPredicate<String>) a -> {
                    return true;
                }))
        );
        Function<String, ? extends Number> findByString = string -> {
            return string.toInt();
        };
        checkMethodInvokes(
                () -> {
                    return findByString.apply("123").convert(re -> {
                        return re.intValue();
                    });
                },
                () -> 123
        );
        ArrayList<Pair<Integer, String>> pairs = new ArrayList<>();
        final ArrayList<Integer> singleList = new ArrayList<>();
//        singleList.forEachPair((a,b) -> a.notNull()) ;
        pairs.forEachPair((a,b)->a.isNull());
        forEachPair(pairs,(a,b)->{a.notNull();});
        testEnd();
    }

    public <M> M self(M t) {
        return t;
    }

    @ExMethod(ex = {Object.class})
    public static <T> Consumer<T> $throw2(ThrowConsumer<T> action) {
        return (t) -> {
            try {
                action.accept(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    @ExMethod
    public static <T> Consumer<T> $throw3(Object object, Consumer<T> action) {
        return (t) -> {
            try {
                action.accept(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static class Pair<T, M> {
        T first;
        M second;

        public Pair(T first, M second) {
            this.first = first;
            this.second = second;
        }
    }

    public static <E, M, V> Map<M, V> groupBy(Collection<E> collection, Function<E, M> function, Function<List<E>, V> valueMap) {
        if (collection == null) return null;
        Map<M, List<E>> map = new HashMap<>();
        for (E e : collection) {
            map.computeIfAbsent(function.apply(e), k -> new ArrayList<>()).add(e);
        }
        return new HashMap<M,V>().let(a -> {
            map.forEach((key, value) -> a.put(key, valueMap.apply(value)));
        });
    }
    @ExMethod
    public static <T, M> void forEachPair(List<Pair<T, M>> pairs, BiConsumer<T, M> action) {
        pairs.forEach(a -> action.accept(a.first, a.second));
    }

    public void throwAssertionFailedError(String message, Object expected, Object actual) {
        assertEquals(expected, actual);
    }
}
