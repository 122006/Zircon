package probe;

import probe.Extensions;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;

public class Usage {
    private static final class Other {
        String surround(String left, String right) {
            return left + right;
        }
    }

    public String extensionCall() {
        return "zircon".surround("[", "]");
    }

    public String directCall() {
        return Extensions.surround("zircon", "[", "]");
    }

    public BiFunction<String, String, String> extensionMethodReference() {
        return "zircon"::surround;
    }

    public String templateExtensionCall() {
        return $"${"zircon".surround("<", ">")}";
    }

    public String templateOnlyImport() {
        return $"${Locale.ROOT}";
    }

    public String unrelatedSameNameCall() {
        return new Other().surround("[", "]");
    }

    public <E, C extends E> List<C> chainedGenericExtension(List<E> values) {
        return Extensions.identity(values).map(value -> (C) value);
    }

    public String optionalChain(String value) {
        return value?.trim() ?: "";
    }
}
