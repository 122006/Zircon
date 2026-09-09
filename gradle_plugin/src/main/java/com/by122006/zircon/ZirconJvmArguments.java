package com.by122006.zircon;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.process.CommandLineArgumentProvider;

import java.util.Arrays;
import java.util.Collections;

/** Tracks the actual compiler toolchain as an input without mutating tasks during execution. */
public abstract class ZirconJvmArguments implements CommandLineArgumentProvider {
    @Input
    public abstract Property<Integer> getJavaVersion();

    @Override
    public Iterable<String> asArguments() {
        if (getJavaVersion().get() < 9) return Collections.emptyList();
        return Arrays.asList(
                "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED");
    }
}
