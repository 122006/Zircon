package com.sun.tools.javac.parser;


public class CompareSameMethod {
    public static <T> int compare(CompareEnv env, MethodInfo<T> info1, MethodInfo<T> info2) {
        final String[] methodOwnerClassNameInfo1 = info1.ownerClassName.split("\\.");
        final String[] methodOwnerClassNameInfo2 = info2.ownerClassName.split("\\.");
        String[] split = env.nowClassName.split("\\.");
        for (int i = 0; i < split.length; i++) {
            String s = split[i];
            final boolean equals1 = methodOwnerClassNameInfo1[i].equals(s);
            final boolean equals2 = methodOwnerClassNameInfo2[i].equals(s);
            if (equals1 && equals2) {
                continue;
            }
            if (equals1) return 1;
            if (equals2) return -1;
        }
        if (methodOwnerClassNameInfo1.length < methodOwnerClassNameInfo2.length) {
            return 1;
        }
        if (methodOwnerClassNameInfo1.length > methodOwnerClassNameInfo2.length) {
            return -1;
        }
        return 0;
    }

    public static class MethodInfo<T> {
        String ownerClassName;

        public T method;

        public static <T> MethodInfo<T> create(String ownerClassName, T method) {
            final MethodInfo<T> methodInfo = new MethodInfo<>();
            methodInfo.ownerClassName = ownerClassName;
            methodInfo.method = method;
            return methodInfo;
        }
    }

    public static class CompareEnv {
        String nowClassName;

        public static CompareEnv create(String nowClassName) {
            final CompareEnv env = new CompareEnv();
            env.nowClassName = nowClassName;
            return env;
        }
    }
}
