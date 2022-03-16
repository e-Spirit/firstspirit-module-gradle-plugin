package org.gradle.plugins.fsm;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FSMPluginExtensionTest {

    private FSMPluginExtension testling;
    private Project project;

    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        project.getPlugins().apply(FSMPlugin.NAME);
        testling = new FSMPluginExtension(project);
    }

    @Test
    void testWebApps() {
        final Project webAppSubprojectA = ProjectBuilder.builder().withParent(project).withName("web_a").build();
        final Project webAppSubprojectB = ProjectBuilder.builder().withParent(project).withName("web_b").build();

        testling.webAppComponent("WebAppA", webAppSubprojectA);
        testling.webAppComponent(webAppSubprojectB);

        assertThat(testling.getWebApps()).containsEntry("WebAppA", webAppSubprojectA);
        assertThat(testling.getWebApps()).containsEntry("web_b", webAppSubprojectB);

        // Ensure the project has a compile dependency on the subprojects
        final Stream<Project> dependencyProjects = project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME).getDependencies().stream()
                .filter(dependency -> dependency instanceof ProjectDependency)
                .map(dependency -> ((ProjectDependency) dependency).getDependencyProject());
        assertThat(dependencyProjects).containsExactly(webAppSubprojectA, webAppSubprojectB);
    }

    @Test
    void testMinVersionIsDefault() {
        assertThat(testling.getAppendDefaultMinVersion()).isTrue();
    }


    @Test
    void testCanSetFsmDependencies() {
        final String fsmModuleName = "fsmModuleName";
        testling.setFsmDependencies(Collections.singletonList(fsmModuleName));
        assertThat(testling.getFsmDependencies()).contains(fsmModuleName);
    }


}
