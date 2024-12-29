package com.yourorg

import com.yourorg.classifiers.method.EndpointMethodClassifier
import com.yourorg.classifiers.method.EventHandlerMethodClassifier
import com.yourorg.classifiers.method.RetrofitClientMethodClassifier
import org.apache.jena.ontapi.OntModelFactory
import org.apache.jena.ontapi.model.OntIndividual
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.tree.K

class MicroscopeService {
    val model: OntModel = createModel()
    val methodClassifiers =
        listOf(
            EndpointMethodClassifier(model),
            EventHandlerMethodClassifier(model),
            RetrofitClientMethodClassifier(model),
        )

    companion object {
        val NS_TBOX = "http://yourorg.com/ontology"
        val NS_ABOX = "http://yourorg.com/data"
        val CLASS_METHOD = "$NS_TBOX#Method"
        val PROPERTY_CALLS = "$NS_TBOX#calls"
        val PROPERTY_HAS_METHOD_NAME = "$NS_TBOX#hasMethodName"
        val PROPERTY_HAS_FQN = "$NS_TBOX#hasFqn"

        fun createModel(): OntModel {
            val model =
                OntModelFactory.createModel().apply {
                    createOntClass(CLASS_METHOD).apply {
                        createObjectProperty(PROPERTY_CALLS).also {
                            it.addDomain(this)
                            it.addRange(this)
                        }
                        createDataProperty(PROPERTY_HAS_METHOD_NAME).also { it.addDomain(this) }
                        createDataProperty(PROPERTY_HAS_FQN).also { it.addDomain(this) }
                    }
                }
            return model
        }

        fun createABoxURI(vararg elements: String): String {
            val uuid = Namespace.URL.uuid3(elements.joinToString("#"))
            return "$NS_ABOX$uuid"
        }

        fun productOf(
            requestMappingClassLevel: Array<String>?,
            requestMappingMethodLevel: Array<String>?,
        ): Set<String>? {
            if (requestMappingClassLevel == null || requestMappingClassLevel.size == 0) {
                return requestMappingMethodLevel?.let { setOf(*it) }
            }
            return requestMappingClassLevel
                .flatMap { cl: String ->
                    requestMappingMethodLevel?.map { ml: String? -> cl + ml } ?: listOf(cl)
                }
                .toSet()
        }
    }

    fun analyzeMethodDeclaration(methodDeclaration: K.MethodDeclaration, cursor: Cursor) {
        analyzeMethodDeclaration(methodDeclaration.methodDeclaration, cursor)
    }

    fun analyzeMethodDeclaration(
        methodDeclaration: J.MethodDeclaration,
        cursor: Cursor,
    ): OntIndividual? {
        val methodType = methodDeclaration.methodType
        return if (methodType != null) {
            val callingTypeName = methodType.declaringType.getFullyQualifiedName()
            val callingMethodName = methodType.name
            val methodUri = createABoxURI(callingTypeName, callingMethodName)
            model.createIndividual(methodUri, model.getOntClass(CLASS_METHOD)).apply {
                classifyMethod(methodDeclaration, cursor, this)
            }
        } else {
            null
        }
    }

    fun analyzeMethodInvocation(
        methodInvocation: J.MethodInvocation,
        cursor: Cursor,
        individualMethodDeclaration: OntIndividual,
    ) {
        val declaringTypeName: String? =
            methodInvocation.methodType?.declaringType?.fullyQualifiedName
        val relevantReceiverType = declaringTypeName?.startsWith("kotlin.") == false
        if (methodInvocation.methodType != null && relevantReceiverType) {
            val invokedMethodName = methodInvocation.name.toString()
            val individualCallee =
                model.createIndividual(
                    createABoxURI(declaringTypeName, invokedMethodName),
                    model.getOntClass(CLASS_METHOD),
                )
            individualMethodDeclaration.addProperty(
                model.getObjectProperty(PROPERTY_CALLS),
                individualCallee,
            )
        }
    }

    private fun classifyMethod(
        jMethodDeclaration: J.MethodDeclaration,
        cursor: Cursor,
        individualMethod: OntIndividual,
    ) {
        individualMethod.addProperty(
            individualMethod.model.getDataProperty(PROPERTY_HAS_METHOD_NAME),
            jMethodDeclaration.toString(),
        )
        val methodType = requireNotNull(jMethodDeclaration.methodType) { "should not occur" }
        individualMethod.addProperty(
            individualMethod.model.getDataProperty(PROPERTY_HAS_FQN),
            methodType.declaringType.fullyQualifiedName,
        )
        methodClassifiers.forEach { it.classify(individualMethod, cursor, jMethodDeclaration) }
    }
}
