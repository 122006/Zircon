package smoke;

import smoke.TextExtensions;

public class Main {
    private static int fallbacks;

    private static String fallback() {
        fallbacks++;
        return "default";
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    public static void main(String[] args) {
        equal("[Zircon]", "Zircon".bracketed());
        String missing = null;
        equal("default", missing?.trim() ?: fallback());
        equal(1, fallbacks);
        String present = " Zircon ";
        equal("Zircon", present?.trim() ?: fallback());
        equal(1, fallbacks);
        String name = "Zircon";
        equal("Hello, ZIRCON!", $"Hello, ${name.toUpperCase()}!");
        equal("Age: 07", f"Age: ${%02d:7}");
        System.out.println("Zircon Central smoke passed on Java " + System.getProperty("java.version"));
    }
}
