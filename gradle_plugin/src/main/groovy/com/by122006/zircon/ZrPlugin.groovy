package com.by122006.zircon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

/** Adds the Central-hosted compiler and annotations to Java/Android projects. */
class ZrPlugin implements Plugin<Project> {
    static final String GROUP = 'io.github.122006.Zircon'

    void apply(Project project) {
        project.tasks.withType(JavaCompile).configureEach { JavaCompile task ->
            ['zircon_optional_chain': 'ZrOptionalChain', 'zircon_ex_method': 'ZrExMethod',
             'zircon_temp_string': 'ZrString'].each { property, plugin ->
                if (!project.hasProperty(property) || project.property(property).toString().toBoolean()) {
                    task.options.compilerArgs.add('-Xplugin:' + plugin)
                }
            }
            task.options.fork = true
            task.doFirst {
                // Gradle's JVM can be newer than the selected compiler toolchain.
                if (task.javaCompiler.get().metadata.languageVersion.asInt() >= 9) {
                    task.options.forkOptions.jvmArgs += [
                            '--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED',
                            '--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED',
                            '--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED',
                            '--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED',
                            '--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED']
                }
            }
        }
        def version = resolveVersion(project)
        ['annotationProcessor': 'javac', 'implementation': 'zircon'].each { configuration, artifact ->
            project.dependencies.add(configuration, GROUP + ':' + artifact + ':' + version)
        }
        project.dependencies.add('implementation', GROUP + ':base:' + version)
        ['androidTestImplementation', 'testAnnotationProcessor'].each { configuration ->
            if (project.configurations.findByName(configuration) != null) {
                project.dependencies.add(configuration, GROUP + ':javac:' + version)
            }
        }
    }

    private static String resolveVersion(Project project) {
        if (project.hasProperty('zircon_version')) return project.property('zircon_version').toString()
        for (candidate in [project, project.rootProject]) {
            def dependency = candidate.buildscript.configurations.classpath.dependencies.find {
                it.group == GROUP && it.name == 'gradle'
            }
            if (dependency?.version) return dependency.version
        }
        def metadata = new Properties()
        ZrPlugin.getResourceAsStream('/zircon-version.properties').withCloseable { metadata.load(it) }
        return metadata.getProperty('version')
    }
}
