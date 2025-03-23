package com.yourorg.classifiers.constructor

import org.apache.jena.ontapi.model.OntIndividual
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import com.yourorg.MicroscopeService.Companion.createABoxURI
import com.yourorg.annotationMap
import com.yourorg.asMap
import com.yourorg.classifiers.method.beanfactory.EventProcessessorConfigurationBFMClassifier.Companion.CLASS_CONFIG
import com.yourorg.getRepository
import org.openrewrite.java.tree.J

class EventProcessorConstructorClassifier(model: OntModel) : AbstractConstructorClassifier(model) {
    override fun addToTBox() {
        //TODO("Not yet implemented")
    }

    override fun classify(
        cursor: Cursor,
        constructor: J.NewClass
    ): OntIndividual? {
        if (constructor.constructorType?.declaringType?.fullyQualifiedName != "com.borrowbox.gearbox.sqs.eventprocessor.processor.EventProcessor") {
            return null
        }
        val configurationIdentifier = extractConfigurationIdentifier(constructor)
        val classDeclaration = cursor.firstEnclosing<J.ClassDeclaration>(J.ClassDeclaration::class.java)
        //TODO: find constructor parameter named configurationIdentifier and extract @Qualifier value
        val constructorAsMethodDeclaration = classDeclaration?.body?.statements?.firstOrNull { it is J.MethodDeclaration && it.methodType?.isConstructor == true } as? J.MethodDeclaration
        if (constructorAsMethodDeclaration == null) {
            return null
        }
        val variableDeclarations =
            constructorAsMethodDeclaration.parameters.firstOrNull { it is J.VariableDeclarations && it.variables.size == 1 && it.variables[0].simpleName == configurationIdentifier } as? J.VariableDeclarations
        if (variableDeclarations == null) {
            return null
        }
        val configurationQualifier = variableDeclarations.leadingAnnotations.annotationMap(cursor)["org.springframework.beans.factory.annotation.Qualifier"]?.get("value")?.firstOrNull() as? String

        val repository = cursor.getRepository()
        if (repository == null) {
            return null
        }
        val classConfig = model.getOntClass(CLASS_CONFIG)
        val configUri = configurationQualifier?.let { createABoxURI(classConfig, repository, it) }
        return model.createIndividual(configUri, classConfig)
    }

    private fun extractConfigurationIdentifier(constructor: J.NewClass): String {
        val arguments = constructor.arguments.asMap()
        return arguments["configuration"]!![0] as String
    }
}
