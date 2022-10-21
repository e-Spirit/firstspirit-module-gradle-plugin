package org.gradle.plugins.fsm.tasks.verification

import org.apache.maven.artifact.versioning.ComparableVersion
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.fsm.descriptor.textContent
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.TextElement
import org.redundent.kotlin.xml.parse
import java.io.ByteArrayInputStream
import java.util.zip.ZipFile

abstract class ValidateDescriptor: DefaultTask() {

    @TaskAction
    fun validateDescriptor() {
        val fsm = inputs.files.singleFile
        val entries = mutableListOf<String>()
        var descriptor = ""
        ZipFile(fsm).use { zip ->
            val descriptorPath = "META-INF/module-isolated.xml"

            for (entry in zip.entries()) {
                entries.add(entry.name)

                if (entry.name == descriptorPath) {
                    descriptor = zip.getInputStream(entry).use { it.bufferedReader().readText() }
                }
            }

            if (!entries.contains(descriptorPath)) {
                throw GradleException("Module descriptor '$descriptorPath' not found!")
            }

        }

        validate(descriptor, entries)
    }

    private fun validate(descriptor: String, files: List<String>) {
        if (descriptor.isBlank()) {
            throw GradleException("Module descriptor is empty!")
        }

        val xml = parse(ByteArrayInputStream(descriptor.toByteArray()))
        if (xml.nodeName != "module") {
            throw GradleException("Module descriptor must contain a single <module> tag!")
        }

        val name = elementRequired(xml, "name")
        validateCharactersForName(name)
        elementRequired(xml, "version")
        validateLicenseFile(xml, files)
        checkAllResources(xml, files)
        checkWebXmlFiles(xml, files)
    }

    private fun elementRequired(xml: Node, element: String): String {
        if (xml.filter(element).isEmpty()) {
            throw GradleException("<$element> is a required element.")
        }

        val children = xml.filter(element).single().children
        if ((children.isEmpty())) {
            throw GradleException("<$element> must not be empty!")
        }

        val text = (children[0] as TextElement).text
        if (text.isBlank()) {
            throw GradleException("<$element> must not be empty or blank!")
        }

        return text
    }


    private fun validateCharactersForName(name: String) {
        if (name.contains("\n")) {
            throw GradleException("<name> must not contain a line break.")
        }

        for (character in name) {
            if (!NAME_ALLOWED_CHARACTERS.contains(character)) {
                throw GradleException("<name> contains illegal character '$character'.")
            }
        }
    }


    private fun validateLicenseFile(xml: Node, files: List<String>) {
        val licenses = xml.filter("licenses").singleOrNull() ?: return

        if (licenses.children.isNotEmpty()) {
            val licenseFilename = licenses.textContent()
            if (!files.contains(licenseFilename)) {
                throw GradleException("License file '$licenseFilename' not found in FSM archive.")
            }
        }
    }

    private fun checkAllResources(xml: Node, files: List<String>) {
        xml.filter("resources").forEach { checkResources(it, files, "global resources") }

        val components = xml.filter("components").singleOrNull() ?: return

        components.children.filterIsInstance<Node>().forEach { component ->
            val componentName = componentName(component)
            component.filter("resources").forEach { checkResources(it, files, componentName) }
            component.filter("web-resources").forEach { checkResources(it, files, componentName) }
        }
    }

    private fun checkWebXmlFiles(xml: Node, files: List<String>) {
        val components = xml.filter("components").singleOrNull() ?: return

        components.filter("web-app").forEach { webApp ->
            val componentName = componentName(webApp)
            webApp.filter("web-xml").singleOrNull()?.let { webXml ->
                if (webXml.children.isEmpty()) {
                    throw GradleException("web.xml path must not be empty in $componentName.")
                }

                val path = webXml.textContent()

                if (path.endsWith("/")) {
                    throw GradleException("web.xml file '$path' must not be a directory for $componentName.")
                }

                if (!files.contains(path)) {
                    throw GradleException("web.xml file '$path' not found for $componentName in the FSM.")
                }
            }
        }
    }

    private fun checkResources(resources: Node, files: List<String>, source: String) {
        resources.filter("resource").forEach { resource ->
            val resourceName = resourceName(resource)

            if (resource.hasAttribute("minVersion") && resource.hasAttribute("maxVersion")) {
                val minVersion = ComparableVersion(resource.attributes["minVersion"] as String)
                val maxVersion = ComparableVersion(resource.attributes["maxVersion"] as String)
                if (minVersion > maxVersion) {
                    throw GradleException("Invalid version range for resource '$resourceName' in $source: " +
                            "$minVersion is greater than $maxVersion")
                }
            }

            if (resource.hasAttribute("version")) {
                if (resource.hasAttribute("minVersion")) {
                    val minVersion = ComparableVersion(resource.attributes["minVersion"] as String)
                    val version = ComparableVersion(resource.attributes["version"] as String)
                    if (version < minVersion) {
                        throw GradleException(
                            "Invalid version for resource '$resourceName' in $source: " +
                                    "$version is smaller than minVersion $minVersion"
                        )
                    }
                }

                if (resource.hasAttribute("maxVersion")) {
                    val maxVersion = ComparableVersion(resource.attributes["maxVersion"] as String)
                    val version = ComparableVersion(resource.attributes["version"] as String)
                    if (version > maxVersion) {
                        throw GradleException(
                            "Invalid version for resource '$resourceName' in $source: " +
                                    "$version is greater than maxVersion $maxVersion"
                        )
                    }
                }
            }

            if (resource.children.isEmpty()) {
                throw GradleException("No file specified for resource '$resourceName' in $source.")
            }

            val filename = resource.textContent()
            if (!files.contains(filename) && !files.contains("$filename/")) {
                throw GradleException("File '$filename' specified for resource '$resourceName' in" +
                        " $source but is not found in the FSM.")
            }
        }
    }

    private fun componentName(component: Node): String {
        val nameNode = component.filter("name").single()
        if (nameNode.children.isEmpty()) {
            throw GradleException("Component of type '${component.nodeName}' with unset or empty name detected.")
        }
        val name = nameNode.textContent()
        return "component of type '${component.nodeName}' with name '$name'"
    }

    private fun resourceName(resource: Node): String {
        if (resource.hasAttribute("name")) {
            val name = resource.attributes["name"] as String
            if (name.isNotBlank()) {
                return name
            }
        }

        return "<unnamed>"
    }

    companion object {
        const val NAME_ALLOWED_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789; ,_-"
    }

}