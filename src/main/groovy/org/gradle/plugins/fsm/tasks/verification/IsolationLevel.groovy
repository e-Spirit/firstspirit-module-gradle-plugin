package org.gradle.plugins.fsm.tasks.verification

enum IsolationLevel {

    /**
     * The build fails only if classes are used which are not part of the isolated runtime
     */
    IMPL_USAGE,

    /**
     * The build fails if classes are not part of the public API (also includes #IMPL_USAGE).
     * This is the default setting.
     */
    RUNTIME_USAGE,

    /**
     * The build fails if deprecated API is used (also includes #RUNTIME_USAGE and #IMPL_USAGE)
     */
    DEPRECATED_API_USAGE

}