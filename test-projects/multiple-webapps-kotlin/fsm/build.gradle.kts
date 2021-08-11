import java.util.zip.ZipFile

plugins {
	id("de.espirit.firstspirit-module")
}

dependencies {
	fsProvidedCompile("de.espirit.firstspirit:fs-isolated-runtime:5.2.200910")
	fsWebCompile("joda-time:joda-time:2.9")
	fsModuleCompile("org.joda:joda-convert:2.1.2")
}

firstSpiritModule {
	moduleName = "Test Multiple WebApps"
	webAppComponent("WebAppA", project(":web_a"))
	webAppComponent("WebAppB", project(":web_b"))
}

// Test tasks and methods

/**
 * Asserts that a resource entry in the module xml is also located in the lib directory
 * @param fsm               The FSM archive
 * @param moduleXmlFilename The filename of the module.xml, either META-INF/module.xml or META-INF/module-isolated.xml
 */
fun testResourceEntries(fsm: java.util.zip.ZipFile, moduleXmlFilename: String) {
	// Test if all resource entries in module.xml are present in the archive
	val moduleXml = fsm.getEntry(moduleXmlFilename)
	fsm.getInputStream(moduleXml).bufferedReader().lines().forEach { line ->
		if (line.trim().startsWith("<resource ")) {
			// Trim <resource> tags from filename and test if resource exists
			val resourceFile = line.substring(line.indexOf(">") + 1).replace("</resource>", "")
			assert(fsm.getEntry(resourceFile) != null)
		}
	}
}

/**
 * Gets the text between a `<web-app>` and `</web-app>` tag for a given web-app
 * @param fsm               The FSM archive
 * @param moduleXmlFilename The filename of the module.xml, either META-INF/module.xml or META-INF/module-isolated.xml
 * @param webAppName        The name of the webapp
 * @return A list containing the lines between the `<web-app>` and `</web-app>` tag of a web-app with the name `webAppName`
 */
fun getWebAppXml(fsm: java.util.zip.ZipFile, moduleXmlFilename: String, webAppName: String): List<String> {
	val moduleXmlText = fsm.getInputStream(fsm.getEntry(moduleXmlFilename)).bufferedReader().lines()
	var currentWebAppTag = mutableListOf<String>()
	var inWebAppTag = false
	for (line in moduleXmlText) {
		val trimmed = line.trim()
		if (!inWebAppTag && trimmed.startsWith("<web-app")) {
			currentWebAppTag = mutableListOf()
			inWebAppTag = true
		} else if (inWebAppTag) {
			if (trimmed == "</web-app>") {
				return currentWebAppTag
			} else if (trimmed.startsWith("<name>")) {
				val name = trimmed.substring(trimmed.indexOf(">") + 1).replace("</name>", "")
				if (name != webAppName) {
					inWebAppTag = false
				}
			}
			currentWebAppTag.add(line)
		}
	}
	return listOf()
}

fun assertWebAppXml(webAppXmlLines: List<String>) {
	val webAppResources = webAppXmlLines.filter { it.startsWith("<resource ") }
	val resourceNamePattern = """name\s*=\s*"([^"]+)"""".toRegex()
	webAppResources.forEach { resource ->
		val resourceName = resourceNamePattern.find(resource)?.groupValues?.get(1) ?: error("resource name not found")
		// Each resource should occur only once
		assert(webAppResources.count { resourceNamePattern.find(it)?.groupValues?.get(1) == resourceName } == 1)
	}
}

