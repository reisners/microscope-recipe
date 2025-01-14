/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yourorg

import com.yourorg.MicroscopeService.Companion.CLASS_METHOD
import com.yourorg.MicroscopeService.Companion.PROPERTY_HAS_FQCN
import com.yourorg.MicroscopeService.Companion.PROPERTY_HAS_METHOD_NAME
import com.yourorg.classifiers.method.EndpointMethodClassifier.Companion.CLASS_ENDPOINT
import com.yourorg.classifiers.method.EndpointMethodClassifier.Companion.PROPERTY_HAS_ENDPOINT
import com.yourorg.classifiers.method.EndpointMethodClassifier.Companion.PROPERTY_HAS_HTTP_METHOD
import com.yourorg.classifiers.method.EndpointMethodClassifier.Companion.PROPERTY_HAS_PATH
import org.apache.jena.ontapi.OntModelFactory
import org.apache.jena.ontapi.OntSpecification
import org.apache.jena.ontapi.model.OntClass
import org.apache.jena.ontapi.model.OntDataProperty
import org.apache.jena.ontapi.model.OntModel
import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.Resource
import org.apache.jena.util.iterator.ExtendedIterator
import org.apache.jena.vocabulary.RDF
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.kotlin.KotlinParser
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import java.io.FileInputStream
import java.util.*

internal class MicroscopeTest : RewriteTest {
    val modelFilename = "/tmp/model-${UUID.randomUUID()}.ttl"

    override fun defaults(spec: RecipeSpec) {
        spec
            .recipe(Microscope(modelFilename))
            .parser(
                KotlinParser.Builder()
                    .classpath(
                        "spring-core",
                        "spring-web",
                        "spring-context",
                        "spring-beans",
                        "gearbox-commons",
                        "gearbox-events",
                        "gearbox-retrofit-core",
                        "gearbox-sqs-eventprocessor",
                        "sqs",
                        "jackson-databind",
                        "jackson-core",
                        "jackson-module-kotlin",
                        "sqs",
                        "aws-core",
                        "sdk-core",
                        "regions",
                        "utils",
                    )
            )
    }

    fun readModel(): OntModel = OntModelFactory.createModel(OntSpecification.OWL1_DL_MEM_RDFS_INF).apply {
        FileInputStream(this@MicroscopeTest.modelFilename).use {
            read(it, null, "TTL");
        }
    }

    @Test
    fun basic() {
        rewriteRun(
            org.openrewrite.kotlin.Assertions.kotlin(
                """
                    class A(val b: B) {
                      fun x() = b.y()
                    }
                    
                    class B {
                      fun y() {
                          Math.abs(-1)
                      }
                    }              
              """
                    .trimIndent()
            )
        )
        val model = readModel()
        val propertyMethodName = model.getDataProperty(PROPERTY_HAS_METHOD_NAME)
        val propertyClassName = model.getDataProperty(PROPERTY_HAS_FQCN)
        val individualMethods = model.listIndividualsWithClass(model.getOntClass(CLASS_METHOD)).filterKeep { it.hasProperty(propertyMethodName) }.toList()
        assertThat(individualMethods).hasSize(2)
        assertThat(individualMethods.extractDataProperty(model.getDataProperty(PROPERTY_HAS_METHOD_NAME)).mapNotNull { it?.value as String }.toSet()).isEqualTo(setOf("x", "y"))
    }

    @Test
    fun constructorInvocation() {
        rewriteRun(
                    org.openrewrite.kotlin.Assertions.kotlin(
                """
                class A(val x: String)
                val a = A("x")
                val bc = org.springframework.util.comparator.BooleanComparator(false) // this requires spring-core being on the classpath
                val pair = kotlin.Pair(1, "y") // currently, rewrite-kotlin only supports kotlin 1.9; this line breaks if kotlin 2.1 is on the classpath
              """
                    .trimIndent()
            )
        )
    }

