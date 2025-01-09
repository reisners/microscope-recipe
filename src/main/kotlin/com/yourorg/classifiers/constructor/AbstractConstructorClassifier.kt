package com.yourorg.classifiers.constructor

import org.apache.jena.ontapi.model.OntIndividual
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaType

abstract class AbstractConstructorClassifier(val model: OntModel) {
    init {
        addToTBox()
    }

    abstract fun addToTBox()

    abstract fun classify(
        cursor: Cursor,
        constructor: J.NewClass,
    ): OntIndividual?
}
