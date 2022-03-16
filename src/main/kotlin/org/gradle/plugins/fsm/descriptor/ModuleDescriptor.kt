package org.gradle.plugins.fsm.descriptor

import io.github.classgraph.ClassGraph
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.fsm.FSMPluginExtension
import org.gradle.plugins.fsm.classloader.FsmComponentClassLoader
import org.redundent.kotlin.xml.*
import java.io.ByteArrayInputStream

class ModuleDescriptor(private val project: Project) {

    private val pluginExtension = project.extensions.getByType(FSMPluginExtension::class.java)

    val moduleClass: ModuleComponent
    val components: Components
    val resources: Resources
    val node: Node
    val dependencies: List<Node>

    init {
        val classGraph = ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
        configureComponentScan(classGraph)

        val classLoader = FsmComponentClassLoader(project)
        val componentsNode: Node

        classGraph.scan().use {
            components = Components(project, it, classLoader)
            componentsNode = components.node
            resources = Resources(project, components.webXmlPaths)
            moduleClass = ModuleComponent(it, classLoader)
            dependencies = pluginExtension.fsmDependencies.map { xml("depends") { -it } }

            node = xml("module") {
                includeXmlProlog = true
                version = XmlVersion.V10
                moduleInformation(this)
                "dependencies" {
                    dependencies.forEach(this::addNode)
                }
                moduleClass.nodes.forEach(this::addNode)
                addNode(componentsNode)
                addNode(resources.node)
            }
        }
    }

    override fun toString(): String {
        return node.toString(PRINT_OPTIONS)
    }

    fun reformat(xml: String): String {
        val bytes = ByteArrayInputStream(xml.toByteArray())
        val parsedNode = parse(bytes)
        parsedNode.includeXmlProlog = true
        return parsedNode.toString(PRINT_OPTIONS)
    }

    fun fsmDependencies(): String {
        return dependencies.joinToString("\n") { it.toString(PRINT_OPTIONS) }
    }

    private fun configureComponentScan(classGraph: ClassGraph) {
        // Get current project + all subprojects for which we have a compile dependency and get their
        // jar tasks' classpath
        val compileClasspathConfiguration = project.configurations.getByName("compileClasspath")
        val projectDependencies = compileClasspathConfiguration.allDependencies.withType(ProjectDependency::class.java)
            .map { it.dependencyProject }
            .filter { it.plugins.hasPlugin(JavaPlugin::class.java) }
        val allProjects = listOf(project) + projectDependencies
        val jarFiles = allProjects.map {
            val jarTask = it.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
            jarTask.archiveFile.get().asFile
        }
        classGraph.overrideClasspath(jarFiles)
    }


    private fun moduleInformation(descriptor: Node) {
        with(descriptor) {
            "name" { -(pluginExtension.moduleName ?: project.name) }
            "version" { -"${project.version}" }
            "displayname" { -(pluginExtension.displayName ?: project.name) }
            "description" { -(project.description ?: project.name) }
            "vendor" { -(pluginExtension.vendor ?: "") }
            "licenses" { -"META-INF/licenses.csv" }
        }
    }

    companion object {
        private val PRINT_OPTIONS = PrintOptions(singleLineTextElements = true)
    }
}