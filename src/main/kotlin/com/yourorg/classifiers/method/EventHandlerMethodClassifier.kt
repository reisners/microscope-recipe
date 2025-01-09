package com.yourorg.classifiers.method

import com.yourorg.MicroscopeService.Companion.CLASS_METHOD
import com.yourorg.MicroscopeService.Companion.NS_TBOX
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.ExecutionContext
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.J

@Deprecated("delete, as it is not needed (event handler method declarations cannot be detected as such)")
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
        jMethodDeclaration: J.MethodDeclaration,
        visitor: JavaVisitor<ExecutionContext>,
    ): Boolean {
        val cursor = visitor.cursor
        return false // not yet implemented
    }
}
