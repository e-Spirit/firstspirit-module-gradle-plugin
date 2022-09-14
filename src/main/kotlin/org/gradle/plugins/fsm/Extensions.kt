package org.gradle.plugins.fsm

import org.gradle.jvm.tasks.Jar

/**
 * Sets a parameter for a [Jar] task's manifest, if it wasn't already set before.
 * If the parameter was already set, doesn't do anything.
 *
 * @param name    The name of the attribute to configure
 * @param value   The value of the attribute
 */
fun Jar.addManifestAttribute(name: String, value: Any) {
    manifest.attributes.putIfAbsent(name, value)
}