package com.by122006.zircon;

import org.gradle.api.provider.Property;

/** Override both coordinates after applying the plugin, including in Kotlin DSL. */
public abstract class ZirconExtension {
    public abstract Property<String> getGroup();
    public abstract Property<String> getVersion();
}
