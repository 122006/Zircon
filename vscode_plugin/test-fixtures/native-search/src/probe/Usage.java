package probe;

import probe.Extensions;

import java.util.List;

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
