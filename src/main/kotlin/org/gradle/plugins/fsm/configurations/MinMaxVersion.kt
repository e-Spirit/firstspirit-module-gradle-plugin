package org.gradle.plugins.fsm.configurations

/**
 * Allowed minimum and maximum versions may be specified using the fsDependency extension
 *
 * @see fsDependency
 */
data class MinMaxVersion(val dependency: String,
                         val minVersion: String? = null,
                         val maxVersion: String? = null)
