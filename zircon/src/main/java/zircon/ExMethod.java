package zircon;
public @interface ExMethod {
    Class<?>[] ex() default {};
    boolean cover() default false;
}
