package com.by122006.zircon.vsplugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * @ClassName: Util
 * @Author: 122006
 * @Date: 2026/2/3 16:51
 * @Description:
 */
public class Util {
    private static final Path LOG_PATH = resolveLogPath();

    private static Path resolveLogPath() {
        final String explicit = getProperty("zircon.log.path", "").trim();
        if (!explicit.isEmpty()) {
            return Paths.get(explicit);
        }
        final String tempDir = System.getProperty("java.io.tmpdir", ".");
        return Paths.get(tempDir, "zircon_vscode_agent.log");
    }

    public static void log(String msg) {
        try {
            Files.write(LOG_PATH, (msg + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[Zircon] 写入日志失败: " + e.getMessage());
        }
    }

    public static boolean isDebugEnabled() {
        return getBooleanProperty("zircon.debug", false);
    }

    public static boolean isTraceEnabled() {
        return getBooleanProperty("zircon.trace", false);
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getProperty(key, Boolean.toString(defaultValue)));
    }

    public static String getProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        if (key != null && key.startsWith("zircon.")) {
            value = System.getProperty("Z" + key.substring(1));
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return defaultValue;
    }

    public static String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        throwable.printStackTrace(writer);
        writer.flush();
        return buffer.toString();
    }
}
