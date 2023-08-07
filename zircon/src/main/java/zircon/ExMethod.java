package zircon;
public @interface ExMethod {
    Class<?>[] ex() default {};
    boolean force() default false;
}
