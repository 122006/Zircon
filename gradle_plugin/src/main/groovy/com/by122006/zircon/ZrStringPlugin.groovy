package com.by122006.zircon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.compile.JavaCompile

class ZrStringPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.getTasks().withType(JavaCompile) { JavaCompile it ->
            it.options.compilerArgs
                    << "-Xplugin:ZrString"
            if (Integer.parseInt(System.getProperty("java.version").split("\\.")[0]) >= 16) {
                it.options.fork = true
                it.options.forkOptions.jvmArgs
                        << "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                        << "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
                        << "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
                        << "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED"
                        << "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED"
            }
        }
//        project.dependencies{
//            annotationProcessor "com.github.122006.Zircon:javac:2.+"
//        }
        project.dependencies.add("annotationProcessor"
                , project.dependencies.create("com.github.122006.Zircon:javac:2.8"))
    }
}