    @Test
    fun restController() {
        rewriteRun(
            org.openrewrite.kotlin.Assertions.kotlin(
                """
                package com.yourorg
                import org.springframework.web.bind.annotation.RestController
                import org.springframework.web.bind.annotation.RequestMapping
                import org.springframework.web.bind.annotation.DeleteMapping
                import org.springframework.stereotype.Service
                import org.springframework.stereotype.Repository

                @RestController
                @RequestMapping(["/v1", "/alternativePath"])
                class MyController(val myService: MyService) {
                    @RequestMapping(value = "/x", method = org.springframework.web.bind.annotation.RequestMethod.GET)
                    fun x(): List<MyEntity> = myService.doX()

                    @DeleteMapping("/x")
                    fun deleteX() = myService.doDeleteX()
                }
                
                @Service
                class MyService(val myRepository: MyRepository) {
                    fun doX(): List<MyEntity> = myRepository.findAll()
                    fun doDeleteX() = myRepository.deleteAll()
                }
                
                class MyEntity(
                    var id: String
                )
                
                @Repository
                interface MyRepository {
                    fun findAll(): List<MyEntity>
                    fun deleteAll()
                }
              
              """
                    .trimIndent()
            )
        )
        val model = readModel()
        val propertyMethodName = model.getDataProperty(PROPERTY_HAS_METHOD_NAME)
        val propertyClassName = model.getDataProperty(PROPERTY_HAS_FQCN)
        val individualMethods = model.listIndividualsWithClass(model.getOntClass(CLASS_METHOD)).filterKeep { it.hasProperty(propertyMethodName) }.toList()
        assertThat(individualMethods).hasSize(4)
        assertThat(individualMethods.map { "${it.getProperty(propertyClassName)?.`object`?.asLiteral()}.${it.getProperty(propertyMethodName)?.`object`?.asLiteral()}" }.toSet())
            .isEqualTo(setOf(
                "com.yourorg.MyController.x",
                "com.yourorg.MyController.deleteX",
                "com.yourorg.MyService.doX",
                "com.yourorg.MyService.doDeleteX"
            ))
        val propertyHttpMethod = model.getDataProperty(PROPERTY_HAS_HTTP_METHOD)
        val propertyPath = model.getDataProperty(PROPERTY_HAS_PATH)
        val individualEndpoints = model.listIndividualsWithClass(model.getOntClass(CLASS_ENDPOINT)).toList()
        assertThat(individualEndpoints).hasSize(2)
        assertThat(individualEndpoints.map { "${it.getProperty(propertyHttpMethod)?.`object`?.asLiteral()} ${it.getProperty(propertyPath)?.`object`?.asLiteral()}" }.toSet())
            .isEqualTo(setOf(
                "GET /v1/x",
                "DELETE /v1/x",
            ))
        val endpointsByMethod = individualEndpoints.associateBy { it.getProperty(propertyHttpMethod)?.`object`?.asLiteral().toString() }
        val propertyEndpoint = model.getDataProperty(PROPERTY_HAS_ENDPOINT)
        val methodsGET = model.listSubjectsWithProperty(propertyEndpoint, endpointsByMethod["GET"]).toList()
        val methodsDELETE = model.listSubjectsWithProperty(propertyEndpoint, endpointsByMethod["DELETE"]).toList()
        assertThat(methodsGET).hasSize(1)
        assertThat(methodsGET[0].getProperty(propertyMethodName)?.`object`?.asLiteral().toString()).isEqualTo("x")
        assertThat(methodsDELETE).hasSize(1)
        assertThat(methodsDELETE[0].getProperty(propertyMethodName)?.`object`?.asLiteral().toString()).isEqualTo("deleteX")
    }

