package com.yourorg.classifiers.method

import com.yourorg.MicroscopeService.Companion.CLASS_METHOD
import com.yourorg.MicroscopeService.Companion.NS_TBOX
import com.yourorg.MicroscopeService.Companion.createABoxURI
import com.yourorg.MicroscopeService.Companion.productOf
import com.yourorg.asMapOfMaps
import org.apache.jena.ontapi.model.OntIndividual
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.java.tree.J

class EndpointMethodClassifier(model: OntModel) : AbstractMethodClassifier(model) {
    companion object {
        val PROPERTY_HAS_ENDPOINT = "$NS_TBOX#hasEndpoint"
        val CLASS_ENDPOINT = "$NS_TBOX#Endpoint"
        val PROPERTY_HAS_PATH = "$NS_TBOX#hasPath"
        val PROPERTY_HAS_HTTP_METHOD = "$NS_TBOX#hasHttpMethod"
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
        individualMethod: OntIndividual,
        cursor: Cursor,
        jMethodDeclaration: J.MethodDeclaration,
    ): Boolean {
        val model = individualMethod.model
        val jClassDeclaration = cursor.firstEnclosing(J.ClassDeclaration::class.java)!!
        val classAnnotations = jClassDeclaration.leadingAnnotations.asMapOfMaps()
        if (!classAnnotations.containsKey("RequestMapping")) {
            return false
        }
        val requestMappingClassLevel =
            classAnnotations["RequestMapping"]!!["value"]
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
        httpMethods.forEach { httpMethod ->
            val individualEndpoint =
                model
                    .createIndividual(
                        createABoxURI(
                            httpMethod,
                            *requestMappingClassLevel ?: emptyArray(),
                            *requestMappingMethodLevel,
                        ),
                        model.getOntClass(CLASS_ENDPOINT),
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
        annotationsAsMap: Map<String, Map<String, Array<Any>>>
    ): Pair<Array<Any>, Set<String>> {
        return annotationsAsMap.keys
            .map { annotation ->
                when (annotation) {
                    "RequestMapping" ->
                        annotationsAsMap[annotation]!!.get("value")!! to
                            (annotationsAsMap[annotation]
                                ?.get("method")
                                ?.map { any -> any as String }
                                ?.toSet() ?: emptySet())
                    "GetMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("GET")
                    "PutMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("PUT")
                    "PostMapping" -> annotationsAsMap[annotation]!!.get("value")!! to setOf("POST")
                    "DeleteMapping" ->
                        annotationsAsMap[annotation]!!.get("value")!! to setOf("DELETE")
                    else -> throw IllegalArgumentException(annotation)
                }
            }
            .first()
    }
}
