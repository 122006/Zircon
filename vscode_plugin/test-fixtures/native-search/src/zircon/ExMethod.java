package zircon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExMethod {
    Class<?>[] ex() default {};
    boolean cover() default false;
    Class<?>[] filterAnnotation() default {};
}
