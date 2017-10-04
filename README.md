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
fsm  | jar        | FSM  | Assembles a fsm archive containing the FirstSpirit module.

## Extension properties

The plugin defines the following extension properties in the `fsm` closure:

Property | Type | Default | Description
:-------:|:----:|:-------:| -----------
moduleDirName  | String        | src/main/resources  |  The name of the directory containing the module.xml, relative to the project directory.

### Example

```groovy
fsm {
    // set a different directory containing the module.xml
    moduleDirName = 'src/main/module'
}
```

## module.xml

If you supply a module.xml resource in your project (as a standard resource that gets embedded into the fsm archive), it will be filtered by the plugin.
The plugin adds tags to the xml file that depend on the module project.
The following placeholders in the _module.xml_ will be replaced at build time:

Placeholder | Value | Description
-------|-------|------------
$name | project.name | Name of the FSM
$version | project.version | Version of the FSM
$description | project.description | Description of the FSM
$artifact | project.jar.archiveName | Artifact (jar) name if the FSM
$components | "complicated" | All FirstSpirit components that can be found in the FSM archive
$resources | "complicated" | All FirstSpirit resources that can be found in the FSM archive

If no module.xml file can be found in the archive, a small generic template module.xml file is used by the plugin.
This is useful, if you don't want to add any custom behaviour to your module.xml.

### FirstSpirit components

The configuration for your implemented FirstSpirit components has to be placed somewhere, so that the plugin can generate a module.xml file.
Your project app, web app and other components can be annotated with our custom annotations that can be found in a seperate project.
In order to be able to use them, add the following dependency to your project:

compile 'com.espirit.moddev.components:annotations:1.5.0'

The annotations should be selfexplanatory. If a component doesn't provide annotations, it won't be treated for module.xml generation.
You could add tags to you module.xml template by hand in this case.

### Example

```xml
<!DOCTYPE module PUBLIC "module.dtd" "platform:/resource/fs-module-support/src/main/resources/dtds/module.dtd">
<module>
    <name>$name</name>
    <version>$version</version>
    <description>$description</description>
    <components>
$components
        <library>
            <resources>
                <resource>lib/$artifact</resource>
$resources
            </resources>
        </library>
    </components>
</module>
```

## Dependency management

The FSM plugin adds the following dependency configurations:

name | Description
-----|------------
fsServerCompile | Same as the usual compile configuration, but the dependency and all transitive dependencies are added to the module.xml with server scope
fsModuleCompile | Same as the usual compile configuration, but the dependency and all transitive dependencies are added to the module.xml with module scope
fsWebCompile | Same as the usual compile configuration, but the dependency and all transitive dependencies are added to the web-resources tag in the module.xml
fsProvidedCompile | Same as the usual compileOnly configuration - the dependency and all transitive dependencies are not added to the FSM archive.
fsProvidedRuntime | Same scope as the usual runtime dependency. Use if you want to do some kind of integration testing without FirstSpirit server.

Dependencies with other scopes then these (for example the regular compile scope) are not treated as a resource to be used for module.xml file generation.
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
    dependencies { classpath 'com.espirit.moddev:fsmgradleplugin:0.4.0-SNAPSHOT' }
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

    fsProvidedCompile 'de.espirit.firstspirit:fs-access:5.2.1306'

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
* [Gradle](http://www.gradle.org/downloads) 4.1+
