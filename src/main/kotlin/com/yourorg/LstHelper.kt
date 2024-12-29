package com.yourorg

import org.openrewrite.java.tree.Expression
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.tree.K

fun List<J.Annotation>.asMapOfMaps(): Map<String, Map<String, Array<Any?>>> {
    return this.associate { it: J.Annotation ->
        it.annotationType.toString() to (it.getArguments()?.asMap() ?: emptyMap())
    }
}

fun List<Expression>.asMap(): Map<String, Array<Any?>> {
    return this.mapNotNull { expression ->
            when (expression) {
                is J.Literal -> "value" to expression.extractValue()!!
                is J.Assignment ->
                    expression.let { it.variable.toString() to it.getAssignment().extractValue()!! }

                is K.ListLiteral -> "value" to expression.extractValue()!!
                else -> null
            }
        }
        .toMap()
}

fun Expression.extractValue(): Array<Any?>? {
    return when (this) {
        is J.Literal -> arrayOf(this.value)
        is K.ListLiteral ->
            this.elements
                .map { expression -> (expression as J.Literal).value }
                .toTypedArray()
        is J.FieldAccess -> this.name.extractValue()
        is J.Identifier -> arrayOf(this.simpleName)
        else -> null
    }
}
