package com.by122006.zircon;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;


@Mojo(name = "zircon")
public class PluginMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    @Parameter(property = "zircon.version", defaultValue = "3.+", readonly = true)
    private String zrVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final String groupId = "com.github.122006.Zircon";
        Predicate<String> predicate = (artifactId) -> {
            final Stream<Dependency> stream = project.getDependencies().stream().map(Dependency.class::cast);
            return stream.anyMatch((a) -> Objects.equals(a.getGroupId(), groupId) && Objects.equals(a.getArtifactId(), artifactId));
        };
        if (predicate.test("zircon")) {
            final Dependency dependency = new Dependency();
            dependency.setGroupId(groupId);
            dependency.setArtifactId("zircon");
            dependency.setVersion(zrVersion);
            project.getDependencyManagement().addDependency(dependency);
        }

        final Stream<Plugin> stream = project.getBuild().getPluginManagement().getPlugins().stream().map(Plugin.class::cast);
        final Optional<Plugin> first = stream.filter((a) -> Objects.equals(a.getGroupId(), "org.apache.maven.plugins") && Objects.equals(a.getArtifactId(), "maven-compiler-plugin"))
                .findFirst();
        if (first.isPresent()) {
            if (predicate.test("javac")) {
                final Dependency dependency = new Dependency();
                dependency.setGroupId(groupId);
                dependency.setArtifactId("javac");
                dependency.setVersion(zrVersion);
                dependency.setScope("compile");
                project.getDependencyManagement().addDependency(dependency);
            }
            if (predicate.test("base")) {
                final Dependency dependency = new Dependency();
                dependency.setGroupId(groupId);
                dependency.setArtifactId("base");
                dependency.setVersion(zrVersion);
                dependency.setScope("compile");
                project.getDependencyManagement().addDependency(dependency);
            }
            final Plugin plugin = first.get();
            Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
            if (configuration == null) {
                configuration = new Xpp3Dom("configuration");
            }
            BiFunction<Xpp3Dom, Predicate<Xpp3Dom>, Xpp3Dom> findXpp3Dom = (xpp3Dom, p) -> {
                for (int i = 0; i < xpp3Dom.getChildCount(); i++) {
                    if (p.test(xpp3Dom.getChild(i))) {
                        return xpp3Dom.getChild(i);
                    }
                }
                return null;
            };
            Xpp3Dom compilerArgs = findXpp3Dom.apply(configuration, p -> Objects.equals(p.getName(), "compilerArgs"));
            if (compilerArgs == null) {
                compilerArgs = new Xpp3Dom("compilerArgs");
                configuration.addChild(compilerArgs);
            }
            if (findXpp3Dom.apply(compilerArgs, p -> Objects.equals(p.getName(), "arg") && Objects.equals(p.getName(), "-Xplugin:ZrExMethod")) == null) {
                final Xpp3Dom arg = new Xpp3Dom("arg");
                arg.setValue("-Xplugin:ZrExMethod");
                compilerArgs.addChild(arg);
            }
            if (findXpp3Dom.apply(compilerArgs, p -> Objects.equals(p.getName(), "arg") && Objects.equals(p.getName(), "-Xplugin:ZrString")) == null) {
                final Xpp3Dom arg = new Xpp3Dom("arg");
                arg.setValue("-Xplugin:ZrString");
                compilerArgs.addChild(arg);
            }
            plugin.setConfiguration(configuration);
        }
    }
}
