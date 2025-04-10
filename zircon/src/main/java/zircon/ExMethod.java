package zircon;

import java.lang.annotation.*;

/**
 * 拓展方法标识，在你需要声明为拓展方法的原始方法上增加该注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExMethod {
    /**
     * 当方法需要注册为静态方法时，声明静态方法所在的类。<br/>
     * 可以复数指定。<br/>
     * 当指定了该参数时，方法中首个变量不再指向方法的调用者对象<br/>
     */
    Class<?>[] ex() default {};

    /**
     * 限制归属类必须包含指定注解<br/>
     * 使用中，如果是静态方法，其对应Class必须<b>直接</b>包含全部指定的注解，不支持继承<br/>
     * 如果是实例方法，其对应<b>直接</b>类型必须包含全部指定的注解，不支持继承<br/>
     * 注解会在编译阶段进行检查<br/>
     */
    Class<? extends Annotation>[] filterAnnotation() default {};

    /**
     * 当方法已有java实现时：false（默认）:不覆盖原有方法实现。true:覆盖原有方法实现
     */
    boolean cover() default false;


}
