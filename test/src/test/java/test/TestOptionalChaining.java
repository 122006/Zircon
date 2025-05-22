package test;

import org.junit.jupiter.api.Test;
import test.TestExMethod;
import zircon.BiOp;
import zircon.ExMethod;

import java.util.function.Supplier;

/**
 * @ClassName: TestOptionalChaining
 * @Author: zwh
 * @Date: 2025/5/7 16:05
 * @Description:
 */
public class TestOptionalChaining {
//    String staticAA = String.class.getClass()?.getCanonicalName();


    @Test
    public void testBasic() {
        TestClass v = new TestClass();
        TestClass nullV = null;

        TestClass var = v?.returnThis()?.returnThis2();

        v?.returnThis()?.returnThis2();

        checkMethodInvokes(
                () -> v?.return_int(),
                () -> v.return_int());
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
                () -> (v?.returnThis()?.nullObj?.returnThis2() || v).returnThis(),
                () -> (BiOp.$$dup(v) != null ? BiOp.$$ignore(v).returnThis2() : null).returnThis());
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


        if ((nullV?.returnNull()?.return_int() || 12) == 12) {
            v.returnThis();
        } else {
            throw new RuntimeException();
        }

        Supplier<TestChildClass> re = v?.getTestImplClass();
        re.get();

        v?.getTestImplClass()?.get();

        testEnd();

    }

    public static class TestChildClass extends TestClass {
    }

    public static class TestClass {
        public <T extends TestClass> Supplier<T> getTestImplClass() {
            return () -> {
                return (T) new TestChildClass();
            };
        }

        TestClass obj = new TestClass();
        TestClass nullObj = null;


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

        public TestClass returnNull() {
            TestExMethod.methodNames.add("returnNull");
            return null;
        }

        public int return_int() {
            TestExMethod.methodNames.add("return_int");
            return 1;
        }

        public void invoke() {
            TestExMethod.methodNames.add("invoke");
        }

    }


    @ExMethod
    public static <T> T $$NullSafe(T o) {
        throw new RuntimeException("异常链路：" + o);
    }
}
