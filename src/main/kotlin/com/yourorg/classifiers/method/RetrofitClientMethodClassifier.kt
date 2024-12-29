package com.yourorg.classifiers.method

import com.yourorg.MicroscopeService.Companion.CLASS_METHOD
import com.yourorg.MicroscopeService.Companion.NS_TBOX
import com.yourorg.MicroscopeService.Companion.createABoxURI
import com.yourorg.asMapOfMaps
import org.apache.jena.ontapi.model.OntIndividual
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.java.tree.J

class RetrofitClientMethodClassifier(model: OntModel) : AbstractMethodClassifier(model) {
    companion object {
        val PROPERTY_IS_RETROFIT_CLIENT = "$NS_TBOX#isRetrofitClient"
        val CLASS_RETROFIT_CLIENT = "$NS_TBOX#RetrofitClient"
        val PROPERTY_HAS_PATH = "$NS_TBOX#hasPath"
        val PROPERTY_HAS_HTTP_METHOD = "$NS_TBOX#hasHttpMethod"
    }

    override fun addToTBox() {
        model.createOntClass(CLASS_METHOD).also { method ->
            model.createObjectProperty(PROPERTY_IS_RETROFIT_CLIENT).also { hasEndpoint ->
                hasEndpoint.addDomain(method)
                hasEndpoint.addRange(
                    model.createOntClass(CLASS_RETROFIT_CLIENT).also { endpoint ->
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
        val methodAnnotations = jMethodDeclaration.leadingAnnotations.asMapOfMaps()
        if (methodAnnotations.isEmpty()) {
            return false
        }
        val pathsAndMethods = extractPathsAndMethods(methodAnnotations)
        if (pathsAndMethods == null) {
            return false
        }
        val requestMappingMethodLevel =
            pathsAndMethods.first.map { any -> any as String }.toTypedArray()
        val httpMethods = pathsAndMethods.second
        httpMethods.forEach { httpMethod ->
            val individualEndpoint =
                model
                    .createIndividual(
                        createABoxURI(httpMethod, *requestMappingMethodLevel),
                        model.getOntClass(CLASS_RETROFIT_CLIENT),
                    )
                    .also {
                        it.addProperty(model.getDataProperty(PROPERTY_HAS_HTTP_METHOD), httpMethod)
                        requestMappingMethodLevel.forEach { path ->
                            it.addProperty(model.getDataProperty(PROPERTY_HAS_PATH), path)
                        }
                    }
            individualMethod.addProperty(
                model.getObjectProperty(PROPERTY_IS_RETROFIT_CLIENT),
                individualEndpoint,
            )
        }
        return true
    }

    private fun extractPathsAndMethods(
        annotationsAsMap: Map<String, Map<String, Array<Any?>>>
    ): Pair<Array<Any?>, Set<String>>? {
        return annotationsAsMap.keys.firstNotNullOfOrNull { annotation ->
            when (annotation) {
                "retrofit2.http.GET" -> annotationsAsMap[annotation]!!["value"]!! to setOf("GET")
                "retrofit2.http.PUT" -> annotationsAsMap[annotation]!!["value"]!! to setOf("PUT")
                "retrofit2.http.POST" -> annotationsAsMap[annotation]!!["value"]!! to setOf("POST")
                "retrofit2.http.DELETE" -> annotationsAsMap[annotation]!!["value"]!! to setOf("DELETE")
                "retrofit2.http.HEAD" -> annotationsAsMap[annotation]!!["value"]!! to setOf("HEAD")
                "retrofit2.http.PATCH" -> annotationsAsMap[annotation]!!["value"]!! to setOf("PATCH")
                else -> null
            }
        }
    }
}
