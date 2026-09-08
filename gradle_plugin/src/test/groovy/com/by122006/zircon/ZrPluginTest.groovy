package com.by122006.zircon

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

class ZrPluginTest {
    @Test
    void usesCentralCoordinatesAndPackagedVersion() {
        def project = ProjectBuilder.builder().build()
        project.pluginManager.apply('java')
        project.pluginManager.apply(ZrPlugin)
        def metadata = new Properties()
        ZrPlugin.getResourceAsStream('/zircon-version.properties').withCloseable { metadata.load(it) }
        ['annotationProcessor': ['javac'], 'implementation': ['zircon', 'base'],
         'testAnnotationProcessor': ['javac']].each { configuration, names ->
            def dependencies = project.configurations.getByName(configuration).dependencies
            assertEquals(names as Set, dependencies.collect { it.name } as Set)
            assertTrue(dependencies.every { it.group == ZrPlugin.GROUP && it.version == metadata.version })
        }
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
        assertEquals('9.8.7', project.configurations.annotationProcessor.dependencies.first().version)
    }
}
