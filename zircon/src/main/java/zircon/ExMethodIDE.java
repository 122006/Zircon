package zircon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExMethodIDE {
    /**
     * 标记是否需要直接调用：false（默认）:无限制。true:只能在类中直接调用<br/>
     * 不会限制编译时校验，错误使用时不会报错，仅在IDE联想提示时生效
     */
    boolean shouldInvokeDirectly() default false;
}
