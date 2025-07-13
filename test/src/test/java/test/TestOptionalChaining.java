package test;

import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;
import test.TestExMethod;
import zircon.example.ExArray;
import zircon.example.ExCollection;
import zircon.example.ExObject;
import zircon.example.ExString;

import java.util.ArrayList;
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
        Integer varInt = v?.return_int1();
        int varint = v?.return_int1() ?: 1;
        float testNormalExpr = true ? .5f : .3f;
        v?.returnThis()?.returnThis2();

        checkMethodInvokes(
                () -> v?.return_int1(),
                () -> v.return_int1());
        checkMethodInvokes(
                () -> v?.returnThis()?.returnThis(),
                () -> v.returnThis()?.returnThis());
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
        v123234141?.returnThis().returnThis2();
        v?.returnNull();
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
        try {
            if (nullV?.returnNull()?.return_int1() == Integer.valueOf(12)) {
                throw new RuntimeException();
            }
        } catch (NullPointerException ignore) {
        }
        try {
            if (nullV?.returnNull()?.return_int1() == (Integer) null) {
                throw new RuntimeException();
            }
        } catch (NullPointerException ignore) {
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
                () -> (TestChildClass.class ?.getName() + null) ?: getClass()
                , () -> "test.TestOptionalChaining$TestChildClassnull");
        checkMethodInvokes(
                () -> (TestChildClass.staticObj?.nullStaticObj ?: "123" + null) ?: getClass()
                , () -> "123null");
        checkMethodInvokes(
                () -> TestChildClass.staticObj?.nullStaticObj == null
                , () -> true);
        if (TestChildClass.staticObj.staticObj?.nullStaticObj != null) throw new RuntimeException();
        assert TestChildClass.staticObj.staticObj?.nullStaticObj == null : "123";
        assert TestChildClass.staticObj?.returnBoolean() : "123";
        if (TestChildClass.staticIgnoreAction().staticIgnoreAction()?.staticIgnoreAction() == null)
            throw new RuntimeException();
        if (TestChildClass.staticIgnoreAction().nullStaticObj?.staticIgnoreAction() != null)
            throw new RuntimeException();
        if (TestChildClass.nullStaticObj.staticIgnoreAction()?.staticIgnoreAction() == null)
            throw new RuntimeException();
        checkMethodInvokes(
                () -> (TestChildClass.staticObj?.nullStaticObj ?: "567" + null) ?: (TestChildClass.staticObj?.getTestImplClass() ?: "456" + null)
                , () -> "567null");
