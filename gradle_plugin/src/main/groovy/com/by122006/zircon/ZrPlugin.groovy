package com.by122006.zircon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.compile.ForkOptions
import org.gradle.api.tasks.compile.JavaCompile

/** Configures the compiler path independently of the Java/Android plugin's apply order. */
class ZrPlugin implements Plugin<Project> {
    static final String GROUP = 'io.github.122006.Zircon'
    static final String JITPACK_GROUP = 'com.github.122006.Zircon'
    private static final boolean JVM_ARGUMENT_PROVIDERS = ForkOptions.methods.any { it.name == 'getJvmArgumentProviders' }

    void apply(Project project) {
        def coordinates = resolveCoordinates(project)
        def extension = project.extensions.create('zircon', ZirconExtension)
        extension.group.convention(project.findProperty('zircon_group')?.toString() ?: coordinates[0])
        extension.version.convention(project.findProperty('zircon_version')?.toString() ?: coordinates[1])

        def compiler = dependencyBucket(project, 'zirconCompiler', 'Zircon compiler and parser dependencies')
        def runtime = dependencyBucket(project, 'zirconRuntime', 'Zircon annotations and runtime helpers')
        compiler.withDependencies { dependencies ->
            ['javac', 'base', 'zircon'].each { artifact ->
                def dependency = project.dependencies.create(module(extension, artifact).get())
                // JitPack POMs may still refer to the Central release used in this checkout.
                // Supply the compiler cohort explicitly while retaining unrelated transitive dependencies.
                if (artifact == 'javac') {
                    [GROUP, JITPACK_GROUP].each { group ->
                        ['base', 'zircon'].each { name -> dependency.exclude(group: group, module: name) }
                    }
                }
                if (!dependencies.any { it.group == extension.group.get() && it.name == artifact &&
                        it.version == extension.version.get() }) {
                    dependencies.add(dependency)
                }
            }
        }
        runtime.withDependencies { dependencies ->
            if (!dependencies.any { it.group == extension.group.get() && it.name == 'zircon' &&
                    it.version == extension.version.get() }) {
                dependencies.add(project.dependencies.create(module(extension, 'zircon').get()))
            }
        }

        project.configurations.configureEach { configuration ->
            if (configuration.name == 'annotationProcessor' || configuration.name.endsWith('AnnotationProcessor')) {
                configuration.extendsFrom(compiler)
            }
            if (configuration.name == 'implementation' || configuration.name.endsWith('Implementation')) {
                configuration.extendsFrom(runtime)
            }
        }

        project.tasks.withType(JavaCompile).configureEach { JavaCompile task ->
            ['zircon_optional_chain': 'ZrOptionalChain', 'zircon_ex_method': 'ZrExMethod',
             'zircon_temp_string': 'ZrString'].each { property, plugin ->
                if (!project.hasProperty(property) || project.property(property).toString().toBoolean()) {
                    def argument = '-Xplugin:' + plugin
                    if (!task.options.compilerArgs.contains(argument)) task.options.compilerArgs.add(argument)
                }
            }
            task.options.fork = true
            if (JVM_ARGUMENT_PROVIDERS) {
                task.options.forkOptions.jvmArgumentProviders.add(jvmArguments(project, task))
            }
        }
        if (!JVM_ARGUMENT_PROVIDERS) {
            // Gradle 6.7/6.8 has toolchains but no JVM argument providers. Wait for toolchain
            // configuration, then set the task inputs before execution (also for lazy tasks).
            project.gradle.projectsEvaluated {
                project.tasks.withType(JavaCompile).configureEach { JavaCompile task ->
                    def existing = task.options.forkOptions.jvmArgs ?: []
                    task.options.forkOptions.jvmArgs = (existing + jvmArguments(project, task).asArguments()).unique()
                }
            }
        }
    }

    private static ZirconJvmArguments jvmArguments(Project project, JavaCompile task) {
        def arguments = project.objects.newInstance(ZirconJvmArguments)
        // Older Android plugins leave javaCompiler unset and use the Gradle JVM's compiler.
        arguments.javaVersion.set(task.javaCompiler.map { it.metadata.languageVersion.asInt() }
                .orElse(Integer.parseInt(JavaVersion.current().majorVersion)))
        return arguments
    }

    private static Configuration dependencyBucket(Project project, String name, String description) {
        project.configurations.create(name) {
            it.description = description
            canBeResolved = false
            canBeConsumed = false
            visible = false
        }
    }

    private static def module(ZirconExtension extension, String artifact) {
        extension.group.zip(extension.version) { group, version -> "${group}:${artifact}:${version}".toString() }
    }

    private static List<String> resolveCoordinates(Project project) {
        // Parent script classloaders take precedence over a child's declaration.
        def ancestors = []
        for (def candidate = project; candidate != null; candidate = candidate.parent) ancestors.add(0, candidate)
        for (candidate in ancestors) {
            def classpath = candidate.buildscript.configurations.findByName('classpath')
            if (classpath == null) continue
            // Buildscript classpaths have already loaded their plugins, but Gradle may reset the
            // public configuration's state afterwards. Read selected modules, including plugin markers.
            // Application dependency configurations are never resolved here.
            if (classpath.state != Configuration.State.UNRESOLVED || !classpath.allDependencies.empty) {
                def selected = classpath.incoming.resolutionResult.allComponents.collect { it.id }.find {
                    it instanceof ModuleComponentIdentifier && isPluginModule(it.group, it.module)
                }
                if (selected != null) return [selected.group, selected.version]
            }
        }
        for (candidate in ancestors) {
            def declared = candidate.buildscript.configurations.findByName('classpath')?.allDependencies?.find {
                isPluginModule(it.group, it.name) && it.version
            }
            if (declared != null) return [declared.group, declared.version]
        }
        def metadata = new Properties()
        ZrPlugin.getResourceAsStream('/zircon-version.properties').withCloseable { metadata.load(it) }
        return [GROUP, metadata.getProperty('version')]
    }

    private static boolean isPluginModule(String group, String name) {
        name == 'gradle' && group in [GROUP, JITPACK_GROUP]
    }
}
