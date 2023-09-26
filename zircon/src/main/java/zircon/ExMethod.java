package zircon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 拓展方法标识，在你需要声明为拓展方法的原始方法上增加该注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExMethod {
    /**
     * 当方法需要注册为静态方法时，声明静态方法所在的类。可以复数指定。当指定了该参数时，方法中首个变量不再指向方法的调用者对象
     */
    Class<?>[] ex() default {};

    /**
     * 当方法已有java实现时：false（默认）:不覆盖原有方法实现。true:覆盖原有方法实现
     */
    boolean cover() default false;
}
