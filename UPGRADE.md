# Upgrading from 5.x.x to 6.0.0

* When packaging an FSM file, only `fsm-resources` of the current project and its dependencies will be included.
Previously, all subprojects of the root project had been evaluated in this step. Please verify the contents of
your module after upgrading.

# Upgrading from 4.x.x to 4.5.0

* The FSM task will no longer be added to the default publications automatically. In order to publish the FSM,
add a configuration block like

```kotlin
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.assembleFSM)
            artifactId = "my-module-name"
        }
    }
}
```

# Upgrading from 4.0.0 to 4.1.0

* Resources defined with the `@Resource` annotation now use
`com.espirit.moddev.components.annotations.params.resource.Scope` instead of
`de.espirit.firstspirit.server.module.ModuleInfo.Scope`.
* The `@WebResource` annotation no longer defines a scope because it is not necessary.

# Upgrading from 3.x.x to 4.0.0

With version 4.0.0, support for legacy modules has been dropped. Additionally, some
deprecated features were removed.

## Support for legacy modules dropped

Since the "legacy mode" of FirstSpirit has been dropped with 2022-03 and the "isolation mode" being the
default for new installations for some time now, legacy modules can no longer be created with this
plug-in. For most users creating "hybrid" modules until now, a few changes need to be made during
the upgrade:

* When using a custom template for the module descriptor, it has to be named `module-isolated.xml`. If
  there is also a `module.xml`, this has to be deleted.
* The `resourceMode` of the `firstSpiritModule` extension is no longer available.
* Annotating a class with `@Resource` or `@WebResource` does not support the `mode` attribute anymore.
* When specifying a dependency with a configuration like `fsModuleCompile`, the `skipInLegacy` attribute
is no longer evaluated.

## New version of the annotations dependency: 2.0.0

The annotations dependency is added automatically added but may also be defined in subprojects. Please
ensure using at least version 2.0.0. 

## Removed deprecated configurations

Since version 3.0.2, the configurations `fsProvidedCompile` and `fsProvidedRuntime` were marked as deprecated
and are now removed entirely. When upgrading older modules, replace both of them with `compileOnly`.

## GadgetComponent

In FirstSpirit 2021-08 the `GadgetComponent` API has changed. After upgrading this plugin to 4.0.0 or later, only
the new version of the API will be supported. If you still need support for detecting older GadgetComponents,
you have to stick with version 3.x.x of this plug-in until your FirstSpirit libraries can be upgraded.

## Minimum JDK version: 11

Building modules with JDK 8 is no longer supported, the new minimum version is 11.