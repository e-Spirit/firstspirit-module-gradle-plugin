# [Gradle](http://www.gradle.org/) plugin to build [FirstSpirit](http://www.e-spirit.com/en/product/advantage/advantages.html) modules (FSMs)

## Usage

TLDR: Apply the _firstspirit-module_ plugin to your project, configure your components and use assembleFSM to build your module.

Define a `pluginManagement` block in your `settings.gradle(.kts)` using this snippet. Set the repository and your personal credentials:

```groovy
// Groovy

pluginManagement {
    repositories {
        maven {
            url = 'https://artifactory.e-spirit.hosting/artifactory/repo/'
            credentials {
                username = artifactory_hosting_username
                password = artifactory_hosting_password
            }
        }
    }
}
```

```kotlin
// Kotlin

pluginManagement {
    val artifactory_hosting_username: String by settings
    val artifactory_hosting_password: String by settings

    repositories {
        maven(url = "https://artifactory.e-spirit.hosting/artifactory/repo") {
            credentials {
                username = artifactory_hosting_username
                password = artifactory_hosting_password
            }
        }
    }
}
```

The repository also needs to be defined in your `build.gradle(.kts)`:

```groovy
// Groovy

repositories {
    maven {
        url = 'https://artifactory.e-spirit.hosting/artifactory/repo/'
        credentials {
            username = artifactory_hosting_username
            password = artifactory_hosting_password
        }
    }
}
```
```kotlin
// Kotlin

repositories {
    maven(url = "https://artifactory.e-spirit.hosting/artifactory/repo") {
        credentials {
            username = property("artifactory_hosting_username") as String
            password = property("artifactory_hosting_password") as String
        }
    }
}
```

There are three plugins you can use for slightly different purposes:
firstspirit-module-annotations, firstspirit-module-configurations and firstspirit-module.

We ship all three plugins in a single dependency, so that it's easy for you to use all modules without version incompatibilities in your multi project build.
If you only need a specific feature in one of your sub projects, you can only apply the corresponding plugin, instead of adding all features and capabilities to this project.
The most important task of the plugins is to provide a task to bundle your application as a FirstSpirit module.
The de.espirit.firstspirit-module-annotations plugin and de.espirit.firstspirit-module-configurations plugin can be used to configure FirstSpirit components and their scope and make them visible for the _de.espirit.firstspirit-module_ plugin containing the bundling task.


### de.espirit.firstspirit-module plugin

This is the most important plugin.
It applies the _de.espirit.firstspirit-module-annotations_ plugin and the _de.espirit.firstspirit-module-configurations_ plugin as well on application.
In addition to the other two plugin's capabilities it adds an `assembleFSM` task to your project.
This task assembles a FirstSpirit module file, based on the components in your project.
For further information, please read the sections below.

To use the plugin, include the following snippet on top of your build script:

```kotlin
plugins {
    id("de.espirit.firstspirit-module") version "6.1.0"
}
```

### de.espirit.firstspirit-module-annotations plugin

This plugin applies a dependency to our annotations module to a project.
Its version corresponds with the other Gradle plugins in the bundle.
The dependency is added as `compileOnly` so that you can use our component annotations at compile time, while the dependency is not shipped with your application.
This is sufficient, because the _de.espirit.firstspirit-module_ plugin also comes with its own runtime dependency on the annotation module.
For more information about available annotations, please take a look at the `com.espirit.moddev.components.annotations` package.

To use the plugin, include the following snippet on top of your build script:

```kotlin
plugins {
    id("de.espirit.firstspirit-module-annotations") version "6.1.0"
}
```

### de.espirit.firstspirit-module-configurations plugin

