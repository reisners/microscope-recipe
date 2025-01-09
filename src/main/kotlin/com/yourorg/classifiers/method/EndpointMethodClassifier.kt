package com.yourorg.classifiers.method

import com.yourorg.MicroscopeService.Companion.CLASS_METHOD
import com.yourorg.MicroscopeService.Companion.NS_TBOX
import com.yourorg.MicroscopeService.Companion.createABoxURI
import com.yourorg.MicroscopeService.Companion.productOf
import com.yourorg.asMapOfMaps
import com.yourorg.createIndividualMethod
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.ExecutionContext
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.J

class EndpointMethodClassifier(model: OntModel) : AbstractMethodClassifier(model) {
    companion object {
        val PROPERTY_HAS_ENDPOINT = "${NS_TBOX}hasEndpoint"
        val CLASS_ENDPOINT = "${NS_TBOX}Endpoint"
        val PROPERTY_HAS_PATH = "${NS_TBOX}hasPath"
        val PROPERTY_HAS_HTTP_METHOD = "${NS_TBOX}hasHttpMethod"
    }

    override fun addToTBox() {
        model.createOntClass(CLASS_METHOD).also { method ->
            model.createObjectProperty(PROPERTY_HAS_ENDPOINT).also { hasEndpoint ->
                hasEndpoint.addDomain(method)
                hasEndpoint.addRange(
                    model.createOntClass(CLASS_ENDPOINT).also { endpoint ->
                        model.createDataProperty(PROPERTY_HAS_PATH).also { hasPath ->
                            hasPath.addDomain(endpoint)
                        }
                        model.createDataProperty(PROPERTY_HAS_HTTP_METHOD).also { hasHttpMethod ->
                            hasHttpMethod.addDomain(endpoint)
                        }
                    }
                )
            }
        }
    }

    override fun classify(
        jMethodDeclaration: J.MethodDeclaration,
        visitor: JavaVisitor<ExecutionContext>,
    ): Boolean {
        val cursor = visitor.cursor
        val jClassDeclaration = cursor.firstEnclosing(J.ClassDeclaration::class.java)!!
        val classAnnotations = jClassDeclaration.leadingAnnotations.asMapOfMaps()
        if (!classAnnotations.containsKey("org.springframework.web.bind.annotation.RestController")) {
            return false
        }
        val requestMappingClassLevel =
            classAnnotations["org.springframework.web.bind.annotation.RequestMapping"]!!["value"]
                ?.map { any -> any as String }
                ?.toTypedArray()
        val methodAnnotations = jMethodDeclaration.leadingAnnotations.asMapOfMaps()
        if (methodAnnotations.isEmpty()) {
            return false
        }
        val pathsAndMethods = extractPathsAndMethods(methodAnnotations)
        val requestMappingMethodLevel =
            pathsAndMethods.first.map { any -> any as String }.toTypedArray()
        val httpMethods = pathsAndMethods.second
        if (httpMethods.isEmpty()) {
            return false
        }
        val callerMethodType = jMethodDeclaration.methodType
        if (callerMethodType == null) {
            return false
        }
        val individualMethod = model.createIndividualMethod(callerMethodType)
        val classEndpoint = model.getOntClass(CLASS_ENDPOINT)
        httpMethods.forEach { httpMethod ->
            val individualEndpoint =
                model
                    .createIndividual(
                        createABoxURI(
                            classEndpoint,
                            httpMethod,
                            *requestMappingClassLevel ?: emptyArray(),
                            *requestMappingMethodLevel,
                        ),
                        classEndpoint,
                    )
                    .also {
                        it.addProperty(model.getDataProperty(PROPERTY_HAS_HTTP_METHOD), httpMethod)
                        productOf(requestMappingClassLevel, requestMappingMethodLevel)?.forEach {
                            path ->
                            it.addProperty(model.getDataProperty(PROPERTY_HAS_PATH), path)
                        }
                    }
            individualMethod.addProperty(
                model.getObjectProperty(PROPERTY_HAS_ENDPOINT),
                individualEndpoint,
            )
        }
        return true
    }

    private fun extractPathsAndMethods(
        annotationsAsMap: Map<String, Map<String, Array<Any?>?>>
    ): Pair<Array<Any?>, Set<String>> {
        return annotationsAsMap.keys
            .map { annotation ->
                when (annotation) {
                    "org.springframework.web.bind.annotation.RequestMapping" ->
                        annotationsAsMap[annotation]!!.get("value")!! to
                            (annotationsAsMap[annotation]
                                ?.get("method")
                                ?.map { any -> any as String }
                                ?.toSet() ?: emptySet())
                    "org.springframework.web.bind.annotation.GetMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("GET")
                    "org.springframework.web.bind.annotation.PutMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("PUT")
                    "org.springframework.web.bind.annotation.PostMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("POST")
                    "org.springframework.web.bind.annotation.DeleteMapping" ->
                        annotationsAsMap[annotation]!!.get("value")!! to setOf("DELETE")
                    else -> throw IllegalArgumentException(annotation)
                }
            }
            .first()
    }
}
