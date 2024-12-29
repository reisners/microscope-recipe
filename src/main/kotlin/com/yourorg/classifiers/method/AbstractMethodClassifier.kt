package com.yourorg.classifiers.method

import org.apache.jena.ontapi.model.OntIndividual
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.java.tree.J

abstract class AbstractMethodClassifier(val model: OntModel) {
    abstract fun addToTBox()

    abstract fun classify(
        individualMethod: OntIndividual,
        cursor: Cursor,
        jMethodDeclaration: J.MethodDeclaration,
    ): Boolean
}
