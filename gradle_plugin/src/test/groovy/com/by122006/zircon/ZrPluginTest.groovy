package com.by122006.zircon

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class ZrPluginTest {
    @Test
    void configuresExistingAndFutureJavaAndAndroidConfigurations() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ZrPlugin)
        project.pluginManager.apply('java')
        project.sourceSets.create('integrationTest')
        ['androidTestImplementation', 'debugAnnotationProcessor', 'androidTestAnnotationProcessor',
         'testDebugAnnotationProcessor'].each { project.configurations.create(it) }
        ['annotationProcessor', 'testAnnotationProcessor', 'integrationTestAnnotationProcessor',
         'debugAnnotationProcessor', 'androidTestAnnotationProcessor', 'testDebugAnnotationProcessor']
                .each { assertTrue(project.configurations.getByName(it).extendsFrom.contains(project.configurations.zirconCompiler)) }
        ['implementation', 'integrationTestImplementation', 'androidTestImplementation']
                .each { assertTrue(project.configurations.getByName(it).extendsFrom.contains(project.configurations.zirconRuntime)) }
        assertEquals(ZrPlugin.GROUP, project.zircon.group.get())
    }

    @Test
    void optionalChainToggleDoesNotReadTemplateStringToggle() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        project.ext.zircon_optional_chain = false
        project.ext.zircon_version = '9.8.7'
        project.pluginManager.apply(ZrPlugin)
        def args = project.tasks.compileJava.options.compilerArgs
        assertFalse(args.contains('-Xplugin:ZrOptionalChain'))
        assertTrue(args.contains('-Xplugin:ZrExMethod'))
        assertTrue(args.contains('-Xplugin:ZrString'))
        assertEquals('9.8.7', project.zircon.version.get())
    }

    @Test
    void acceptsCoordinatesAfterApplyAndPreservesOtherProcessors() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        project.dependencies.add('annotationProcessor', 'example:processor:1.0')
        project.tasks.compileJava.options.compilerArgs.add('-parameters')
        project.tasks.compileJava.options.forkOptions.jvmArgs = ['-Dexample=true']
        project.pluginManager.apply(ZrPlugin)
        project.zircon.group.set(ZrPlugin.JITPACK_GROUP)
        project.zircon.version.set('master-SNAPSHOT')
        assertTrue(project.configurations.annotationProcessor.dependencies.any { it.name == 'processor' })
        assertTrue(project.configurations.annotationProcessor.extendsFrom.contains(project.configurations.zirconCompiler))
        assertTrue(project.configurations.implementation.extendsFrom.contains(project.configurations.zirconRuntime))
        assertEquals(ZrPlugin.JITPACK_GROUP, project.zircon.group.get())
        assertEquals('master-SNAPSHOT', project.zircon.version.get())
        assertTrue(project.tasks.compileJava.options.compilerArgs.contains('-parameters'))
        assertEquals(['-Dexample=true'], project.tasks.compileJava.options.forkOptions.jvmArgs)
    }

    @Test
    void inheritsLegacyJitpackCoordinatesFromIntermediateProject() {
        def root = ProjectBuilder.builder().build()
        def middle = ProjectBuilder.builder().withParent(root).withName('middle').build()
        def child = ProjectBuilder.builder().withParent(middle).withName('child').build()
        middle.buildscript.dependencies.add('classpath', ZrPlugin.JITPACK_GROUP + ':gradle:abc1234')
        child.pluginManager.apply(ZrPlugin)
        assertEquals(ZrPlugin.JITPACK_GROUP, child.zircon.group.get())
        assertEquals('abc1234', child.zircon.version.get())
    }

    @Test
    void legacyCoordinateOverridesTakePrecedence() {
        def project = ProjectBuilder.builder().build()
        project.ext.zircon_group = ZrPlugin.JITPACK_GROUP
        project.ext.zircon_version = 'my-branch-SNAPSHOT'
        project.pluginManager.apply(ZrPlugin)
        assertEquals(ZrPlugin.JITPACK_GROUP, project.zircon.group.get())
        assertEquals('my-branch-SNAPSHOT', project.zircon.version.get())
    }

    @Test
    void jvmArgumentsFollowCompilerVersionAndAreStableAcrossReads() {
        def project = ProjectBuilder.builder().build()
        def arguments = project.objects.newInstance(ZirconJvmArguments)
        [8, 11, 17].each { version ->
            arguments.javaVersion.set(version)
            def first = arguments.asArguments() as List
            assertEquals(version == 8 ? 0 : 5, first.size())
            assertEquals(first, arguments.asArguments() as List)
        }
    }
}
