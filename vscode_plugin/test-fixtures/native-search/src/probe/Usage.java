package probe;

import probe.Extensions;

public class Usage {
    public String extensionCall() {
        return "zircon".surround("[", "]");
    }

    public String directCall() {
        return Extensions.surround("zircon", "[", "]");
    }
}
