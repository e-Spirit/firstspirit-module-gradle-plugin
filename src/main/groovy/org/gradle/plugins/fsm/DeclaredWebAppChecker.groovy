package org.gradle.plugins.fsm

import com.espirit.moddev.components.annotations.WebAppComponent
import org.gradle.api.Project

/**
 * Checks correspondence of classes annotated with {@link WebAppComponent} annotation to gradle subprojects
 * declared in {@link FSMPluginExtension#getWebApps()}. Reports web app annotations that do not have a corresponding
 * declaration or vice-versa.
 */
class DeclaredWebAppChecker {

    private List<Class<?>> _classes
    private Project _project

    private Set<String> _declaredProjectsWithoutAnnotation = null
    private Set<WebAppComponent> _webAppAnnotationWithoutDeclaration = null

    DeclaredWebAppChecker(Project project, Collection<Class<?>> webAppClasses) {
        _project = project
        _classes = new ArrayList<>(webAppClasses);
    }

    Set<String> getDeclaredProjectsWithoutAnnotation() {
        if (_declaredProjectsWithoutAnnotation == null) {
            scanWebApps()
        }
        Collections.unmodifiableSet(_declaredProjectsWithoutAnnotation)
    }

    Set<WebAppComponent> getWebAppAnnotationsWithoutDeclaration() {
        if (_webAppAnnotationWithoutDeclaration == null) {
            scanWebApps()
        }
        Collections.unmodifiableSet(_webAppAnnotationWithoutDeclaration)
    }

    private void scanWebApps() {
        def declaredWebapps = _project.extensions.getByType(FSMPluginExtension).webApps

        _declaredProjectsWithoutAnnotation = new HashSet<>(declaredWebapps.keySet())
        _webAppAnnotationWithoutDeclaration = new HashSet<>()

        _classes.forEach { webAppClass ->
            def annotation = Arrays.asList(webAppClass.annotations).find { it instanceof WebAppComponent } as WebAppComponent
            if (annotation != null) {
                def webAppName = annotation.name()
                if (declaredWebapps.containsKey(webAppName)) {
                    _declaredProjectsWithoutAnnotation.remove(webAppName)
                } else {
                    _webAppAnnotationWithoutDeclaration.add(annotation)
                }
            }
        }

    }
}
