package org.gradle.plugins.fsm.util

import org.assertj.core.api.AbstractAssert
import org.gradle.api.Task

class TaskAssert(actual: Task): AbstractAssert<TaskAssert, Task>(actual, TaskAssert::class.java) {

    fun dependsOn(vararg taskNames: String): TaskAssert {
        val dependencies = actual.taskDependencies.getDependencies(actual).map { it.name }
        for (taskName in taskNames) {
            if (!dependencies.contains(taskName)) {
                val existing = dependencies.joinToString(", ")
                failWithMessage("Expected task '${actual.name}' to depend on '$taskName' but depends on '$existing'")
            }
        }
        return this
    }

    companion object {
        fun assertThat(actual: Task): TaskAssert {
            return TaskAssert(actual)
        }
    }

}