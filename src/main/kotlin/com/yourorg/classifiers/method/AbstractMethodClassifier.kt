package com.yourorg.classifiers.method

import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.ExecutionContext
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.J

abstract class AbstractMethodClassifier(val model: OntModel) {
    init {
        addToTBox()
    }

    abstract fun addToTBox()

    abstract fun classify(
        jMethodDeclaration: J.MethodDeclaration,
        visitor: JavaVisitor<ExecutionContext>
    ): Boolean
}
