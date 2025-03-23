package com.yourorg.classifiers.method.beanfactory

import com.yourorg.MicroscopeService.Companion.CLASS_METHOD
import com.yourorg.MicroscopeService.Companion.NS_TBOX
import com.yourorg.MicroscopeService.Companion.createABoxURI
import com.yourorg.annotationMap
import com.yourorg.asMap
import com.yourorg.createIndividualMethod
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.java.tree.J
import org.openrewrite.kotlin.tree.K
import java.util.regex.Pattern

class EventProcessessorConfigurationBFMClassifier(model: OntModel) : AbstractBeanFactoryMethodClassifier(model) {
    companion object {
        val PATTERN = Pattern.compile(Pattern.quote("com.borrowbox.gearbox.sqs.eventprocessor.domain.EventProcessorConfiguration"))
        val PROPERTY_HAS_CONFIG = "${NS_TBOX}hasConfig"
        val CLASS_CONFIG = "${NS_TBOX}EventProcessorConfiguration"
        val PROPERTY_HAS_QUEUE_URL = "${NS_TBOX}hasQueueURL"
        val PROPERTY_HAS_QUALIFIER = "${NS_TBOX}hasQualifier"
    }

    override fun addToTBox() {
        model.createOntClass(CLASS_METHOD).also { method ->
            model.createObjectProperty(PROPERTY_HAS_CONFIG).also { hasConfig ->
                hasConfig.addDomain(method)
                hasConfig.addRange(
                    model.createOntClass(CLASS_CONFIG).also { config ->
                        model.createDataProperty(PROPERTY_HAS_QUEUE_URL).also { hasQueueArn ->
                            hasQueueArn.addDomain(config)
                        }
                        model.createDataProperty(PROPERTY_HAS_QUALIFIER).also { hasQualifier ->
                            hasQualifier.addDomain(config)
                        }
                    }
                )
            }
        }
    }

    override fun doClassify(
        cursor: Cursor,
        jMethodDeclaration: J.MethodDeclaration,
    ): Boolean {
        if (jMethodDeclaration.methodType?.returnType?.isAssignableFrom(PATTERN) == false) {
            return false
        }
        val annotations = jMethodDeclaration.leadingAnnotations.annotationMap(cursor)
        val qualifiers: List<String>? = annotations["org.springframework.context.annotation.Bean"]?.get("value")?.mapNotNull { it as String }
        if (qualifiers == null) {
            return false
        }
        val callerMethodType = jMethodDeclaration.methodType
        if (callerMethodType == null) {
            return false
        }
        val individualMethod = model.createIndividualMethod(callerMethodType)
        val classConfig = model.getOntClass(CLASS_CONFIG)
        qualifiers.forEach { qualifier ->
            val individualConfig = model.createIndividual(createABoxURI(classConfig, qualifier), classConfig).apply {
                addProperty(model.getDataProperty(PROPERTY_HAS_QUALIFIER), qualifier)
                addProperty(model.getDataProperty(PROPERTY_HAS_QUEUE_URL), extractQueueUrl(jMethodDeclaration))
            }
            individualMethod.addProperty(
                model.getObjectProperty(PROPERTY_HAS_CONFIG),
                individualConfig,
            )
        }
        return true
    }

    private fun extractQueueUrl(jMethodDeclaration: J.MethodDeclaration): String {
        val returnStatement = jMethodDeclaration.body?.statements?.first { s -> s is K.Return } as K.Return
        val newClass = returnStatement.expression.expression as J.NewClass
        val arguments = newClass.arguments.asMap()
        val queueUrl = arguments["queueUrl"]!![0] as String
        return queueUrl
    }
}
