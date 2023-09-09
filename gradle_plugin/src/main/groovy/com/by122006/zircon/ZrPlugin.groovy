package com.by122006.zircon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.compile.JavaCompile

class ZirconExt {
    var version = 'latest.release'
    var tempString = true
    var exMethod = true
}

class ZrPlugin implements Plugin<Project> {
    void apply(Project project) {
        ZirconExt extension = project.getExtensions().create("zircon", ZirconExt)
        project.extensions.add("zircon", extension);
        project.tasks.withType(JavaCompile) { JavaCompile it ->
            if (project.extension.zircon.tempString) it.options.compilerArgs << "-Xplugin:ZrExMethod"
            if (project.extension.zircon.exMethod) it.options.compilerArgs << "-Xplugin:ZrString"
            if (Integer.parseInt(System.getProperty("java.version").split("\\.")[0]) >= 11) {
                it.options.forkOptions.jvmArgs << "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" << "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED" << "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED" << "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED" << "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED"
                it.options.fork = true
            }
        }
        var version = project.extensions.zircon.version || "latest.release";
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