This plugin adds Gradle configurations to your project.
They enable you to configure FirstSpirit scopes (module or server) and other aspects for your dependencies.
Please take a loot at (#dependency-management) for a detailed description of the available scopes and when to use them.

```kotlin
plugins {
    id("de.espirit.firstspirit-module-configurations") version "6.1.0"
}
```

Keep in mind, that you have to add a repository where the plugin can be found.
For example, you can install it in your local Maven repo and add mavenLocal() to the buildScript configuration.

## Project layout

All plugins also apply the [Java Plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html), thus using the same layout as any standard Java project using Gradle.

For a single project build, you can apply the _de.espirit.firstspirit-module_ plugin solely and place all your components into your source folders as usual.
The _de.espirit.firstspirit-module_ plugin will create a .jar archive automatically for the project/subproject it is applied to, place the jar in the .fsm archive, and create a resource entry in the module-isolated.xml.

For a multi-project build, just apply the _de.espirit.firstspirit-module_ plugin to the project/subproject that is responsible for assembling your module archive (.fsm). All other subprojects of the multiproject build should be referenced via the appropriate plugin configurations (`fsWebCompile`, `fsServerCompile`). This will assure the rendering of an appropriate resource entry for the dependent subprojects and their respective transitive dependencies.
 
_Other subprojects may need to apply the_ de.espirit.firstspirit-module-annotations _plugin and/or the firstspirit-module-configurations depending on their content._

```kotlin
//Example: dataservice-fsm subproject includes dataservice-api and dataservice-web subprojects 
dependencies {
    fsWebCompile(project(":dataservice-api"))
    fsWebCompile(project(":dataservice-web"))
}
```


## Tasks

The _de.espirit.firstspirit-module_ plugin defines the following tasks:

Task | Depends on | Type | Description
:---:|:----------:|:----:| -----------
assembleFSM            | jar    | FSM             | Assembles an fsm archive containing the FirstSpirit module.
checkIsolation | fsm    | IsolationCheck  | Checks if the FSM is compliant to the isolated runtime (requires access to a configured FSM Dependency Detector web service).

###assembleFSM
The assembleFSM task has the goal to create a FirstSpirit module file (.fsm). The .fsm file contains the module libraries and their dependencies, the module-isolated.xml meta file, and possibly other module resources from the project directory.

For the project that includes the _de.espirit.firstspirit-module_ plugin, a .jar is created containing the compiled Java classes of the project/subproject. The name of this .jar file is composed by the name and version properties of the project/subproject ([project.name]-[project.version].jar). Furthermore a resource entry with the fsm-project-jar with scope="module" is created in the module resource block. Also, if the module contains a WebAppComponent, a WebResource entry is created for the WebApp to make the code available in the context of the web application.

In order for further dependencies to have a resource entry in the module-isolated.xml, the plugin's own configurations (`fsServerCompile`, `fsWebCompile`, etc.) must be used in the dependencies section.

## Extension properties

The _de.espirit.firstspirit-module_ plugin defines the following extension properties in the `fsm` closure:

Property | Type | Default | Description
:-------:|:----:|:-------:| -----------
moduleName			        | String        | *unset* (project name)	|  The name of the module. If not set the project name is used
displayName                 | String        | *unset*             		|  Human-readable name of the module
moduleDirName               | String        | *unset*             		|  The name of the directory containing the module-isolated.xml, relative to the project directory.
isolationDetectorUrl        | String        | *unset*             		|  If set, this URL is used to connect to the FSM Dependency Detector
isolationDetectorUsername   | String        | *unset*             		|  If set, this username is used to connect to the FSM Dependency Detector
isolationDetectorPassword   | String        | *unset*             		|  If set, this password is used to connect to the FSM Dependency Detector
isolationDetectorWhitelist  | String[]      | *unset*                   |  Contains all resources that should not be scanned for dependencies
contentCreatorComponents    | String[]      | *unset*                   |  Names of components which are meant to be installed with the ContentCreator.
complianceLevel             | String        | DEFAULT                   |  Compliance level to check for if isolationDetectorUrl is set
maxBytecodeVersion          | int           | 61                        |  Maximum bytecode version for all JAR files of the FSM. Defaults to 61 (JDK 17).
firstSpiritVersion          | String        | *unset*             		|  FirstSpirit version used in the isolation check
minimalFirstSpiritVersion   | String        | *unset*                   |  Minimal FirstSpirit server version required to install the module. *Supported by FirstSpirit 2023.10 and later.*
appendDefaultMinVersion     | boolean       | true                      |  If set to true, appends the artifact version as the minVersion attribute to all resource tags (except resources which were explicitly set within FS component annotations)
addDefaultJarTaskOutputToWebResources   | boolean       | true                		|  If set to true, adds the default jar task output of the project to web resources of all web-app components.
### Example

```kotlin
import de.espirit.mavenplugins.fsmchecker.ComplianceLevel.HIGHEST

firstSpiritModule {
    // set a different directory containing the module-isolated.xml
    moduleDirName = "src/main/module"
    isolationDetectorUrl = "https://..."
    isolationDetectorUsername = property("isolation_detector_username") as String  // Read sensitive credentials from external properties file
    isolationDetectorPassword = property("isolation_detector_password") as String  
    isolationDetectorWhitelist = listOf("org.freemarker:freemarker:2.3.28")
    firstSpiritVersion = "5.2.230909"
    minimalFirstSpiritVersion = "5.2.230909" 
    complianceLevel = HIGHEST
}
```

## module-isolated.xml

The _module-isolated.xml_ holds meta information about the FirstSpirit module archive, its components, resources, and dependencies.  
When the plugin generates the .fsm archive it will always create a _module-isolated.xml_ in the META-INF/ directory within the archive.
You may provide your custom _module-isolated.xml_ resource in your project by placing it in the directory defined by the _moduleDirName_ property. 
Furthermore, the _module-isolated.xml_ is filtered by the plugin and predefined placeholders are replaced.
The following placeholders in the _module-isolated.xml_ will be replaced at build time:

Placeholder | Value | Description
-------|-------|------------
$name | project.name / moduleName | Name of the FSM
$displayName | project.name | Human-readable display name of the FSM 
$version | project.version | Version of the FSM
$description | project.description | Description of the FSM
$artifact | project.jar.archiveName | Artifact (jar) name of the FSM
$class | complex (see module example) | The class name of the class implementing the FirstSpirit module interface
$components | complex (see component example) | All FirstSpirit components that can be found in the FSM archive
$minimalFirstSpiritVersion | (unset) | Minimal FirstSpirit server version required to install the module. Supported by FirstSpirit 2023.10 and later.
$resources | complex (see resource example) | All FirstSpirit resources that can be found in the FSM archive

If no module-isolated.xml file is provided within the project, a small generic template module-isolated.xml file is used by the plugin.
This is useful if you don't want to add any custom behaviour to your module-isolated.xml and should be sufficient for most modules.

### FirstSpirit components

Your module may include one or more FirstSpirit components. With the annotations from the _de.espirit.firstspirit-module-annotations_ plugin it is possible to annotate the components with their respective configuration. When assembling the fsm file, the _de.espirit.firstspirit-module_ plugin will evaluate the annotations and render the configurations into the module-isolated.xml.

#### Annotations

##### com.espirit.moddev.components.annotations.@ModuleComponent
A module developer may provide an implementation of the Module interface to participate in the lifecycle of the module. With the @ModuleComponent Annotation on the respective implementation, a Configuration Class may be specified to be used in the module configuration at runtime.

##### com.espirit.moddev.components.annotations.@ServiceComponent
Should be added to a class implementing the Service interface in order to render the appropriate Service configuration into the module-isolated.xml.

##### com.espirit.moddev.components.annotations.@ProjectAppComponent
Should be added to a class implementing the ProjectApp interface in order to render the appropriate ProjectApp configuration into the module-isolated.xml.

##### com.espirit.moddev.components.annotations.@WebAppComponent
Should be added to a class implementing the WebApp interface in order to render the appropriate WebApp configuration into the module-isolated.xml.
Please note, that if you configure a webXml attribute, you need to have a matching web.xml file inside your project in a subfolder of fsm-resources, or else the installation of the .fsm on the FirstSpirit server will fail.

##### com.espirit.moddev.components.annotations.@WebResource
The @WebResource annotation is used within a @WebAppComponent configuration to define one or more web resources (javascript, css, images etc. ) used by the web application. Resources referenced in a @WebResource should be placed in the fsm-resources folder.   

##### com.espirit.moddev.components.annotations.@PublicComponent
Should be added to a class implementing the Public interface in order to render the appropriate Public configuration into the module-isolated.xml.

##### com.espirit.moddev.components.annotations.@ScheduleTaskComponent
Should be added to a class implementing the ScheduleTaskApplication in order to render the appropriate ScheduleTaskApplication configuration into the module-isolated.xml.
When using the <code>@ScheduleTaskComponent</code> annotation, some things need to be considered. For serialization purpose it is required to have a class which implements
the <code>ScheduleTaskData</code> interface in server scope. Most of the time this class is part of the project which uses the _de.espirit.firstspirit-module_ plugin and therefore part of the generated jar which
is <u>NOT</u> in server scope. To make this work the following can be done.

1. Make an extra subproject for the sources which should be in server scope
2. Put `include("SubProject")` or `include("SubProject:SubSubProject")` (if there is more than one level of subprojects) in your `settings.gradle(.kts)`
3. Put `fsServerCompile(project(":SubProject"))` or `fsServerCompile(project(":SubProject:SubSubProject"))` in the `build.gradle(.kts)` file of your fsm generating module. (See "Dependency management" below for more details about dependency configurations)

With this a `.fsm` file is generated which includes a jar with all the classes which are needed in server scope and the module-isolated.xml with the corresponding resource entry.

##### com.espirit.moddev.components.annotations.@UrlFactoryComponent
Should be added to a class implementing the UrlFactory in order to render the appropriate UrlFactory configuration into the module-isolated.xml.

##### com.espirit.moddev.components.annotations.@GadgetComponent
Should be added to a class implementing the GomElement in order to render the appropriate GomElement configuration into the module-isolated.xml.

#### Library Components

Since library components are not based on a specific interface implementation, there is no annotation available to configure those. Instead, a specific configuration block in the Gradle configuration is used to add them:

```kotlin
// Kotlin

val customLib: Configuration by configurations.creating

dependencies {
    customLib("org.slf4j:slf4j-api:2.0.6")
}

firstSpiritModule {
    libraries {
        create("libWithCustomConfiguration") {
            displayName = "Library with custom configuration"
            description = "A library component defined by a custom Gradle configuration"
            hidden = false
            configurable = "com.crownpeak.fsm.demo.Config"
            configuration = customLib
        }
    }
}
```

```groovy
// Groovy

configurations {
    customLib
}

dependencies {
    customLib 'org.slf4j:slf4j-api:2.0.6'
}

firstSpiritModule {
    libraries {
        libWithCustomConfiguration {
            displayName = 'Library with custom configuration'
            description = 'A library component defined by a custom Gradle configuration'
            hidden = false
            configurable = 'com.crownpeak.fsm.demo.Config'
            configuration = configurations.customLib
        }
    }
}

```

Please note that the library component only supports resolvable artifacts, local files or directories will be ignored.

### Resources by convention

The Jar file resulting from the Java Plugin is included in the module-isolated.xml as `${project.name}-lib` with the given group id and version.
Additionally, files in `src/main/fsm-resources` in all referenced project dependencies and the main project will be merged into the root of the resulting fsm file.
All resources in the main project (the project where you apply the fsm plugin), are module-scoped. Please keep in mind that resource entries
with server scope override those with module scope. If you want to include other resource directories, for example for including a generated-resources-folder,
you can configure the assembleFSM task just like other archive tasks accordingly:

```groovy
// Groovy

assembleFSM {
    dependsOn ':impl:generateMyResources'
    
    into('/some-resources') {
        from(project(':impl').layout.buildDirectory.dir('generated-resources'))
    }
}
```

```kotlin
// Kotlin

tasks.assembleFSM {
    dependsOn(tasks.getByPath(":impl:generateMyResources"))
    
    into("/some-resources") {
        from(project(":impl").layout.buildDirectory.dir("generated-resources"))
    }
}
```

CAUTION: Files on the root level of your fsm-resources folder that are used as web.xml files in WebApp components are excluded from resource
declarations in the resulting module-isolated.xml. This is because web.xml files are a special kind of resource, that normally should get embedded
in your fsm file but not be treated as a runtime resource for the application. web.xml files you place in a subdirectory
of the fsm-resources directory are not excluded in any way.

### Property-Filtering in resource annotations for webapp and projectapp components
The build script with subprojects is the preferred tool for managing resources and dependencies.
However, if the project demands more flexibility in addition to the build dependency mechanism with our custom configuration scopes,
one can declare resources within the `@ProjectAppComponent` and `@WebAppComponent` annotation usages.

Within those resources, the name, path and version properties are filtered, which means they can use properties from the global Gradle project context,
as well as properties defined by the resource itself, if a proper corresponding resource can be found.

For example, one can add a regular `implementation` dependency (that is not a fsWebCompile dependency) and get dependency version and path
via properties injected. Or one can use properties to declare fsm-resource entries from other subprojects as web resources for
a webapp component in another subproject.

```java
// in a build.gradle

rootProject.ext {
    webappIconName = 'com.espirit.moddev.example.icon.png'
    commonsIOWebDependencyName = 'commons-io:commons-io'
}
...
implementation "$commonsIOWebDependencyName:2.6"

// within a @WebComponent usage
webResources = {
    @WebResource(path = "someResources/icon.png", name = "${project.webappIconName}", version = "${project.version}", targetPath = "img"),
    @WebResource(path = "$path", name = "${project.commonsIOWebDependencyName}", version = "${version}", targetPath = "lib")
}

```

For regular build dependencies, the version property can be used, whereas for file resources, this wouldn't make sense, hence it's not supported.
Please note that `$project` always refers to the root project of the build.
This way, the complete project context can be retrieved, if really necessary.



### Examples

#### module-isolated.xml example
```xml
<module>
    <name>$name</name>
    <displayname>$displayName</displayname>
    <version>$version</version>
    <description>$description</description>
    $class
    <components>
$components
    </components>
    <resources>
        <resource mode="isolated">lib/$artifact</resource>
$resources
    </resources>
</module>
```
The `$class` placeholder adds a class tag to the module-isolated.xml file. 
The value contains the name of the class annotated with the `@ModuleAnnotation` as well as its configurable if used.
```xml
<module>
    ...
    <class>org.simple.ExampleModule</class>
    <configurable>org.simple.Configuration</configurable>
    ...
</module>
```
NOTE: Do not enclose the `$class` placeholder in `<class>` tags as the tags will be generated by the plugin.
#### Component example:
In this minimalistic example, there will only be one component inside the `$components` tag.
The value of `$components` may contain many more.
```xml
<public>
    <name>MySimplePublicComponent</name>
    <class>org.simple.ExampleComponent</class>
</public>
```
#### Resource example:
This minimalistic example contains only one resource inside the `$resources` tag.
The value of `$resources` may contain many more.
```xml
<resource scope="module">lib/someexample.jar</resource>
```

## Dependency management

The FSM plugin adds the following dependency configurations.

name | Description
-----|------------
fsServerCompile | Same as the usual `implementation` configuration, but the dependency and all transitive dependencies are added to the module-isolated.xml with server scope
fsModuleCompile | Same as the usual `implementation` configuration, but the dependency and all transitive dependencies are added to the module-isolated.xml with module scope
fsWebCompile | Same as the usual `implementation` configuration, but the dependency and all transitive dependencies are added to the web-resources tag in the module-isolated.xml

Dependencies with other scopes than these (for example the regular compile scope) are not treated as a resource to be used for module-isolated.xml file generation.
That means if you use compile scope, you can compile your source files against it like in any other project, but the resource won't be listed in the module-isolated.xml
or included in the `lib` directory.

In a multi-project build make sure to only use the dependency configurations in the project that assembles the fsm. The dependencies of other subprojects should be defined without using the plugin dependency configurations. The resource entries of these dependencies are created depending on how the respective subproject is referenced from the project executing the assembleFSM task.

Local Jars which are part of the project directory will not be included when they are defined with one of the three
scopes above because FirstSpirit requires the artifact metadata (like group-id and version) to detect conflicts
between different modules.

### Example

```kotlin
dependencies {
  // Required to compile the production source code of 
  // this FSM, provided by FirstSpirit at runtime. 
  compileOnly("de.espirit.firstspirit:fs-isolated-runtime:5.2.221111")

  // Embeds this and transitive deps as a server scoped resource
  fsServerCompile("joda-time:joda-time:2.3")

  // Embeds this and transitive deps as a module scoped resource
  fsModuleCompile("org.apache.activemq:activemq-all:5.14.2")

  // Embeds this and transitive deps as a web resource to all web app components
  fsWebCompile("org.apache.activemq:activemq-all:5.14.2")
}
```

### More complex example
If the standard configuration of a dependency is not sufficient, more complex definitions
can be done with the _fsDependency_ helper function, that the plugin configures for a project instance.
This can be useful for tagging dependencies with a special `minVersion` or `maxVersion` attribute.

Configuration of the above-mentioned aspects can be done in standard groovy style, for example:

```groovy
import org.gradle.plugins.fsm.configurations.FSMConfigurationsPluginKt

dependencies {
  use (FSMConfigurationsPluginKt) {
    fsModuleCompile fsDependency(dependency: 'joda-time:joda-time:2.10', minVersion: '1.0', maxVersion: '1.5')
    fsModuleCompile fsDependency('commons-logging:commons-logging:1.2', '1.0', '1.5')
  }
}
```

For build scripts written in Kotlin, the syntax is even simpler:

```kotlin
import org.gradle.plugins.fsm.configurations.fsDependency

dependencies {
  fsModuleCompile(fsDependency(mapOf("dependency" to "joda-time:joda-time:2.10", "minVersion" to "1.0", "maxVersion" to "1.5")))
  fsModuleCompile(fsDependency("commons-logging:commons-logging:1.2", "1.0", "1.5"))
}
```

## Working with isolated resources

Modules built with this plug-in will only work with FirstSpirit servers running in
"isolation mode", which has been the default for some time now. All resources will
be marked "isolated" automatically, thus reducing conflicts on the classpath.

It is possible to perform an isolation check on the resulting module file to ensure a certain level of compliance to the isolated mode. This check requires access to an *FSM Depedency Detector* web service and can be configured using the extension properties as explained above. Valid values for the compliance level are:

name | Description
-----|------------
MINIMAL | Asserts that there is no use of implementation classes that are not available in the isolated runtime. This ist the minimal requirement to run a module with a server in isolated mode (prevents IMPL_USAGE type dependencies).
DEFAULT | In addition to MINIMAL, the default compliance level asserts that there is no use of internal FirstSpirit classes, that are not part of the public API. These classes are available in the isolated runtime of the current version and will work in isolated mode, but they are subject to change without prior notice and should therefore be removed for sake of longevity (prevents IMPL_USAGE and RUNTIME_USAGE type dependencies).
HIGHEST | The highest setting further prohibits the usage of deprecated FirstSpirit API (prevents IMPL_USAGE, RUNTIME_USAGE and DEPRECATED API_USAGE type dependencies)

#### Dependency types (Isolation level)

name | Description
-----|------------
IMPL_USAGE | Usage of classes which are not part of the isolated runtime
RUNTIME_USAGE | Usage of classes that are not part of the public API
DEPRECATED_API_USAGE | Usage of FirstSpirit API that has been deprecated

## Adding WebApps

To add a WebApp to the FSM, use the following steps:
 
 1. Add a class implementing `WebApp` and mark it with the `@WebAppComponent` annotation, as with other components. This class should be located in the project that assembles the FSM. 
 2. Add a Gradle subproject corresponding to the WebApp to your project
 3. Register the WebApp in the `firstSpiritModule` configuration block by calling `webAppComponent(String name, Project project)`. Example:
    ```kotlin
    firstSpiritModule {
        webAppComponent("myWebApp", project(":myWebAppSubproject"))
    }
    ```
    **Important**: The name you provide in the `webAppComponent` definition **must** match the `name` attribute of the `@WebAppComponent` annotation!\
    **Important**: The default jar task output of the root project is added to the web resources of all web apps by default. This can be turned off using the extension property `addDefaultJarTaskOutputToWebResources` (see [Extension properties](#extension-properties)).\
    **Note**: If you add a web app with `webAppComponent`, do **not** declare a `fsWebCompile` dependency on the subproject. 
    
If you have multiple webapps, note that the `fsm-resources` directories (see above) of each Gradle subproject are merged into the root directory of the FSM archive. To ensure no files are overwritten, we recommend placing the resources of each web app into a uniquely named subfolder in the `fsm-resources` directory. Example:
    
```
+-+ myWebAppA
| +-- build.gradle
| +-+ src
|   +-+ main
|     +-+ fsm-resources
|       +-+ my-web-app-a
|         +-- ...       // resources for the first web app
+-+ myWebAppB
  +-- build.gradle
  +-+ src
    +-+ main
      +-+ fsm-resources
        +-+ my-web-app-b
          +-- ...       // resources for the second web app
```  
 

## Example

You can use the following snippet as a starting point:

```groovy
// Groovy

plugins {
    id 'de.espirit.firstspirit-module' version '6.1.0'
}

description = 'Example FSM Gradle build'
version = '0.1.0'

dependencies {
    fsServerCompile 'joda-time:joda-time:2.10.14'
    fsModuleCompile 'org.apache.activemq:activemq-all:5.17.0'
    fsWebCompile 'org.apache.activemq:activemq-all:5.17.0'

    compileOnly "de.espirit.firstspirit:fs-isolated-runtime:${fsRuntimeVersion}"

    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.2'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.2'

    implementation 'com.espirit.moddev.components:annotations:3.0.0'
}

test {
    useJUnitPlatform()
}

firstSpiritModule {
    // example to set a different directory containing the module-isolated.xml
    // moduleDirName = 'src/main/module'
    // declare dependencies to other FSM's to be written to the module-isolated.xml
    // fsmDependencies = ['nameOfMyOtherFsmThisFsmDependsOn', 'andAnotherOne']
}
```

```kotlin
// Kotlin

plugins {
    id("de.espirit.firstspirit-module") version "6.1.0"
}

description = "Example FSM Gradle build"
version = "0.1.0"

dependencies {
    fsServerCompile("joda-time:joda-time:2.10.14")
    fsModuleCompile("org.apache.activemq:activemq-all:5.17.0")
    fsWebCompile("org.apache.activemq:activemq-all:5.17.0")

    compileOnly("de.espirit.firstspirit:fs-isolated-runtime:${fsRuntimeVersion}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")

    implementation("com.espirit.moddev.components:annotations:3.0.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

firstSpiritModule {
    // example to set a different directory containing the module-isolated.xml
    // moduleDirName = "src/main/module"
    // declare dependencies to other FSM's to be written to the module-isolated.xml
    // fsmDependencies = listOf("nameOfMyOtherFsmThisFsmDependsOn", "andAnotherOne")
}
```


## Requirements

* [Java](http://www.java.com/en/download/) 11+
* [Gradle](http://www.gradle.org/downloads) 6.8+
