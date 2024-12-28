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

import com.yourorg.table.MicroscopeReport
import org.apache.jena.ontapi.common.OntVocabulary.RDFS
import org.apache.jena.ontapi.model.OntStatement
import org.apache.jena.vocabulary.RDF
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.UncheckedConsumer
import java.util.function.Consumer

internal class MicroscopeTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(Microscope("/tmp/model.ttl"))
            .parser(JavaParser.fromJavaVersion().classpath("spring-core", "spring-web", "spring-context"))
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
                      println("hello world")
                  }
              }
              
              """.trimIndent()
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
              
              """.trimIndent()
            )
        )
    }
}
