package com.by122006.zircon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile


class ZrPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.tasks.withType(JavaCompile) { JavaCompile it ->
            if (!project.hasProperty("zircon_ex_method") || project.zircon_ex_method) it.options.compilerArgs << "-Xplugin:ZrExMethod"
            if (!project.hasProperty("zircon_temp_string") || project.zircon_temp_string) it.options.compilerArgs << "-Xplugin:ZrString"
            if (Integer.parseInt(System.getProperty("java.version").split("\\.")[0]) >= 9) {
                it.options.forkOptions.jvmArgs << "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" << "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED" << "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED" << "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED" << "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED"
                it.options.fork = true
            }
        }
        var version = project.hasProperty("zircon_version") ? project.zircon_version : "3.2.5";
        project.dependencies.add("annotationProcessor"
                , project.dependencies.create("com.github.122006.Zircon:javac:" + version))
        project.dependencies.add("implementation"
                , project.dependencies.create("com.github.122006.Zircon:zircon:" + version))
        try {
            project.dependencies.add("androidTestImplementation"
                    , project.dependencies.create("com.github.122006.Zircon:javac:" + version))
        } catch (ignored) {

        }
        try {
            project.dependencies.add("testAnnotationProcessor"
                    , project.dependencies.create("com.github.122006.Zircon:javac:" + version))
        } catch (ignored) {

        }

    }
}
