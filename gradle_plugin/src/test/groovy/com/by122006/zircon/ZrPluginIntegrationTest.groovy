package com.by122006.zircon

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import javax.tools.ToolProvider
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import static org.junit.Assert.*
import static org.junit.Assume.assumeTrue

/** Exercises the shipped jar through a local Maven repository, not an injected TestKit classpath. */
class ZrPluginIntegrationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()

    @Test
    void jitpackBranchCompilesOn8And11And17AndReusesConfigurationCache() {
        def repository = repository(ZrPlugin.JITPACK_GROUP, 'master-SNAPSHOT')
        addProcessor(repository)
        [8, 11, 17].each { javaVersion ->
            def project = fixture(repository, ZrPlugin.JITPACK_GROUP, 'master-SNAPSHOT', javaVersion)
            write(project, 'build.gradle', new File(project, 'build.gradle').text.replace('assert zircon.group', '''
sourceSets { integrationTest {} }
dependencies { annotationProcessor 'fixture:processor:1.0' }
assert configurations.annotationProcessor.files.any { it.name == 'processor-1.0.jar' }
assert zircon.group'''))
            write(project, 'src/main/java/smoke/VerifyProcessor.java',
                    'package smoke; class VerifyProcessor { fixture.GeneratedMarker marker; }')
            write(project, 'src/test/java/ExampleTest.java',
                    'class ExampleTest { String value = $"test ${1 + 2}"; }')
            write(project, 'src/integrationTest/java/ExampleIntegration.java',
                    'class ExampleIntegration { String value = $"integration ${1 + 2}"; }')
            def first = runner(project, 'smoke', 'compileTestJava', 'compileIntegrationTestJava', '--configuration-cache').build()
            assertEquals(first.output, TaskOutcome.SUCCESS, first.task(':compileJava').outcome)
            assertEquals(first.output, TaskOutcome.SUCCESS, first.task(':compileTestJava').outcome)
            assertEquals(first.output, TaskOutcome.SUCCESS, first.task(':compileIntegrationTestJava').outcome)
            assertTrue(first.output, first.output.contains('smoke passed on Java'))
            def second = runner(project, 'smoke', 'compileTestJava', 'compileIntegrationTestJava', '--configuration-cache').build()
            assertEquals(second.output, TaskOutcome.UP_TO_DATE, second.task(':compileJava').outcome)
            assertTrue(second.output, second.output.contains('Configuration cache entry reused.'))
            assertTrue(second.output, second.output.contains('smoke passed on Java'))
        }
    }

    @Test
    void legacyGradleCompilesOn8And11() {
        def installation = System.getProperty('zircon.test.legacyGradleHome')
        def gradleVersion = System.getProperty('zircon.test.legacyGradleVersion')
        assumeTrue('Set -PzirconLegacyGradleHome or -PzirconLegacyGradleVersion to verify Gradle 6.7/6.8',
                !!installation || !!gradleVersion)
        def repository = repository(ZrPlugin.JITPACK_GROUP, 'legacy-SNAPSHOT')
        [8, 11].each { version ->
            def project = fixture(repository, ZrPlugin.JITPACK_GROUP, 'legacy-SNAPSHOT', version)
            write(project, 'gradle.properties', 'org.gradle.java.home=' +
                    System.getProperty(version == 8 ? 'zircon.test.legacyJava8Home' : 'zircon.test.legacyJavaHome')
                            .replace('\\', '/') + '\n')
            def legacy = runner(project, 'smoke')
            if (installation) legacy.withGradleInstallation(new File(installation))
            else legacy.withGradleVersion(gradleVersion)
            def result = legacy.build()
            assertTrue(result.output, result.output.contains('smoke passed on Java'))
        }
    }

    @Test
    void android4CompilesMainUnitAndInstrumentationSources() {
        def sdk = System.getProperty('zircon.test.androidSdk')
        assumeTrue('Set -PzirconAndroidSdk to verify AGP 4.0.2 with a real Android SDK', !!sdk)
        assumeTrue('AGP 4.0.2 smoke requires platform android-29 and build-tools 29.0.2',
                new File(sdk, 'platforms/android-29/android.jar').isFile() &&
                        (new File(sdk, 'build-tools/29.0.2/dx.bat').isFile() ||
                         new File(sdk, 'build-tools/29.0.2/dx').isFile()))
        def repository = repository(ZrPlugin.JITPACK_GROUP, 'android-SNAPSHOT')
        def project = temporary.newFolder()
        write(project, 'settings.gradle', "rootProject.name = 'android-smoke'\n")
        write(project, 'local.properties', 'sdk.dir=' + sdk.replace('\\', '/') + '\n')
        write(project, 'gradle.properties', 'org.gradle.java.home=' +
                System.getProperty('zircon.test.legacyJavaHome').replace('\\', '/') + '\n')
        write(project, 'src/main/AndroidManifest.xml', '<manifest package="smoke" />')
        write(project, 'build.gradle', """
buildscript {
    repositories {
        maven { url '${repository.toURI()}' }
        google()
        mavenCentral()
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public/' }
    }
    dependencies {
        classpath '${ZrPlugin.JITPACK_GROUP}:gradle:android-SNAPSHOT'
        classpath 'com.android.tools.build:gradle:4.0.2'
    }
}
apply plugin: 'zircon'
apply plugin: 'com.android.library'
repositories { maven { url '${repository.toURI()}' }; google(); mavenCentral() }
android {
    compileSdkVersion 29
    buildToolsVersion '29.0.2'
    defaultConfig { minSdkVersion 21; targetSdkVersion 28 }
    compileOptions { sourceCompatibility JavaVersion.VERSION_1_8; targetCompatibility JavaVersion.VERSION_1_8 }
}
tasks.register('verifyScopes') {
    doLast {
        ['debugRuntimeClasspath', 'debugUnitTestRuntimeClasspath', 'debugAndroidTestRuntimeClasspath'].each { name ->
            def coordinates = configurations[name].incoming.resolutionResult.allComponents
                    .collect { it.moduleVersion?.toString() }.findAll { it?.contains('.Zircon:') } as Set
            assert coordinates == ['${ZrPlugin.JITPACK_GROUP}:zircon:android-SNAPSHOT'] as Set
        }
    }
}
""")
        ['Main', 'TextExtensions'].each { name ->
            write(project, 'src/main/java/smoke/' + name + '.java',
                    getClass().getResource('/smoke/smoke/' + name + '.java').getText('UTF-8'))
        }
        write(project, 'src/test/java/UnitSource.java', 'class UnitSource { String value = $"test ${1 + 2}"; }')
        write(project, 'src/androidTest/java/InstrumentedSource.java',
                'class InstrumentedSource { String value = $"android ${1 + 2}"; }')
        def compileTasks = ['compileDebugJavaWithJavac', 'compileDebugUnitTestJavaWithJavac',
                            'compileDebugAndroidTestJavaWithJavac']
        def legacy = runner(project, false, *(compileTasks + ['verifyScopes']))
        def installation = System.getProperty('zircon.test.legacyGradleHome')
        if (installation) legacy.withGradleInstallation(new File(installation))
        else legacy.withGradleVersion(System.getProperty('zircon.test.legacyGradleVersion') ?: '6.7.1')
        def result = legacy.build()
        compileTasks.each { assertEquals(result.output, TaskOutcome.SUCCESS, result.task(':' + it).outcome) }
    }

    @Test
    void usesResolvedPluginVersionInheritedThroughMultipleProjects() {
        def repository = repository(ZrPlugin.JITPACK_GROUP, 'selected-commit')
        def project = temporary.newFolder()
        write(project, 'settings.gradle', "rootProject.name = 'inheritance'\ninclude 'middle:app'\n")
        write(project, 'build.gradle', """
buildscript {
    repositories { maven { url '${repository.toURI()}' } }
    configurations.classpath.resolutionStrategy.force '${ZrPlugin.JITPACK_GROUP}:gradle:selected-commit'
    dependencies { classpath '${ZrPlugin.JITPACK_GROUP}:gradle:declared-commit' }
}
""")
        write(project, 'middle/app/build.gradle', """
apply plugin: 'zircon'
apply plugin: 'java'
repositories { maven { url '${repository.toURI()}' } }
${assertCoordinates(ZrPlugin.JITPACK_GROUP, 'selected-commit')}
""")
        runner(project, ':middle:app:help').build()
    }

    @Test
    void centralCoordinatesWorkAndCanBeOverriddenAfterApply() {
        def repository = repository(ZrPlugin.GROUP, '3.3.3')
        def central = fixture(repository, ZrPlugin.GROUP, '3.3.3', 17)
        assertTrue(runner(central, 'smoke').build().output.contains('smoke passed on Java'))

        addModules(repository, ZrPlugin.JITPACK_GROUP, 'test-commit')
        def overridden = fixture(repository, ZrPlugin.GROUP, '3.3.3', 11,
                ZrPlugin.JITPACK_GROUP, 'test-commit')
        assertTrue(runner(overridden, 'smoke').build().output.contains('smoke passed on Java'))
    }

    @Test
    void ioGithubAliasPreservesBranchAndCommitCoordinates() {
        // Different from the packaged release: falling back to jar metadata must fail this test.
        ['master-SNAPSHOT', '79c0c0910f'].each { version ->
            def repository = repository(ZrPlugin.GROUP, version)
            def project = fixture(repository, ZrPlugin.GROUP, version, 11)
            def result = runner(project, 'smoke').build()
            assertEquals(result.output, TaskOutcome.SUCCESS, result.task(':compileJava').outcome)
            assertTrue(result.output, result.output.contains('smoke passed on Java'))
        }
    }

    @Test
    void pluginDslResolvesThePluginImplementationCoordinates() {
        def repository = repository(ZrPlugin.JITPACK_GROUP, 'plugin-dsl-commit')
        artifact(repository, 'zircon', 'zircon.gradle.plugin', 'plugin-dsl-commit', null,
                dependency(ZrPlugin.JITPACK_GROUP, 'gradle', 'plugin-dsl-commit'))
        def project = temporary.newFolder()
        write(project, 'settings.gradle', """
pluginManagement { repositories { maven { url '${repository.toURI()}' } } }
rootProject.name = 'plugin-dsl'
""")
        write(project, 'build.gradle', """
plugins { id 'zircon' version 'plugin-dsl-commit'; id 'java' }
repositories { maven { url '${repository.toURI()}' } }
${assertCoordinates(ZrPlugin.JITPACK_GROUP, 'plugin-dsl-commit')}
""")
        runner(project, 'help').build()
    }

    @Test
    void invalidTemplateFailsWithSourceDiagnosticThroughGradle() {
        def repository = repository(ZrPlugin.JITPACK_GROUP, 'negative-commit')
        def project = fixture(repository, ZrPlugin.JITPACK_GROUP, 'negative-commit', 11)
        write(project, 'src/main/java/Broken.java', 'class Broken { String value = $"hello ${1"; }')
        def result = runner(project, 'compileJava').buildAndFail()
        assertEquals(result.output, TaskOutcome.FAILED, result.task(':compileJava').outcome)
        assertTrue(result.output, result.output.contains('Broken.java:1:'))
        assertTrue(result.output, result.output.contains('ZR1002'))
        assertFalse(result.output, result.output.contains('ConcurrentModificationException'))
        assertFalse(result.output, result.output.contains('An exception has occurred in the compiler'))
        assertFalse(result.output, result.output.contains('ZR9001'))
    }

    private File repository(String group, String version) {
        def directory = temporary.newFolder()
        artifact(directory, group, 'gradle', version, new File(System.getProperty('zircon.fixture.gradle')))
        addModules(directory, group, version)
        return directory
    }

    private void addModules(File repository, String group, String version) {
        // Deliberately stale POM edges reproduce JitPack artifacts built without changing project.version.
        def stale = dependency(ZrPlugin.GROUP, 'base', '3.3.3') + dependency(ZrPlugin.GROUP, 'zircon', '3.3.3')
        stale += dependency('fixture', 'compiler-helper', '1.0')
        artifact(repository, 'fixture', 'compiler-helper', '1.0', emptyJar())
        ['javac', 'base', 'zircon'].each { module ->
            artifact(repository, group, module, version, new File(System.getProperty('zircon.fixture.' + module)),
                    module == 'javac' ? stale : '')
        }
    }

    private File fixture(File repository, String pluginGroup, String pluginVersion, int javaVersion,
                         String moduleGroup = pluginGroup, String moduleVersion = pluginVersion) {
        def directory = temporary.newFolder()
        write(directory, 'settings.gradle', "rootProject.name = 'gradle-smoke'\n")
        write(directory, 'build.gradle', """
buildscript {
    repositories { maven { url '${repository.toURI()}' } }
    dependencies { classpath '${pluginGroup}:gradle:${pluginVersion}' }
}
apply plugin: 'zircon'
apply plugin: 'java'
${moduleGroup == pluginGroup && moduleVersion == pluginVersion ? '' : "zircon { group.set('${moduleGroup}'); version.set('${moduleVersion}') }"}
repositories { maven { url '${repository.toURI()}' } }
java { toolchain { languageVersion = JavaLanguageVersion.of(${javaVersion}) } }
${assertCoordinates(moduleGroup, moduleVersion)}
tasks.register('smoke', JavaExec) {
    dependsOn classes
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'smoke.Main'
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(${javaVersion}) }
}
""")
        ['Main', 'TextExtensions'].each { name ->
            write(directory, 'src/main/java/smoke/' + name + '.java',
                    getClass().getResource('/smoke/smoke/' + name + '.java').getText('UTF-8'))
        }
        return directory
    }

    private static String assertCoordinates(String group, String version) {
        """
assert zircon.group.get() == '${group}'
assert zircon.version.get() == '${version}'
assert !configurations.annotationProcessor.files.empty
def compilerCoordinates = configurations.annotationProcessor.incoming.resolutionResult.allComponents
        .collect { it.moduleVersion?.toString() }.findAll { it != null && !it.startsWith(':') } as Set
assert compilerCoordinates.containsAll(['${group}:javac:${version}', '${group}:base:${version}', '${group}:zircon:${version}'])
assert compilerCoordinates.contains('fixture:compiler-helper:1.0')
assert compilerCoordinates.findAll { it.contains('.Zircon:') }.size() == 3
def runtimeCoordinates = configurations.runtimeClasspath.incoming.resolutionResult.allComponents
        .collect { it.moduleVersion?.toString() }.findAll { it?.contains('.Zircon:') } as Set
assert runtimeCoordinates == ['${group}:zircon:${version}'] as Set
"""
    }

    private GradleRunner runner(File project, String... arguments) {
        runner(project, true, arguments)
    }

    private GradleRunner runner(File project, boolean offline, String... arguments) {
        def options = ['--gradle-user-home', System.getProperty('zircon.test.gradleUserHome')] +
                arguments.toList() + ['--stacktrace'] + (offline ? ['--offline'] : []) + ['--max-workers=2',
                                           '-Dorg.gradle.java.installations.auto-download=false']
        def installations = System.getProperty('zircon.test.javaInstallations')
        if (installations) options.add('-Dorg.gradle.java.installations.paths=' + installations)
        GradleRunner.create().withProjectDir(project)
                .withGradleInstallation(new File(System.getProperty('zircon.test.gradleHome')))
                .withArguments(options)
    }

    private static void write(File directory, String path, String contents) {
        def file = new File(directory, path)
        file.parentFile.mkdirs()
        file.setText(contents, 'UTF-8')
    }

    private static String dependency(String group, String artifact, String version) {
        "<dependency><groupId>${group}</groupId><artifactId>${artifact}</artifactId><version>${version}</version></dependency>"
    }

    private static void artifact(File repository, String group, String name, String version, File jar, String dependencies = '') {
        def directory = new File(repository, group.replace('.', '/') + '/' + name + '/' + version)
        directory.mkdirs()
        write(directory, name + '-' + version + '.pom', """
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>${group}</groupId><artifactId>${name}</artifactId><version>${version}</version>
<packaging>${jar == null ? 'pom' : 'jar'}</packaging><dependencies>${dependencies}</dependencies></project>
""")
        if (jar != null) new File(directory, name + '-' + version + '.jar').bytes = jar.bytes
    }

    private File emptyJar() {
        def jar = temporary.newFile()
        new JarOutputStream(new FileOutputStream(jar)).close()
        return jar
    }

    private void addProcessor(File repository) {
        def directory = temporary.newFolder()
        write(directory, 'MarkerProcessor.java', '''
import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import java.util.*;
import java.io.*;
@SupportedAnnotationTypes("*")
public class MarkerProcessor extends AbstractProcessor {
    private boolean generated;
    public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (!generated && !round.processingOver()) {
            generated = true;
            try (Writer writer = processingEnv.getFiler().createSourceFile("fixture.GeneratedMarker").openWriter()) {
                writer.write("package fixture; public class GeneratedMarker {}");
            } catch (IOException ex) { throw new RuntimeException(ex); }
        }
        return false;
    }
}
''')
        assertEquals(0, ToolProvider.systemJavaCompiler.run(null, null, null, '-source', '8', '-target', '8',
                '-d', directory.absolutePath, new File(directory, 'MarkerProcessor.java').absolutePath))
        def jar = temporary.newFile()
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { output ->
            output.putNextEntry(new JarEntry('MarkerProcessor.class'))
            output.write(new File(directory, 'MarkerProcessor.class').bytes)
            output.closeEntry()
            output.putNextEntry(new JarEntry('META-INF/services/javax.annotation.processing.Processor'))
            output.write('MarkerProcessor\n'.getBytes('UTF-8'))
            output.closeEntry()
        }
        artifact(repository, 'fixture', 'processor', '1.0', jar)
    }
}
