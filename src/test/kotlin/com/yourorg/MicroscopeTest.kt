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

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.kotlin.KotlinParser
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import kotlin.io.path.Path
import kotlin.random.Random

internal class MicroscopeTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec
            .recipe(Microscope("/tmp/model.ttl"))
            .parser(
                KotlinParser.Builder()
                    .classpath("spring-core", "spring-web", "spring-context")
            )
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
                
                @org.springframework.web.bind.annotation.RestController
                @org.springframework.web.bind.annotation.RequestMapping(["/v1", "/alternativePath"])
                class MyController(val myService: MyService) {
                    @org.springframework.web.bind.annotation.RequestMapping(value = "/x", method = org.springframework.web.bind.annotation.RequestMethod.GET)
                    fun x(): List<MyEntity> = myService.doX()

                    @org.springframework.web.bind.annotation.DeleteMapping("/x")
                    fun deleteX() = myService.deleteX()
                }
                
                @org.springframework.stereotype.Service
                class MyService(val myRepository: MyRepository) {
                    fun doX(): List<MyEntity> = myRepository.findAll()
                    fun deleteX() = myRepository.deleteAll()
                }
                
                class MyEntity(
                    var id: String
                )
                
                @org.springframework.stereotype.Repository
                interface MyRepository {
                    fun findAll(): List<MyEntity>
                    fun deleteAll()
                }
              
              """
                    .trimIndent()
            )
        )
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
    fun eventHandler() {
        rewriteRun(
            org.openrewrite.kotlin.Assertions.kotlin(
                """
                package com.yourorg
                
                @Component
                class MyEventHandler(
                    sqsClient: SqsClient,
                    objectMapper: ObjectMapper,
                    @Qualifier("myEventConfig")
                    configuration: EventProcessorConfiguration,
                    myService: MyService
                ) {
                    private val processor =
                        EventProcessor(
                            sqsClient = sqsClient,
                            configuration = configuration,
                            objectMapper = objectMapper,
                            eventReference = jacksonTypeRef<MyEvent>(),
                            handleEvent = ::handleEvent,
                        )
                
                    fun handleEvent(event: MyEvent) {
                        myService.doX()
                    }
                }
                
                typealias MyEvent = com.borrowbox.gearbox.events.CommonBaseEvent<com.borrowbox.gearbox.events.EventMeta, String>

              
              """
                    .trimIndent()
            )
        )
    }

}
