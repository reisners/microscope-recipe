package com.yourorg

import org.apache.jena.ontapi.OntModelFactory
import org.apache.jena.ontapi.model.OntIndividual
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.java.tree.Expression
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.tree.K

class MicroscopeService {
    val model: OntModel = createModel()

    companion object {
        val NS_TBOX = "http://yourorg.com/ontology"
        val NS_ABOX = "http://yourorg.com/data"
        val CLASS_METHOD = "$NS_TBOX#Method"
        val CLASS_ENDPOINT = "$NS_TBOX#Endpoint"
        val PROPERTY_CALLS = "$NS_TBOX#calls"
        val CLASS_TOPIC = "$NS_TBOX#Topic"
        val PROPERTY_HAS_METHOD_NAME = "$NS_TBOX#hasMethodName"
        val PROPERTY_HAS_FQN = "$NS_TBOX#hasFqn"
        val PROPERTY_HAS_ENDPOINT = "$NS_TBOX#hasEndpoint"
        val PROPERTY_HAS_PATH = "$NS_TBOX#hasPath"
        val PROPERTY_HAS_HTTP_METHOD = "$NS_TBOX#hasHttpMethod"
        val PROPERTY_HAS_TOPIC = "$NS_TBOX#hasTopic"
        fun createModel(): OntModel {
            val model = OntModelFactory.createModel().apply {
                createOntClass(CLASS_METHOD).apply {
                    createObjectProperty(PROPERTY_CALLS).also {
                        it.addDomain(this)
                        it.addRange(this)
                    }
                    createObjectProperty(PROPERTY_HAS_TOPIC).also {
                        it.addDomain(this)
                        it.addRange(
                            createOntClass(CLASS_TOPIC)
                        )
                    }
                    createObjectProperty(PROPERTY_HAS_ENDPOINT).also {
                        it.addDomain(this)
                        it.addRange(
                            createOntClass(CLASS_ENDPOINT).apply {
                                createDataProperty(PROPERTY_HAS_PATH).also {
                                    it.addDomain(this)
                                }
                                createDataProperty(PROPERTY_HAS_HTTP_METHOD).also {
                                    it.addDomain(this)
                                }
                            }
                        )
                    }
                    createDataProperty(PROPERTY_HAS_METHOD_NAME).also {
                        it.addDomain(this)
                    }
                    createDataProperty(PROPERTY_HAS_FQN).also {
                        it.addDomain(this)
                    }
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
            requestMappingMethodLevel: Array<String>?
        ): kotlin.collections.Set<String>? {
            if (requestMappingClassLevel == null || requestMappingClassLevel.size == 0) {
                return requestMappingMethodLevel?.let { setOf(*it) }
            }
            return requestMappingClassLevel.flatMap { cl: String ->
                requestMappingMethodLevel?.map { ml: String? -> cl + ml } ?: listOf(cl)
            }
                .toSet()
        }
    }

    fun analyzeMethodDeclaration(methodDeclaration: K.MethodDeclaration, cursor: Cursor) {
        analyzeMethodDeclaration(methodDeclaration.methodDeclaration, cursor)
    }

    fun analyzeMethodDeclaration(methodDeclaration: J.MethodDeclaration, cursor: Cursor): OntIndividual? {
        val methodType = methodDeclaration.methodType
        return if (methodType != null) {
            val callingTypeName = methodType.declaringType.getFullyQualifiedName()
            val callingMethodName = methodType.name
            val methodUri = createABoxURI(callingTypeName, callingMethodName)
            model.createIndividual(methodUri,model.getOntClass(CLASS_METHOD)).apply {
                classifyMethod(methodDeclaration, cursor, this)
            }
        } else {
            null
        }
    }

    fun analyzeMethodInvocation(
        methodInvocation: J.MethodInvocation,
        cursor: Cursor,
        individualMethodDeclaration: OntIndividual
    ) {
        val declaringTypeName: String = methodInvocation.methodType.declaringType.fullyQualifiedName
        val builtInType = declaringTypeName.startsWith("kotlin.")
        if (methodInvocation.methodType != null && !builtInType) {
            val invokedMethodName = methodInvocation.name.toString()
            val individualCallee = model.createIndividual(createABoxURI(declaringTypeName, invokedMethodName), model.getOntClass(CLASS_METHOD))
            individualMethodDeclaration.addProperty(
                model.getObjectProperty(PROPERTY_CALLS),
                individualCallee
            )
        }
    }

    private fun classifyMethod(jMethodDeclaration: J.MethodDeclaration, cursor: Cursor, individualMethod: OntIndividual) {
        individualMethod.addProperty(
            individualMethod.model.getDataProperty(PROPERTY_HAS_METHOD_NAME),
            jMethodDeclaration.toString()
        )
        individualMethod.addProperty(
            individualMethod.model.getDataProperty(PROPERTY_HAS_FQN),
            jMethodDeclaration.methodType.declaringType.fullyQualifiedName
        )
        addEndpoints(individualMethod, cursor, jMethodDeclaration)
    }

    private fun addEndpoints(individualMethod: OntIndividual, cursor: Cursor, jMethodDeclaration: J.MethodDeclaration) {
        val jClassDeclaration = cursor.firstEnclosing(J.ClassDeclaration::class.java)!!
        val classAnnotations = jClassDeclaration.leadingAnnotations.asMapOfMaps()
        if (!classAnnotations.containsKey("RequestMapping")) {
            return
        }
        val requestMappingClassLevel = classAnnotations["RequestMapping"]!!["value"]?.let { it.map { any -> any as String }.toTypedArray() }
        val methodAnnotations = jMethodDeclaration.leadingAnnotations.asMapOfMaps()
        if (methodAnnotations.isEmpty()) {
            return
        }
        val pathsAndMethods = extractPathsAndMethods(methodAnnotations)
        val requestMappingMethodLevel = pathsAndMethods.first.map { any -> any as String }.toTypedArray()
        val httpMethods = pathsAndMethods.second
        httpMethods.forEach { httpMethod ->
            val individualEndpoint = model.createIndividual(createABoxURI(httpMethod, *requestMappingClassLevel ?: emptyArray(), *requestMappingMethodLevel ?: emptyArray()), model.getOntClass(CLASS_ENDPOINT)).also {
                it.addProperty(model.getDataProperty(PROPERTY_HAS_HTTP_METHOD), httpMethod)
                productOf(requestMappingClassLevel, requestMappingMethodLevel)
                    ?.forEach { path -> it.addProperty(model.getDataProperty(PROPERTY_HAS_PATH), path) }
            }
            individualMethod.addProperty(model.getObjectProperty(PROPERTY_HAS_ENDPOINT), individualEndpoint)
        }
    }

    private fun extractPathsAndMethods(annotationsAsMap: Map<String, Map<String, Array<Any>>>): Pair<Array<Any>, Set<String>> {
        return annotationsAsMap.keys.map { annotation ->
            when (annotation) {
                "RequestMapping" -> annotationsAsMap[annotation]!!.get("value")!! to (annotationsAsMap[annotation]?.get("method")?.map { any -> any as String }?.toSet() ?: emptySet())
                "GetMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("GET")
                "PutMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("PUT")
                "PostMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("POST")
                "DeleteMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("DELETE")
                else -> throw IllegalArgumentException(annotation)
            }
        }
        .first()
    }

    fun List<J.Annotation>.asMapOfMaps(): Map<String, Map<String, Array<Any>>> {
        return this.associate { it: J.Annotation -> it.getSimpleName() to (it.getArguments()?.asMap() ?: emptyMap()) }
    }

    fun List<Expression>.asMap(): Map<String, Array<Any>> {
        return this.mapNotNull { expression ->
            when (expression) {
                is J.Literal -> "value" to expression.extractValue()!!
                is J.Assignment -> (expression as J.Assignment).let {
                    it.variable.toString() to it.getAssignment().extractValue()!!
                }
                is K.ListLiteral -> "value" to expression.extractValue()!!
                else -> null
            }
        }.toMap()
    }

    fun Expression.extractValue(): Array<Any>? {
        return when (this) {
            is J.Literal -> arrayOf((this as J.Literal).value)
            is K.ListLiteral -> (this as K.ListLiteral).elements.map { expression -> (expression as J.Literal).value }.toTypedArray()
            is J.FieldAccess -> (this as J.FieldAccess).name.extractValue()
            is J.Identifier -> arrayOf((this as J.Identifier).simpleName)
            else -> null
        }
    }
}
