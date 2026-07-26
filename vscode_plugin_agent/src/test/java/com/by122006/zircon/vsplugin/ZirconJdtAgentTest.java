package com.by122006.zircon.vsplugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZirconJdtAgentTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesAtomicRuntimeHeartbeat() throws Exception {
        Path heartbeat = temporaryDirectory.resolve("nested").resolve("agent-runtime.properties");
        File agentJar = temporaryDirectory.resolve("zircon-agent.jar").toFile();

        ZirconJdtAgent.writeRuntimeHeartbeat(heartbeat, agentJar, 12345L, 67890L);

        String content = Files.readString(heartbeat, StandardCharsets.UTF_8);
        assertEquals(
                "pid=12345\n"
                        + "startedAt=67890\n"
                        + "agentJar=" + agentJar.toPath().toAbsolutePath().normalize() + "\n"
                        + "mode=full\n",
                content
        );
    }
}
