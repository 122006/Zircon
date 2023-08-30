package com.by122006.zircon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.compile.JavaCompile

class ZrPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.tasks.withType(JavaCompile) { JavaCompile it ->
            it.options.compilerArgs << "-Xplugin:ZrExMethod"
            it.options.compilerArgs << "-Xplugin:ZrString"
            if (Integer.parseInt(System.getProperty("java.version").split("\\.")[0]) >= 16) {
                it.options.forkOptions.jvmArgs
                        << "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                        << "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
                        << "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
                        << "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED"
                        << "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED"
                it.options.fork = true

            }
        }
        project.dependencies.add("annotationProcessor"
                , project.dependencies.create("com.github.122006.Zircon:javac:3.+"))
        project.dependencies.add("implementation"
                , project.dependencies.create("com.github.122006.Zircon:zircon:3.+"))
    }
}
