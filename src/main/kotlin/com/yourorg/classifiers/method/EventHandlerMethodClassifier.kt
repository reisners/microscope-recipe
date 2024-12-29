package com.yourorg.classifiers.method

import com.yourorg.MicroscopeService.Companion.CLASS_METHOD
import com.yourorg.MicroscopeService.Companion.NS_TBOX
import org.apache.jena.ontapi.model.OntIndividual
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.java.tree.J

class EventHandlerMethodClassifier(model: OntModel) : AbstractMethodClassifier(model) {
    companion object {
        val CLASS_TOPIC = "$NS_TBOX#Topic"
        val PROPERTY_HAS_TOPIC = "$NS_TBOX#hasTopic"
    }

    override fun addToTBox() {
        model.createOntClass(CLASS_METHOD).also { method ->
            model.createObjectProperty(PROPERTY_HAS_TOPIC).also {
                it.addDomain(method)
                it.addRange(model.createOntClass(CLASS_TOPIC))
            }
        }
    }

    override fun classify(
        individualMethod: OntIndividual,
        cursor: Cursor,
        jMethodDeclaration: J.MethodDeclaration,
    ): Boolean {
        TODO("Not yet implemented")
    }
}