//        checkMethodInvokes(
//                () -> TestChildClass.nullStaticObj?.returnBoolean() + (String) null
//                , () -> "nullnull");
        checkMethodInvokes(
                () -> (TestChildClass.nullStaticObj?.returnNull() + (String) "12312323")?.getClass()
                , () -> String.class);
        List<String> data = Arrays.asList("123", "456");
        checkMethodInvokes(
                () -> data.stream().filter(a -> a.length() == 0).findFirst().orElse("1234")?.length()
                , () -> 4);
        checkMethodInvokes(
                () -> data.find(a -> a.length() == 0)?.length()
                , () -> null);
        Object testA = TestChildClass.nullStaticObj ?: classVar;
        checkMethodInvokes(
                () -> TestChildClass.nullStaticObj?.nullObj
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
                () -> TestChildClass.nullStaticObj?.returnDouble() ?: 2.0d
                , () -> 2.0d);
        checkMethodInvokes(
                () -> {
                    final double v1 = TestChildClass.nullStaticObj?.returnDouble() ?: 2.0d;
                    final double v2 = TestChildClass.nullStaticObj?.returnDouble() ?: 2;
                    final int v3 = (int) TestChildClass.nullStaticObj?.returnDouble() ?: 2;
                    final double v4 = TestChildClass.staticObj.returnDouble() ?: 2;
                    final int v5 = (int) TestChildClass.staticObj.returnDouble() ?: 2;
                    final long l1 = TestChildClass.nullStaticObj?.returnLong() ?: 2l;
                    return v1 + v2 + v3 + v4 + v5 + l1;
                }
                , () -> {
                    final double v1 = TestChildClass.staticObj.returnDouble()
                            + (int) TestChildClass.staticObj.returnDouble()
                            + 8;
                    return v1;
                });
        checkMethodInvokes(
                () -> TestChildClass.staticIgnoreAction()?.returnDouble(var?.returnDouble(), v?.returnDouble()) ?: 2.0d
                , () -> {
                    TestChildClass.staticIgnoreAction();
                    final double d1 = var.returnDouble();
                    final double d2 = var.returnDouble();
                    final double v1 = var.returnDouble(d1, d2);
                    return v1;
                });
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
                () -> classNullVar?.obj = (classNullVar?.returnThis() ?: classVar)
                , () -> classVar);
        checkMethodInvokes(
                () -> {
                    classNullVar?.obj = (classNullVar?.returnThis() ?: classVar);
                }
                , () -> {
                });
        checkMethodInvokes(
                () -> {
                    classVar?.obj = (classNullVar?.returnThis() ?: classVar);
                }
                , () -> {
                    assert classVar.obj == classVar;
                });
        checkMethodInvokes(
                () -> {
                    return classVar?.returnThis(classVar?.returnThis())?.obj = (classVar?.returnThis2() ?: classVar);
                }
                , () -> {
                    classVar.returnThis();//先执行左边的参数
                    classVar.returnThis(classVar);//再执行左边方法
                    classVar.returnThis2();//执行右边的方法
                    return classVar;//左边不成立，返回右边
                });
        checkMethodInvokes(
                () -> {
                    classNullVar?.obj = (classNullVar?.returnThis() ?: classVar);
                    return classNullVar?.obj = (classNullVar?.returnThis() ?: classVar);
                }
                , () -> {
                    return classVar;
                });
        checkMethodInvokes(
                () -> {
                    String[] array = null;
                    return array?.get(0);
                }
                , () -> {
                    return null;
                });
        checkMethodInvokes(
                () -> {
                    String[] array = {"123"};
                    try {
                        return array?.get(1).trim()?.toString() ?: "nullString";
                    } catch (NullPointerException e) {
                        return "NullPointerException";
                    }
                }
                , () -> {
                    return "NullPointerException";
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
        if (emptyString?.isEmpty() ?: true) {
        } else {
            throw new RuntimeException();
        }
        checkMethodInvokes(
                () -> {
                    Integer integer = null;
                    return integer ?: 22233344;
                }
                , () -> {
                    Integer integer = null;
                    return integer ?: Integer.valueOf(22233344);
                });
        checkMethodInvokes(
                () -> {
                    List<String> strings = List.create(1, 2, 3).map(String::valueOf) ?: new ArrayList<>();
                    return strings;
                }
                , () -> List.create(1, 2, 3).map(String::valueOf));
        checkMethodInvokes(
                () -> {
                    classVar.setValue(classNullVar?.getClass().getName());
                }
                , () -> {
                    classVar.setValue("String");
                });
        checkMethodInvokes(
                () -> {
                    try {
                        classVar.setValue(classNullVar?.return_int1());
                        return null;
                    } catch (Exception e) {
                        return e.getClass();
                    }
                }
                , () -> {
                    return NullPointerException.class;
                });

        checkMethodInvokes(
                () -> {
                    classVar.setIntegerValue(classNullVar?.return_int1());
                }
                , () -> {
                    classVar.setIntegerValue(null);
                });

        checkMethodInvokes(
                () -> {
                    classVar.setValue(classNullVar?.return_int1() ?: 1);
                }
                , () -> {
                    classVar.setValue(1);
                });
        checkMethodInvokes(
                () -> {
                    classVar.setValue(classNullVar?.getClass().getName() ?: "-");
                }
                , () -> {
                    classVar.setValue("String");
                });
        checkMethodInvokes(
                () -> {
                    classVar.setValueMix(classNullVar?.getClass().getName() ?: "-", classNullVar?.return_int1() ?: 1);
                }
                , () -> {
                    classVar.setValueMix("String", 1);
                });
        checkMethodInvokes(
                () -> {
                    classVar.setValueMix(classNullVar?.return_int1() ?: 1, classNullVar?.getClass().getName() ?: "-");
                }
                , () -> {
                    classVar.setValueMix(1, "String");
                });
        checkMethodInvokes(
                () -> {
                    classVar.setValueMix(classNullVar?.returnThis().cast(TestChildClass.class) ?: new TestChildClass(), classNullVar?.getClass().getName() ?: "-");
                }
                , () -> {
                    classVar.setValueMix(new TestChildClass(), "String");
                });
        checkMethodInvokes(
                () -> {
                    classVar.setValueMix(classNullVar?.returnThis().cast(TestChildClass.class) ?: new TestChildClass(), classNullVar?.returnThis().cast(TestClass.class) ?: new TestChildClass());
                }
                , () -> {
                    classVar.setValueMix(new TestChildClass(), "String");
                });
        checkMethodInvokes(
                () -> classVar ?: classNullVar?.cast(TestChildClass.class)
                , () -> classVar);
        checkMethodInvokes(
                () -> {
                    classVar.setValueMix(classNullVar?.returnThis().cast(TestChildClass.class) ?: new TestChildClass(), classNullVar?.returnThis().cast(TestClass.class) ?: classNullVar.cast(TestChildClass.class));
                }
                , () -> {
                    classVar.setValueMix(new TestChildClass(), "String");
                });
        checkMethodInvokes(
                () -> {
                    classVar.setValueMix(classNullVar?.returnThis().cast(TestChildClass.class) ?: new TestChildClass(), classNullVar?.returnThis(classNullVar?.returnThis().cast(TestClass.class) ?: classNullVar?.returnThis().cast(TestChildClass.class)));
                }
                , () -> {
                    classVar.setValueMix(new TestChildClass(), "String");
                });
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
        static TestClass staticObj = new TestClass();

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

        public double returnDouble() {
            TestExMethod.methodNames.add("returnDouble");
            return 12.34d;
        }

        public long returnLong() {
            TestExMethod.methodNames.add("returnLong");
            return 100L;
        }

        public double returnDouble(double dv1, double dv2) {
            TestExMethod.methodNames.add("returnDouble(" + dv1 + "," + dv2);
            return 12.34d;
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

        public String setValue(String a) {
            TestExMethod.methodNames.add("setValue(Str");
            return a;
        }

        public void setValue(int a) {
            TestExMethod.methodNames.add("setValue(int");
        }

        public void setIntegerValue(Integer a) {
            TestExMethod.methodNames.add("setIntegerValue(Integer");
        }

        public String setValueMix(int a, String b) {
            TestExMethod.methodNames.add("setValueMix(int,String");
            return b;
        }

        public String setValueMix(String a, int b) {
            TestExMethod.methodNames.add("setValueMix(String,int");
            return a;
        }

        public <T> T setValueMix(TestChildClass a, T b) {
            TestExMethod.methodNames.add("setValueMix(TestChildClass,T");
            return b;
        }

        public void invoke() {
            TestExMethod.methodNames.add("invoke");
        }

    }


}