    @Test
    fun springBeanProperties() {
        rewriteRun(
            org.openrewrite.kotlin.Assertions.kotlin(
                """
                package com.yourorg
                
                @org.springframework.context.annotation.Configuration
                open class SqsConfiguration {
                
                    val x = com.borrowbox.gearbox.sqs.eventprocessor.domain.EventProcessorConfiguration(enabled = true, queueUrl = "xUrl", waitTimeInSeconds = 10)

                    @org.springframework.context.annotation.Bean(value = ["firstEventConfig"])
                    open fun firstEventConfiguration(@org.springframework.beans.factory.annotation.Value("\${'$'}{sqs.receiver.first-events.queue}") queueUrl: String): com.borrowbox.gearbox.sqs.eventprocessor.domain.EventProcessorConfiguration = com.borrowbox.gearbox.sqs.eventprocessor.domain.EventProcessorConfiguration(enabled = true, queueUrl = queueUrl, waitTimeInSeconds = 10)
    
                    @org.springframework.context.annotation.Bean(value = ["secondEventConfig"])
                    open fun secondEventConfiguration(@org.springframework.beans.factory.annotation.Value("\${'$'}{sqs.receiver.second-events.queue}") queueUrl: String) =
                        com.borrowbox.gearbox.sqs.eventprocessor.domain.EventProcessorConfiguration(enabled = true, queueUrl = queueUrl, waitTimeInSeconds = 10)
                }              
              """
                    .trimIndent()
            )
        )
    }

    @Test
    fun constructorParameterAnnotation() {
        rewriteRun(
            org.openrewrite.kotlin.Assertions.kotlin(
                """
                package com.yourorg
                
                class MyEventHandler(
                    sqsClient: software.amazon.awssdk.services.sqs.SqsClient,
                    objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
                    @org.springframework.beans.factory.annotation.Qualifier("myEventConfig")
                    configuration: com.borrowbox.gearbox.sqs.eventprocessor.domain.EventProcessorConfiguration,
                    val myService: MyService
                )

                class MyService
              """
                    .trimIndent()
            )
        )
    }

    @Test
    fun eventHandler() {
        rewriteRun(
            org.openrewrite.kotlin.Assertions.kotlin(
                """
                package com.yourorg
                
                data class MyEventContent(val s: String)

                typealias MyEvent = com.borrowbox.gearbox.events.CommonBaseEvent<com.borrowbox.gearbox.events.EventMeta, MyEventContent>

                @org.springframework.context.annotation.Configuration
                class MyConfiguration {
                    @org.springframework.context.annotation.Bean(value = ["myEventConfig"])
                    fun myEventConfiguration() =
                        com.borrowbox.gearbox.sqs.eventprocessor.domain.EventProcessorConfiguration(enabled = true, queueUrl = "myQueueUrl", waitTimeInSeconds = 10)                
                }

                @org.springframework.stereotype.Component
                class MyEventHandler(
                    sqsClient: software.amazon.awssdk.services.sqs.SqsClient,
                    objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
                    @org.springframework.beans.factory.annotation.Qualifier("myEventConfig")
                    configuration: com.borrowbox.gearbox.sqs.eventprocessor.domain.EventProcessorConfiguration,
                    val myService: MyService
                ) {
                                private val processor =
                        com.borrowbox.gearbox.sqs.eventprocessor.processor.EventProcessor(
                            sqsClient = sqsClient,
                            configuration = configuration,
                            objectMapper = objectMapper,
                            eventReference = com.fasterxml.jackson.module.kotlin.jacksonTypeRef<MyEvent>(),
                            handleEvent = this::handleEvent,
                        )
                
                    fun handleEvent(event: MyEvent) {
                        myService.doX()
                    }
                }
                
                @org.springframework.stereotype.Service
                class MyService {
                    fun doX() {}
                }
              """
                    .trimIndent()
            )
        )
    }

}

private fun List<Resource>.extractDataProperty(dataProperty: OntDataProperty?): List<Literal?> =
    this.mapNotNull { it.getProperty(dataProperty)?.`object` }
        .filter { it.isLiteral }
        .map { it.asLiteral() }

private fun OntModel.listIndividualsWithClass(classMethod: OntClass.Named?): ExtendedIterator<Resource> =
    listSubjects().filterKeep { it.hasProperty(RDF.type, classMethod) }
