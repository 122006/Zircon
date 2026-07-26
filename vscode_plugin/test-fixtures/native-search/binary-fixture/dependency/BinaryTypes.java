package dependency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class BinaryTypes {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Allowed {
    }

    @Allowed
    public static final class Accepted {
    }

    public static final class Rejected {
    }

    private BinaryTypes() {
    }
}
