package com.by122006.zircon;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Dependency;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maven插件目标：配置编译器参数并添加依赖
 */
@Mojo(name = "configure-zircon", defaultPhase = LifecyclePhase.PROCESS_SOURCES) // 插件目标名称
public class ZrMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(property = "zirconExMethod", defaultValue = "true")
    protected boolean zirconExMethod; // 是否启用ZrExMethod插件

    @Parameter(property = "zirconTempString", defaultValue = "true")
    protected boolean zirconTempString; // 是否启用ZrString插件

    @Parameter(property = "zirconVersion", defaultValue = "latest.release")
    protected String zirconVersion; // Zircon库版本

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().warn("ZrMojo");
        // 配置编译器参数
        configureCompilerArgs();

        // 添加依赖
        addDependencies();
    }

    private void configureCompilerArgs() {
        List<String> compilerArgs = new ArrayList<>();

        if (zirconExMethod) {
            compilerArgs.add("-Xplugin:ZrExMethod");
        }
        if (zirconTempString) {
            compilerArgs.add("-Xplugin:ZrString");
        }

        // 如果Java版本 >= 9，添加额外的JVM参数
        String javaVersion = System.getProperty("java.version");
        int majorVersion = Integer.parseInt(javaVersion.split("\\.")[0]);
        if (majorVersion >= 9) {
            compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
            compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED");
            compilerArgs.add("--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED");
            compilerArgs.add("--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED");
            compilerArgs.add("--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED");
        }
        getLog().info("project:" + project);
        getLog().info("zirconVersion:" + zirconVersion);
        getLog().info("properties:" + project.getProperties().entrySet().stream().map(a->a.getKey()+":"+a.getValue()).collect(Collectors.joining("; ")));
        // 将编译器参数设置到Maven编译器插件
        project.getProperties().put("maven.compiler.compilerArgs", String.join(" ", compilerArgs));
        getLog().info("properties:" + project.getProperties().entrySet().stream().map(a->a.getKey()+":"+a.getValue()).collect(Collectors.joining("; ")));

//        project.getProperties().setProperty("Xplugin")
    }

    private void addDependencies() {
        // 添加annotationProcessor依赖
        Dependency annotationProcessorDependency = new Dependency();
        annotationProcessorDependency.setGroupId("com.github.122006.Zircon");
        annotationProcessorDependency.setArtifactId("javac");
        annotationProcessorDependency.setVersion(zirconVersion);
        project.getDependencies().add(annotationProcessorDependency);

        // 添加implementation依赖
        Dependency implementationDependency = new Dependency();
        implementationDependency.setGroupId("com.github.122006.Zircon");
        implementationDependency.setArtifactId("zircon");
        implementationDependency.setVersion(zirconVersion);
        project.getDependencies().add(implementationDependency);

        // 尝试添加androidTestImplementation依赖
        try {
            Dependency androidTestDependency = new Dependency();
            androidTestDependency.setGroupId("com.github.122006.Zircon");
            androidTestDependency.setArtifactId("javac");
            androidTestDependency.setVersion(zirconVersion);
            project.getDependencies().add(androidTestDependency);
        } catch (Exception ignored) {
            getLog().warn("Failed to add androidTestImplementation dependency.");
        }

        // 尝试添加testAnnotationProcessor依赖
        try {
            Dependency testAnnotationProcessorDependency = new Dependency();
            testAnnotationProcessorDependency.setGroupId("com.github.122006.Zircon");
            testAnnotationProcessorDependency.setArtifactId("javac");
            testAnnotationProcessorDependency.setVersion(zirconVersion);
            project.getDependencies().add(testAnnotationProcessorDependency);
        } catch (Exception ignored) {
            getLog().warn("Failed to add testAnnotationProcessor dependency.");
        }
    }
}