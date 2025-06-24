package test;

import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;
import test.TestExMethod;
import zircon.ExMethod;
import zircon.example.ExArray;
import zircon.example.ExCollection;
import zircon.example.ExString;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * @ClassName: TestOptionalChaining
 * @Author: 0001185
 * @Date: 2025/5/7 16:05
 * @Description:
 */
public class TestOptionalChaining {


    @Test
    public void testBasic() {
        TestClass v = new TestClass();
        TestClass nullV = null;

        TestClass var = v?.returnThis()?.returnThis2();

        float testNormalExpr = true ? .5f : .3f;

        v?.returnThis()?.returnThis2();

        checkMethodInvokes(
                () -> v?.return_int1(),
                () -> v.return_int1());
//
        checkMethodInvokes(
                () -> v?.returnThis()?.returnThis(),
                () -> v.returnThis()?.returnThis());
//
        checkMethodInvokes(
                () -> v?.returnThis()?.returnThis(),
                () -> v.returnThis().returnThis());


        checkMethodInvokes(
                () -> v?.returnThis()?.returnThis()?.returnThis(),
                () -> v?.returnThis().returnThis().returnThis());
        checkMethodInvokes(
                () -> v?.returnThis()?.returnThis()?.returnThis()?.returnThis(),
                () -> v?.returnThis().returnThis().returnThis().returnThis());

        checkMethodInvokes(
                () -> v.returnThis()?.returnThis().returnThis().returnThis(),
                () -> v?.returnThis().returnThis()?.returnThis().returnThis());


        checkMethodInvokes(
                () -> v?.returnNull()?.returnThis(),
                () -> {
                    final TestClass testClass = v.returnNull();
                    return null;
                });
        checkMethodInvokes(
                () -> (v.returnThis2())?.returnThis(),
                () -> (v?.returnThis2())?.returnThis());
        checkMethodInvokes(
                () -> (v?.returnThis()?.nullObj?.returnThis2() ?: v).returnThis(),
                () -> {
                    v.returnThis();
                    return v.returnThis();
                });
        checkMethodInvokes(
                () -> v.returnThis()?.invoke(),
                () -> v?.returnThis()?.invoke());

        checkMethodInvokes(
                () -> {
                    final TestClass testClass = v?.returnNull()?.returnThis();
                },
                () -> {
                    v.returnNull();
                });
        checkMethodInvokes(
                () -> {
                    final TestClass testClass = v?.returnThis()?.returnNull();
                },
                () -> {
                    v.returnThis().returnNull();
                });
        checkMethodInvokes(
                () -> {
                    (v?.returnThis())?.returnNull();
                },
                () -> {
                    v.returnThis().returnNull();
                });

        checkMethodInvokes(
                () -> {
                    if (v?.returnNull() == null) {
                        v.returnThis();
                    }
                },
                () -> {
                    v?.returnNull();
                    v.returnThis();
                });
        TestClass v123234141 = v?.returnThis().returnThis2();
//
        v123234141?.returnThis().returnThis2();

        v?.returnNull();
//
//

        if ((nullV?.returnNull()?.return_int1() ?: 12) == 12) {
            v.returnThis();
        } else {
            throw new RuntimeException();
        }
        try {
            if ((nullV?.returnNull()?.return_int1()) == 12) {
                v.returnThis();
            }
            throw new RuntimeException("对于可选链最终结果类型为基础类型时，不满足时会抛出空指针异常。如果为抛出则错误");
        } catch (NullPointerException e) {
            //可选链默认可能返回null。对于基本类型会扩展为包装器类型。所以在后续转化时发生拆箱动作时会空指针异常
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
        if (nullV?.returnNull()?.return_int1() == Integer.valueOf(12)) {
            //如果作为非基本类型使用则没问题
            throw new RuntimeException();
        }
        if (nullV?.returnNull()?.return_int1() != (Integer) null) {
            //同时可以与(Integer) null比较
            throw new RuntimeException();
        }
        if ((nullV?.returnNull()?.return_long() ?: 12L) == 12) {
            v.returnThis();
        }
        try {
            if ((nullV?.returnNull()?.return_long()) == 12) {
                v.returnThis();
            }
            throw new RuntimeException("long基础类型同理");
        } catch (NullPointerException e) {
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }

        Supplier<TestChildClass> re = v?.getTestImplClass();
        re.get();

        v?.getTestImplClass()?.get();


        TestChildClass.nullStaticObj?.getTestImplClass();
        (TestChildClass.class ?.getName() + "")?.getClass();


        checkMethodInvokes(
                () -> (TestChildClass.class ?.getName() + "")?.getClass()
                , () -> String.class);
        checkMethodInvokes(
                () -> (TestChildClass.class ?.getName() + null)?.getClass()
                , () -> String.class);
        checkMethodInvokes(
                () -> TestChildClass.nullStaticObj?.returnBoolean() + (String) null
                , () -> "nullnull");
        checkMethodInvokes(
                () -> (TestChildClass.nullStaticObj?.returnNull() + (String) null)?.getClass()
                , () -> String.class);
        List<String> data = Arrays.asList("123", "456");

        checkMethodInvokes(
                () -> data.stream().filter(a -> a.length() == 0).findFirst().orElse("1234")?.length()
                , () -> 4);
        checkMethodInvokes(
                () -> data.find(a -> a.length() == 0)?.length()
                , () -> null);
        checkMethodInvokes(
                () -> TestChildClass.nullStaticObj ?: classVar
                , () -> classVar);
        checkMethodInvokes(
                () -> TestChildClass.nullStaticObj?.return_int1() ?: 1
                , () -> 1);
        checkMethodInvokes(
                () -> TestChildClass.nullStaticObj?.return_int1() ?: Integer.valueOf(1)
                , () -> 1);
        checkMethodInvokes(
                () -> TestChildClass.nullStaticObj?.return_Integer1() ?: 1
                , () -> 1);
        checkMethodInvokes(
                () -> data.find(a -> a.length() == 0)?.length() ?: 4
                , () -> 4);
        checkMethodInvokes(
                () -> classNullVar?.obj = classVar.returnThis()
                , () -> classVar.returnThis());
        checkMethodInvokes(
                () -> classNullVar?.obj = classNullVar?.returnThis()
                , () -> null);
        checkMethodInvokes(
                () -> {
                    return classNullVar?.obj = classVar.returnThis();
                }
                , () -> classVar.returnThis());
        checkMethodInvokes(
                () -> {
                    return classNullVar?.obj = classNullVar?.returnThis();
                }
                , () -> null);
        checkMethodInvokes(
                () -> {
                    classNullVar?.obj = classNullVar?.returnThis();
                }
                , () -> {
                });
        checkMethodInvokes(
                () -> {
                    return classNullVar?.obj = (classNullVar?.returnThis() ?: classVar);
                }
                , () -> classVar);
        checkMethodInvokes(
                () -> {
                    classNullVar?.obj = (classNullVar?.returnThis() ?: classVar);
                }
                , () -> {
                });


        checkMethodInvokes(
                () -> {
                    String[] array = null;
                    return array?.get(0);
                }
                , () -> {
                    return null;
                });
        final TestChildClass testChildClass = new TestChildClass();
        checkMethodInvokes(
                () -> {
                    TestChildClass[] array = new TestChildClass[1];
                    return array?.get(0) ?: classVar;
                }
                , () -> {
                    return classVar;
                });
        checkMethodInvokes(
                () -> {
                    TestChildClass[] array = null;
                    return array?.get(0) ?: testChildClass;
                }
                , () -> {
                    return testChildClass;
                });
        checkMethodInvokes(
                () -> {
                    TestChildClass[] array = new TestChildClass[1];
                    return array?.get(0)?.getClass();
                }
                , () -> {
                    return null;
                });
        checkMethodInvokes(
                () -> {
                    TestChildClass[] array = new TestChildClass[]{new TestChildClass()};
                    return array?.get(0)?.getClass();
                }
                , () -> {
                    return TestChildClass.class;
                });
        checkMethodInvokes(
                () -> {
                    TestChildClass[] array = new TestChildClass[]{new TestChildClass()};
                    return array?.get(0)?.returnThis().getClass();
                }
                , () -> {
                    new TestChildClass().returnThis();
                    return TestChildClass.class;
                });
        checkMethodInvokes(
                () -> {
                    TestChildClass[] array = new TestChildClass[]{new TestChildClass()};
                    return array[new int[]{0, 1, 2, 3} ?.copy()[0]].getClass();
                }
                , () -> TestChildClass.class);

        checkMethodInvokes(
                () -> {
                    TestChildClass[] array = new TestChildClass[]{new TestChildClass()};
                    return array[array?.copy()[0].return_int1() - 1].getClass();
                }
                , () -> {
                    new TestChildClass().return_int1();
                    return TestChildClass.class;
                });
        checkMethodInvokes(
                () -> {
                    final TestClass testClass = new TestClass();
                    return testClass?.returnBoolean() ?: false;
                }
                , () -> {
                    return new TestClass().returnBoolean();
                });
        String emptyString = null;
        if (emptyString?.isEmpty() ?: false) {
            throw new RuntimeException();
        }

        testEnd();

    }

    TestClass classVar = new TestClass();
    TestClass classNullVar;

    public static class TestChildClass extends TestClass {
    }

    public static class TestClass {
        public <T extends TestClass> Supplier<T> getTestImplClass() {
            return () -> {
                return (T) new TestChildClass();
            };
        }

        static TestClass nullStaticObj = null;

        TestClass nullObj = null;

        TestClass obj = null;


        public static TestClass staticIgnoreAction() {
            TestExMethod.methodNames.add("staticIgnoreAction");
            return new TestClass();
        }

        public TestClass ignoreAction() {
            TestExMethod.methodNames.add("ignoreAction");
            return this;
        }

        public TestClass returnThis() {
            TestExMethod.methodNames.add("returnThis");
            return this;
        }

        public TestClass returnThis2() {
            TestExMethod.methodNames.add("returnThis2");
            return this;
        }

        public boolean returnBoolean() {
            TestExMethod.methodNames.add("returnBoolean");
            return true;
        }

        public TestClass returnThis(TestClass param) {
            TestExMethod.methodNames.add("returnThis(" + param);
            return this;
        }

        @Nullable
        public TestClass returnNull() {
            TestExMethod.methodNames.add("returnNull");
            return null;
        }

        public int return_int1() {
            TestExMethod.methodNames.add("return_int");
            return 1;
        }

        public Integer return_Integer1() {
            TestExMethod.methodNames.add("return_Integer1");
            return 1;
        }

        public long return_long() {
            TestExMethod.methodNames.add("return_long");
            return 1L;
        }

        public void invoke() {
            TestExMethod.methodNames.add("invoke");
        }

    }


    @ExMethod
    public static <T> T $$NullSafe(T o) {
        throw new RuntimeException("异常链路：" + o);
//        return o;
    }
}
