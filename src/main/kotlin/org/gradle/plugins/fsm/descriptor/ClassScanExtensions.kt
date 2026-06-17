package org.gradle.plugins.fsm.descriptor

import io.github.classgraph.AnnotationClassRef
import io.github.classgraph.AnnotationEnumValue
import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassInfo
import kotlin.reflect.KClass

fun AnnotationInfo.isClass(annotationClass: KClass<*>): Boolean {
    return name == annotationClass.java.name
}

fun AnnotationInfo.getString(parameter: String): String {
    return parameterValues[parameter].value.toString()
}

fun AnnotationInfo.getEnumValue(parameter: String): AnnotationEnumValue {
    return (parameterValues[parameter].value as AnnotationEnumValue)
}

/**
* Returns the annotation value for the given parameter. Returns `null` if the value
* equals the default value for the parameter
*/
fun AnnotationInfo.getStringOrNull(parameter: String, defaultValue: String): String? {
    val value = parameterValues[parameter].value.toString()
    return if (value != defaultValue) {
        value
    } else {
        null
    }
}

/**
 * Returns the name of the class for the given parameter. Returns `null` if the class
 * matches the default class for the parameter, as we do not enter default classes in the module.xml
 */
fun AnnotationInfo.getClassNameOrNull(parameter: String, defaultClass: KClass<*>): String? {
    val classInfo = parameterValues[parameter].value as AnnotationClassRef
    return if (classInfo.name != defaultClass.qualifiedName) {
        classInfo.name
    } else {
        null
    }
}

fun AnnotationInfo.getClassNames(parameter: String): List<ClassInfo> {
    return (parameterValues[parameter].value as Array<*>)
                .map { it as AnnotationClassRef }
                .map { it.classInfo }
}

fun AnnotationInfo.getEnumValues(parameter: String): List<AnnotationEnumValue> {
    return (parameterValues[parameter].value as Array<*>).map { it as AnnotationEnumValue }
}

fun AnnotationInfo.getAnnotationValues(parameter: String): List<AnnotationInfo> {
    return (parameterValues[parameter].value as Array<*>).map { it as AnnotationInfo }
}

/**
 * Returns `true` if this class extends or implements any of the given fully-qualified type names,
 * considering both the superclass chain and implemented interfaces. `superclass` alone never reports
 * interfaces, so an interface-typed supertype (e.g. `WebApp`, `ProjectApp`) must be matched here.
 */
fun ClassInfo.hasSupertype(typeNames: Set<String?>): Boolean {
    val supertypes = (superclasses + interfaces).map { it.name } + name
    return supertypes.any { it in typeNames }
}
