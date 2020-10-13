# [Gradle](http://www.gradle.org/) plugin to build [FirstSpirit](http://www.e-spirit.com/en/product/advantage/advantages.html) modules (FSMs)

## Usage

TLDR: Apply the _firstspirit-module_ plugin to your project, configure your components and use assembleFSM to build your module.

Define a pluginManagement block in your settings.gradle using this snippet. Set the repository and your personal credentials:

    pluginManagement {
        repositories {
            maven {
                url = 'https://artifactory.e-spirit.de/artifactory/repo/'
                credentials {
                    username = artifactory_username
                    password = artifactory_password
                }
            }
            gradlePluginPortal()
        }
    }

There are three plugins you can use for slightly different purposes:
[firstspirit-module-annotations](https://git.e-spirit.de/projects/DEVEX/repos/fsmannotations/), firstspirit-module-configurations and firstspirit-module.

We ship all three plugins in a single dependency, so that it's easy for you to use all modules without version incompatibilities in your multi project build.
If you only need a specific feature in one of your sub projects, you can only apply the corresponding plugin, instead of adding all features and capabilities to this project.
The most important task of the plugins is to provide a task to bundle your application as a FirstSpirit module.
The de.espirit.firstspirit-module-annotations plugin and de.espirit.firstspirit-module-configurations plugin can be used to configure FirstSpirit components and their scope and make them visible for the _de.espirit.firstspirit-module_ plugin, that contains the bundling task.


### de.espirit.firstspirit-module plugin

This is the most important plugin.
It applies the _de.espirit.firstspirit-module-annotations_ plugin and the _de.espirit.firstspirit-module-configurations_ plugin as well on application.
Additionally to the other two plugin's capabilities, it adds an `assembleFSM` task to your project.
This task assembles a FirstSpirit module file, based on the components in your project.
For further information, please read the sections below.

To use the plugin, include the following snippet on top of your build script:

```groovy
plugins {
    id 'de.espirit.firstspirit-module' version '{{version}}'
}
```

### de.espirit.firstspirit-module-annotations plugin

This plugin applies a dependency to our annotations module to a project.
Its version corresponds with the other gradle plugins in the bundle.
The dependency is added as compileOnly so that you can use our component annotations at compile time, while the dependency is not shipped with your application.
This is sufficient, because the _de.espirit.firstspirit-module_ plugin also comes with it's own runtime dependency on the annotation module.
For more information about available annotations, please take a look at the `com.espirit.moddev.components.annotations` package.

To use the plugin, include the following snippet on top of your build script:

```groovy
plugins {
    id 'de.espirit.firstspirit-module-annotations' version '{{version}}'
}
```

### de.espirit.firstspirit-module-configurations plugin

This plugin adds gradle configurations to your project.
They enable you to configure FirstSpirit scopes (module or server) and other aspects (for example FirstSpirit isolation) for your dependencies.
Please take a loot at #dependency-management for a detailed description of the available scopes and when to use them.

```groovy
plugins {
    id 'de.espirit.firstspirit-module-configurations' version '{{version}}'
}
```

Keep in mind, that you have to add a repository where the plugin can be found.
For example you can install it in your local Maven repo and add mavenLocal() to the buildScript configuration.

## Project layout

All plugins also apply the [Java Plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html), thus using the same layout as any standard Java project using Gradle.

For a single project build, you can apply the _de.espirit.firstspirit-module_ plugin alone and place all your components into your source folders as usual.
The _de.espirit.firstspirit-module_ plugin will create a .jar archive automatically for the project/subproject it is applied to, place the jar in the .fsm archive, and create a resource entry in the module.xml.

For a multi-project build, just apply the _de.espirit.firstspirit-module_ plugin to the project/subproject that is responsible for assembling your module archive (.fsm). All other subprojects of the multiproject build should be referenced via the appropriate plugin configurations (fsWebCompile, fsServerCompile). This will assure the rendering of an appropriate resource entry for the dependent subprojects and their respective transitive dependencies.
 
_Other subprojects may need to apply the_ de.espirit.firstspirit-module-annotations _plugin and/or the firstspirit-module-configurations depending on their content._

```groovy
//Example: dataservice-fsm subproject includes dataservice-api and dataservice-web subprojects 
dependencies {

    fsWebCompile project(':dataservice-api')
    fsWebCompile project(':dataservice-web')

}
```


## Tasks

The _de.espirit.firstspirit-module_ plugin defines the following tasks:

Task | Depends on | Type | Description
:---:|:----------:|:----:| -----------
assembleFSM            | jar    | FSM             | Assembles an fsm archive containing the FirstSpirit module.
checkIsolation | fsm    | IsolationCheck  | Checks if the FSM is compliant to the isolated runtime (requires access to a configured FSM Dependency Detector web service).

###assembleFSM
The assembleFSM task has the goal to create a FirstSprit module file (.fsm). The .fsm file contains the module libraries and their dependencies, the module.xml meta file, and possibly other module resources from the project directory.
For the project that includes the _de.espirit.firstspirit-module_ plugin, a .jar is created that contains the compiled Java classes of the project/subproject. The name of this .jar file is composed by the name and version properties of the project/subproject ([project.name]-[project.version].jar). Furthermore a resource entry with the fsm-project-jar with scope="module" is created in the module resource block. Also, if the module contains a WebAppComponent, a WebResource entry is created for the WebApp to make the code available in the context of the web application.
In order for further dependencies to have a resource entry in the module.xml, the plugin's own configurations (fsServerCompile, fsWebCompile etc.) must be used in the dependencies section.

## Extension properties

The _de.espirit.firstspirit-module_ plugin defines the following extension properties in the `fsm` closure:

Property | Type | Default | Description
:-------:|:----:|:-------:| -----------
moduleName			        | String        | *unset* (project name)	|  The name of the module. If not set the project name is used
displayName                 | String        | *unset*             		|  Human-readable name of the module
moduleDirName               | String        | src/main/resources  		|  The name of the directory containing the module.xml (and/or module-isolated.xml), relative to the project directory.
resourceMode                | Mode          | *unset*             		|  Resource mode (legacy, isolated) used for all resources
isolationDetectorUrl        | String        | *unset*             		|  If set, this URL is used to connect to the FSM Dependency Detector
isolationDetectorWhitelist  | String[]      | *unset*                   |  Contains all resources that should not be scanned for dependencies
contentCreatorComponents    | String[]      | *unset*                   |  Names of components which are meant to be installed with the ContentCreator.
complianceLevel             | String        | DEFAULT                   |  Compliance level to check for if isolationDetectorUrl is set
firstSpiritVersion          | String        | *unset*             		|  FirstSpirit version used in the isolation check
appendDefaultMinVersion     | boolean       | true                      |  If set to true, appends the artifact version as the minVersion attribute to all resource tags (except resources which were explicitly set within FS component annotations)

### Example

```groovy
firstSpiritModule {
    // set a different directory containing the module.xml
    moduleDirName = 'src/main/module'
    resourceMode = ISOLATED
    isolationDetectorUrl = 'https://...'
    isolationDetectorWhitelist = ['org.freemarker:freemarker:2.3.28']
    firstSpiritVersion = '5.2.190507'
    complianceLevel = 'HIGHEST'
}
```

## module.xml

The _module.xml_ holds meta information about the FirstSpirit module archive, its components, resources, and dependencies.  
When the plugin generates the .fsm archive it will always create a _module.xml_ in the META-INF/ directory within the archive.
You may provide your custom _module.xml_ resource in your project by placing it in the directory defined by the _moduleDirName_ property. 
Furthermore, the _module.xml_ is filtered by the plugin and predefined placeholders are replaced.
The following placeholders in the _module.xml_ will be replaced at build time:

Placeholder | Value | Description
-------|-------|------------
$name | project.name / moduleName | Name of the FSM
$displayName | project.name | Human-readable display name of the FSM 
$version | project.version | Version of the FSM
$description | project.description | Description of the FSM
$artifact | project.jar.archiveName | Artifact (jar) name of the FSM
$class | complex (see module example) | The class name of the class implementing the FirstSpirit module interface
$components | complex (see component example) | All FirstSpirit components that can be found in the FSM archive
$resources | complex (see resource example) | All FirstSpirit resources that can be found in the FSM archive

If no module.xml file is provided within the project, a small generic template module.xml file is used by the plugin.
This is useful, if you don't want to add any custom behaviour to your module.xml. 

### FirstSpirit components

Your module may include one or more FirstSpirit components. With the annotations from the _de.espirit.firstspirit-module-annotations_ plugin it is possible to annotate the components with their respective configuration. When assembling the fsm file, the _de.espirit.firstspirit-module_ plugin will evaluate the annotations and render the configurations into the module.xml.

#### Annotations

##### com.espirit.moddev.components.annotations.@ModuleComponent
A module developer may provide an implementation of the Module interface to participate in the lifecycle of the module. With the @ModuleComponent Annotation on the respective implementation, a Configuration Class may be specified to be used in the module configuration at runtime.

##### com.espirit.moddev.components.annotations.@ServiceComponent
Should be added to a class implementing the Service interface in order to render the appropriate Service configuration into the module.xml.

##### com.espirit.moddev.components.annotations.@ProjectAppComponent
Should be added to a class implementing the ProjectApp interface in order to render the appropriate ProjectApp configuration into the module.xml.

##### com.espirit.moddev.components.annotations.@WebAppComponent
Should be added to a class implementing the WebApp interface in order to render the appropriate WebApp configuration into the module.xml.
Please note, that if you configure a webXml attribute, you need to have a matching web.xml file inside your project in a subfolder of fsm-resources, or else the installation of the .fsm on the FirstSpirit server will fail.

##### com.espirit.moddev.components.annotations.@WebResource
The @WebResource annotation is used within a @WebAppComponent configuration to define one or more web resources (javascript, css, images etc. ) used by the web application. Resources referenced in a @WebResource should be placed in the fsm-resources folder.   

##### com.espirit.moddev.components.annotations.@PublicComponent
Should be added to a class implementing the Public interface in order to render the appropriate Public configuration into the module.xml.

###### com.espirit.moddev.components.annotations.@ScheduleTaskComponent
Should be added to a class implementing the ScheduleTaskApplication in order to render the appropriate ScheduleTaskApplication configuration into the module.xml.
When using the <code>@ScheduleTaskComponent</code> annotation, some things need to be considered. For serialization purpose it is required to have the class which implements
the <code>ScheduleTaskData</code> interface in server scope. Most of the time this class is part of the project which uses the _de.espirit.firstspirit-module_ plugin and therefore part of the generated jar which
is <u>NOT</u> in server scope. To make this work the following can be done.

<ul>
<li>Make an extra subproject for the sources which should be in server scope</li>
<li>Put <code>include 'SubProject'</code> or <code>include 'SubProject:SubSubProject'</code> (if there is more than one level of subprojects) in your <code>settings.gradle</code></li>
<li>Put <code>fsServerCompile project(:'SubProject')</code> or <code>fsServerCompile project(':SubProject:SubSubProject')</code> in the <code>build.gradle</code> file of your fsm generating module. (See "Dependency management" below for more details about dependency configurations)</li>
</ul>

With this a <code>.fsm</code> file is generated which includes a jar with all the classes which are needed in server scope and the module.xml with the corresponding resource entry.

###### com.espirit.moddev.components.annotations.@UrlFactoryComponent
Should be added to a class implementing the UrlFactory in order to render the appropriate UrlFactory configuration into the module.xml.

###### com.espirit.moddev.components.annotations.@GadgetComponent
Should be added to a class implementing the GomElement in order to render the appropriate GomElement configuration into the module.xml.

### Resources by convention

The Jar file resulting from the Java Plugin is included in the module.xml as `${project.name}-lib` with the given group id and version.
Additionally, files in `src/main/fsm-resources` in all referenced sub projects and the main project will be merged into the root of the resulting fsm file.
All resources in the main project (the project where you apply the fsm plugin), are module scoped. Please keep in mind that resource entries
with server scope override those with module scope. If you want to include other resource directories, for example for including a generated-resources-folder,
you can configure the assembleFSM task just like other archive tasks accordingly:
```
assembleFSM {
    into('/') { // embed right into the fsm root
        from(project.projectDir.toPath().resolve('..').resolve('impl').resolve('build').resolve('generated-resources'))
    }
}
assembleFSM.dependsOn 'generateMyResources'
```

CAUTION: Files on the root level of your fsm-resources folder that are used as web.xml files in WebApp components are excluded from resource
declarations in the resulting module.xml. This is because web.xml files are a special kind of resource, that normally should get embedded
in your fsm file but not be treated as a runtime resource for the application. web.xml files you place in a subfolder
of the fsm-resources directory, are not excluded in any way.

### Property Filtering in resource annotations for webapp and projectapp components
The build script with subprojects is the preferred tool for managing resources and dependencies.
However, if the project demands more flexibility, in addition to the build dependency mechanism with our custom configuration scopes,
one can declare resources within the @ProjectAppComponent and @WebAppComponent annotation usages.

Within those resources, the name, path and version properties are filtered, which means they can use properties from the global gradle project context,
as well as properties defined by the resource itself, if a proper corresponding resource can be found.

For example, one can add a regular compile dependency (that is not a fsWebCompile dependency) and get dependency version and path
via properties injected. Or one can use properties to declare fsm-resource entries from other subprojects as web resources for
a webapp component in another subproject.

```java
// in a build.gradle

rootProject.ext {
    webappIconName = 'com.espirit.moddev.example.icon.png'
    commonsIOWebDependencyName = 'commons-io:commons-io'
}
...
compile "$commonsIOWebDependencyName:2.6"

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

#### module.xml example
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
        <resource>lib/$artifact</resource>
$resources
    </resources>
</module>
```
The `$class` placeholder adds a class tag to the module.xml file. 
The value contains the name of the class annotated with the `@ModuleAnnotation` as well as its configurable if used.
```xml
<module>
    ...
    <class>org.simple.ExampleModule</class>
    <configurable>org.simple.Configuration</configurable>
    ...
</module>
```
WARNING: Do not enclose the `$class` placeholder in `<class>` tags as the tags will be generated by the plugin.
#### Component example:
In this minimalistic example, there will only one component inside $components tag.
The value of $components may contain many more.
```xml
<public>
    <name>MySimplePublicComponent</name>
    <class>org.simple.ExampleComponent</class>
</public>
```
#### Resource example:
This minimalistic example contains only one resource inside $resources tag.
The value of $resources may contain many more.
```xml
<resource scope="module">lib/someexample.jar</resource>
```

## Dependency management

The FSM plugin adds the following dependency configurations.

name | Description
-----|------------
fsServerCompile | Same as the usual compile configuration, but the dependency and all transitive dependencies are added to the module.xml with server scope
fsModuleCompile | Same as the usual compile configuration, but the dependency and all transitive dependencies are added to the module.xml with module scope
fsWebCompile | Same as the usual compile configuration, but the dependency and all transitive dependencies are added to the web-resources tag in the module.xml
fsProvidedCompile | Same as the usual compileOnly configuration - the dependency and all transitive dependencies are not added to the FSM archive.
fsProvidedRuntime | Same scope as the usual runtime dependency. Use if you want to do some kind of integration testing without FirstSpirit server.

Dependencies with other scopes than these (for example the regular compile scope) are not treated as a resource to be used for module.xml file generation.
That means if you use compile scope, you can compile your source files against it like in any other project, but the resource won't be listed in the module.xml.
   

In a multiproject build make sure to only use the dependency configurations in the project that assembles the fsm. The dependencies of other subprojects should be defined without using the plugin dependency configurations. The resource entries of these dependencies are created depending on how the respective subproject is referenced from the project executing the assembleFSM task.  

### Example

```groovy
dependencies {
  // Library required to compile the production source code of 
  // this FSM which is provided by FirstSpirit. 
  fsProvidedCompile ('commons-logging:commons-logging:1.1.3')

  // Embeds this and transitive deps as a server scoped resource
  fsServerCompile 'joda-time:joda-time:2.3'

  // Embeds this and transitive deps as a module scoped resource
  fsModuleCompile 'org.apache.activemq:activemq-all:5.14.2'

  // Embeds this and transitive deps as a web resource to all web app components
  fsWebCompile 'org.apache.activemq:activemq-all:5.14.2'
}
```

### More complex example
If the standard configuration of a dependency is not sufficient, more complex definitions
can be done with the _fsDependency_ helper function, that the plugin configures for a project instance.
This can be useful for tagging dependencies with a special minVersion or maxVersion attribute,
or if one has a resource that should be added in the isolation configuration of the FirstSpirit module,
but not in the non-isolation case. For information about isolation in FirstSpirit scenarios, please
take a look at the official FirstSpirit module development documentation.

Configuration of the above mentioned aspects can be done in standard groovy style, for example:

```
dependencies {
  fsModuleCompile fsDependency(dependency: 'joda-time:joda-time:2.10', skipInLegacy: true, minVersion: '1.0', maxVersion: '1.5')
  fsModuleCompile fsDependency('commons-logging:commons-logging:1.2', true, '1.0', '1.5')
}
```

## Working with isolated resources

Resources declared as "isolated" have less classpath conflicts because they share a
classpath with classes available in the isolated runtime (in contrast to all classes
available in the access jar). Isolated resources are only available if the FirstSpirit
server is started in "isolation mode".

To declare all resource as legacy or isolated, use the `resourceMode` extension property.

It is possible to perform an isolation check on the resulting module file to ensure a certain level of compliance to the isolated mode. This check requires access to an *FSM Depedency Detector* web service and can be configured using the extension properties as explained above. Valid values for the compliance level are:

name | Description
-----|------------
MINIMAL | Asserts that there is no use of implementation classes that are not available in the isolated runtime. This ist the minimal requirement to run a module with a server in isolated mode (prevents IMPL_USAGE type dependencies).
DEFAULT | In addition to MINIMAL, the default compliance level asserts that there is no use of internal FirstSpirit classes, that are not part of the public API. These classes are available in the isolated runtime of the current version and will work in isolated mode, but they are subject to change without prior notice and should therefore be removed for sake of longevity (prevents IMPL_USAGE and RUNTIME_USAGE type dependencies).
HIGHEST | The highest setting further prohibits the usage of deprecated FirstSpirit API (prevents IMPL_USAGE, RUNTIME_USAGE and DEPRECATED API_USAGE type dependencies)

Dependency types (Isolation level)

name | Description
-----|------------
IMPL_USAGE | Usage of classes which are not part of the isolated runtime
RUNTIME_USAGE | Usage of classes that are not part of the public API
DEPRECATED_API_USAGE | Usage of FirstSpirit API that has been deprecated

## Adding WebApps

To add a WebApp to the FSM, use the following steps:
 
 1. Add a class implementing `WebApp` and mark it with the `@WebAppComponent` annotation, as with other components. This class should be located in the project that assembles the FSM. 
 1. Add a Gradle subproject corresponding to the WebApp to your project
 1. Register the WebApp in the `firstSpiritModule` configuration block by calling `webAppComponent(String name, Project project)`. Example:
    ```groovy
    firstSpiritModule {
        webAppComponent("myWebApp", project(":myWebAppSubproject"))
    }
    ```
    **Important**: The name you provide in the `webAppComponent` definition **must** match the `name` attribute of the `@WebAppComponent` annotation!\
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
 

## IDE support

### IntelliJ

The two custom configurations can be added to the [IntelliJ](http://www.jetbrains.com/idea/webhelp/gradle-2.html) classpath:

```groovy
apply plugin: 'idea'

// add libraries from custom FirstSpirit configurations to the IntelliJ classpath (Gradle 2.0 syntax)
idea.module.scopes.COMPILE.plus += [configurations.fsProvidedCompile]
idea.module.scopes.COMPILE.plus += [configurations.fsProvidedRuntime]
```

### Eclipse

The two custom configurations can be added to the [Eclipse](http://docs.spring.io/sts/docs/2.9.0.old/reference/html/gradle/gradle-sts-tutorial.html) classpath:

```groovy
apply plugin: 'eclipse'

eclipse {
    classpath {
        // add libraries from custom FirstSpirit configurations to the eclipse classpath (Gradle 2.0 syntax)
        plusConfigurations += [configurations.fsProvidedCompile]
        plusConfigurations += [configurations.fsProvidedRuntime]
    }
}
```

## Example

You can use the following snippet as a starting point:

```groovy
plugins {
    id 'de.espirit.firstspirit-module' version '{{version}}'
    id 'eclipse'
    id 'idea'
}

description = 'Example FSM Gradle build'
version = '0.1.0'

dependencies {
    fsServerCompile 'joda-time:joda-time:2.3'
    fsModuleCompile 'org.apache.activemq:activemq-all:5.14.2'
    fsWebCompile 'org.apache.activemq:activemq-all:5.14.2'

	fsProvidedCompile "de.espirit.firstspirit:fs-isolated-runtime:${fsRuntimeVersion}"

	testCompile 'junit:junit:4.12'

    compile 'com.espirit.moddev.components:annotations:1.9.1'
}

firstSpiritModule {
    // example to set a different directory containing the module.xml
	// moduleDirName = 'src/main/module'
	// declare dependencies to other FSM's to be written to the module.xml
	// fsmDependencies = ['nameOfMyOtherFsmThisFsmDependsOn', 'andAnotherOne']
}

eclipse {
    classpath {
        // add libraries from custom FirstSpirit configurations to the eclipse classpath (Gradle 2.0 syntax)
        plusConfigurations += [configurations.fsProvidedCompile]
        plusConfigurations += [configurations.fsProvidedRuntime]
    }
}

// add libraries from custom FirstSpirit configurations to the IntelliJ classpath (Gradle 2.0 syntax)
idea.module.scopes.COMPILE.plus += [configurations.fsProvidedCompile]
idea.module.scopes.COMPILE.plus += [configurations.fsProvidedRuntime]
```

## Requirements

* [Java](http://www.java.com/en/download/) 1.8+
* [Gradle](http://www.gradle.org/downloads) 4.2+
