package org.gradle.plugins.fsm.descriptor

import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.TextElement

fun Node.textContent(): String {
    return (children[0] as TextElement).text
}

fun Node.childText(name: String): String {
    return (first(name).children[0] as TextElement).text
}