fun extractNameAndVersion(resource: String): Pair<String, String> {
	val extractAttributeValue = { attribute: String ->
		val regex = (attribute + """\s*=\s*"([^"]+)"""").toRegex()
		regex.find(resource)!!.groupValues[1]
	}

	return Pair(extractAttributeValue("name"), extractAttributeValue("version"))
}

fun assertFsmResourcesFolder(project: Project, fsm: java.util.zip.ZipFile) {
	// Tests if each file in a project's fsm-resource folder exists
	val fsmResourcesPath = project.projectDir.resolve("src/main/fsm-resources")
	if (fsmResourcesPath.exists()) {
		fsmResourcesPath.walk().forEach {
			assert(fsm.getEntry(fsmResourcesPath.toRelativeString(it)) != null)
		}
	}
}

val testModuleXml by tasks.creating {
	dependsOn(tasks.assembleFSM)
	doLast {
		// Tests the contents of the module xml
		val fsmFile = tasks.assembleFSM.get().archiveFile.get().asFile
		ZipFile(fsmFile).use { fsm ->
			// Ensure all <resource> entries in module-xml are in lib directory
			testResourceEntries(fsm, "META-INF/module.xml")
			testResourceEntries(fsm, "META-INF/module-isolated.xml")

			// Test specific web resources

			// WebApp A
			val webAppAXml = getWebAppXml(fsm, "META-INF/module-isolated.xml", "WebAppA").map { it.trim() }
			assertWebAppXml(webAppAXml)

			// Resources of Web-App A
			val webAppAResources = webAppAXml.filter { it.startsWith("<resource ")}.map { extractNameAndVersion(it) }
			assert(webAppAResources.contains(Pair("multiple-webapps:web_a", "0.0.1")))
			assert(webAppAResources.contains(Pair("org.joda:joda-convert", "2.1.2")))
			assert(webAppAResources.contains(Pair("joda-time:joda-time", "2.10")))
			assert(webAppAResources.contains(Pair("org.slf4j:slf4j-api", "1.7.25")))

			// Don't want resources of the other subproject, no matter the version
			val webAppAResourceNames = webAppAResources.map { it.first } // Get names only
			assert(!webAppAResourceNames.contains("multiple-webapps:web_b"))
			assert(!webAppAResourceNames.contains("de.espirit.firstspirit:fs-isolated-runtime"))
			assert(!webAppAResourceNames.contains("com.fasterxml.jackson.core:jackson-databind"))
			assert(!webAppAResourceNames.contains("com.fasterxml.jackson.core:jackson-core"))
			assert(!webAppAResourceNames.contains("com.fasterxml.jackson.core:jackson-annotations"))


			// WebApp B
			val webAppBXml = getWebAppXml(fsm, "META-INF/module-isolated.xml", "WebAppB").map { it.trim() }
			assertWebAppXml(webAppBXml)

			// Resources of Web-App B
			val webAppBResources = webAppBXml.filter { it.startsWith("<resource ")}.map { extractNameAndVersion(it) }
			assert(webAppBResources.contains(Pair("multiple-webapps:web_b", "0.0.1")))
			assert(webAppBResources.contains(Pair("joda-time:joda-time", "2.10")))
			assert(webAppBResources.contains(Pair("org.slf4j:slf4j-api", "1.7.25")))
			assert(webAppBResources.contains(Pair("com.fasterxml.jackson.core:jackson-core", "2.10.0")))
			assert(webAppBResources.contains(Pair("com.fasterxml.jackson.core:jackson-databind", "2.10.0")))
			assert(webAppBResources.contains(Pair("com.fasterxml.jackson.core:jackson-annotations", "2.10.0")))

			// Don't want resources of Web-App A
			val webAppBResourceNames = webAppBResources.map { it.first }
			assert(!webAppBResourceNames.contains("multiple-webapps:web_a"))
			assert(!webAppAResourceNames.contains("de.espirit.firstspirit:fs-isolated-runtime"))
			assert(!webAppBResourceNames.contains("org.joda:joda-convert"))

			// Test Fsm-Resources folder
			assertFsmResourcesFolder(project, fsm)
			assertFsmResourcesFolder(project(":web_a"), fsm)
			assertFsmResourcesFolder(project(":web_b"), fsm)
		}
	}
}
