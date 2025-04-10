package test.filter;

public class FilterAnnotation {
    public static class NoAnnotation {

    }

    @TestFilterAnnotation2
    @TestFilterAnnotation1
    public static class HasFilterAnnotation {

    }

    public static class HasFilterAnnotationExtend extends HasFilterAnnotation {

    }

    public static class HasFilterAnnotation2 {

    }
}

