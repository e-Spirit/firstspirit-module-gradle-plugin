![e-spirit logo](https://fbcdn-profile-a.akamaihd.net/hprofile-ak-ash3/s160x160/581307_346033565453595_1547840127_a.jpg)

# Gradle FSM plugin

[Gradle](http://www.gradle.org/) plugin to build [FirstSpirit](http://www.e-spirit.com/en/product/advantage/advantages.html) modules (FSMs).

## Usage

To use the plugin, include the following snippet on top of your build script:

```groovy
apply plugin: 'fsmgradleplugin'
```

Keep in mind, that you have to add a repository where the plugin can be found.
For example you can install it in your local Maven repo and add mavenLocal() to the buildScript configuration.

## Project layout

This plugin applies the [Java Plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html), thus uses the same layout as any standard Java project using Gradle.

## Tasks

The plugin defines the following tasks:

Task | Depends on | Type | Description
:---:|:----------:|:----:| -----------
fsm            | jar    | FSM             | Assembles an fsm archive containing the FirstSpirit module.
isolationCheck | fsm    | IsolationCheck  | Checks the FSM using an FSM Dependency Detector web service.

## Extension properties

The plugin defines the following extension properties in the `fsm` closure:

Property | Type | Default | Description
:-------:|:----:|:-------:| -----------
moduleName			 | String        | *unset* (project name)	|  The name of the module. If not set the project name is used
displayName          | String        | *unset*             		|  Human-readable name of the module
moduleDirName        | String        | src/main/resources  		|  The name of the directory containing the module.xml, relative to the project directory.
resourceMode         | Mode          | *unset*             		|  Resource mode (legacy, isolated) used for all resources
isolationDetectorUrl | String        | *unset*             		|  If set, this URL is used to connect to the FSM Dependency Detector
complianceLevel      | String        | DEFAULT                 |  Compliance level to check for if isolationDetectorUrl is set
firstSpiritVersion   | String        | *unset*             		|  FirstSpirit version used in the isolation check
appendDefaultMinVersion | boolean    | false                    |  If set to true, appends the artifact version as the minVersion attribute to all resource tags (except resources which were explicitly set within FS component annotations)

### Example

```groovy
fsm {
    // set a different directory containing the module.xml
    moduleDirName = 'src/main/module'
    resourceMode = ISOLATED
    isolationDetectorUrl = 'https://...'
    firstSpiritVersion = '5.2.2109'
    complianceLevel = 'HIGHEST'
}
```

## module.xml

If you supply a module.xml resource in your project (as a standard resource that gets embedded into the fsm archive), it will be filtered by the plugin.
The plugin adds tags to the xml file that depend on the module project.
The following placeholders in the _module.xml_ will be replaced at build time:

Placeholder | Value | Description
-------|-------|------------
$name | project.name / moduleName | Name of the FSM
$displayName | project.name | Human-readable display name of the FSM 
$version | project.version | Version of the FSM
$description | project.description | Description of the FSM
$artifact | project.jar.archiveName | Artifact (jar) name of the FSM
$components | complex (see component example) | All FirstSpirit components that can be found in the FSM archive
$resources | complex (see resource example) | All FirstSpirit resources that can be found in the FSM archive

If no module.xml file can be found in the archive, a small generic template module.xml file is used by the plugin.
This is useful, if you don't want to add any custom behaviour to your module.xml. 

### FirstSpirit components

The configuration for your implemented FirstSpirit components has to be placed somewhere, so that the plugin can generate a module.xml file.
Your project app, web app and other components can be annotated with our custom annotations that can be found in a separate project.
In order to be able to use them, add the following dependency to your project:

compile 'com.espirit.moddev.components:annotations:1.5.0'

The annotations should be self explanatory. If a component doesn't provide annotations, it won't be treated for module.xml generation.
You could add tags to your module.xml template by hand in this case. Please note, if you are using an @WebAppComponent annotation with 
a webXml attribute, you need to have a matching file inside your project. Otherwise your .fsm cannot be installed on the FirstSpirit server.

#### Special case for server plugins

If the <code>@ScheduleTaskComponent</code> annotation is used, some things need to be considered. For serialization purpose it is required to have the class which implements
the <code>ScheduleTaskData</code> interface in server scope. Most of the time this class is part of the project which uses the fsmgradleplugin and therefore part of the generated jar which
is <u>NOT</u> in server scope. To make this work the following can be done.

<ul>
<li>Make an extra subproject for the sources which should be in server scope</li>
<li>Put <code>include 'SubProject'</code> or <code>include 'SubProject:SubSubProject'</code> (if there is more than one level of subprojects) in your <code>settings.gradle</code></li>
<li>Put <code>fsServerCompile project(:'SubProject')</code> or <code>fsServerCompile project(':SubProject:SubSubProject')</code> in the <code>build.gradle</code> file of your fsm generating module. (See "Dependency management" below for more details about dependency configurations)</li>
</ul>

With this a <code>.fsm</code> file is generated which includes a jar with all the classes which are needed in server scope and the module.xml with the corresponding resource entry.

### Resources by convention

The Jar file resulting from the Java Plugin is included in the module.xml as `${project.name}-lib` with the given group id and version.
Additionally, files in `src/main/files` will be placed in the `/files` directory of the FSM with a resource definition named `${project.name}-files`. 

### Examples

#### module.xml example
```xml
<!DOCTYPE module PUBLIC "module.dtd" "platform:/resource/fs-module-support/src/main/resources/dtds/module.dtd">
<module>
    <name>$name</name>
    <displayname>$displayName</displayname>
    <version>$version</version>
    <description>$description</description>
    <components>
$components
    </components>
    <resources>
        <resource>lib/$artifact</resource>
$resources
    </resources>
</module>
```

#### Component example:
In this minimalistic example, there will only one component inside $components tag.
The value of $components can contain many more.
```xml
<public>
    <name>MySimplePublicComponent</name>
    <class>org.simple.ExampleComponent</class>
</public>
```
#### Resource example:
In this minimalistic example, there will only one resource inside $resources tag.
The value of $resources can contain many more.
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

## Working with isolated resources

Resources declared as "isolated" have less classpath conflicts because they share a
classpath with classes available in the isolated runtime (in contrast to all classes
available in the access jar). Isolated resources are only available if the FirstSpirit
server is started in "isolation mode".

To declare all resource as legacy or isolated, use the `resourceMode` extension property.

It is possible to perform and isolation check on the resulting module file to ensure a certain level of compliance to the isolated mode. This check requires access to an *FSM Depedency Detector* web service and can be configured using the extension properties as explained above. Valid values for the compliance level are:

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
buildscript {
    dependencies {
        classpath 'com.espirit.moddev:fsmgradleplugin:0.10.15'
        classpath 'de.espirit.firstspirit:fs-isolated-runtime:5.2.2109'
    }
}
apply plugin: 'fsmgradleplugin'
apply plugin: 'eclipse'
apply plugin: 'idea'

description = 'Example FSM Gradle build'
version = '0.1.0'

dependencies {
    fsServerCompile 'joda-time:joda-time:2.3'
    fsModuleCompile 'org.apache.activemq:activemq-all:5.14.2'
    fsWebCompile 'org.apache.activemq:activemq-all:5.14.2'

	fsProvidedCompile 'commons-logging:commons-logging:1.1.3'

    fsProvidedCompile 'de.espirit.firstspirit:fs-access:5.2.2109'

	testCompile 'junit:junit:4.12'

    compile 'com.espirit.moddev.components:annotations:1.4.0'
}

fsm {
    // example to set a different directory containing the module.xml
	// moduleDirName = 'src/main/module'
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
