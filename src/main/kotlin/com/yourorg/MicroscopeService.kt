package com.yourorg

import com.yourorg.MicroscopeService.Companion.CLASS_METHOD
import com.yourorg.MicroscopeService.Companion.PROPERTY_HAS_FQCN
import com.yourorg.MicroscopeService.Companion.PROPERTY_HAS_METHOD_NAME
import com.yourorg.MicroscopeService.Companion.createABoxURI
import com.yourorg.classifiers.constructor.EventProcessorConstructorClassifier
import com.yourorg.classifiers.method.EndpointMethodClassifier
import com.yourorg.classifiers.method.RetrofitClientMethodClassifier
import com.yourorg.classifiers.method.beanfactory.EventProcessessorConfigurationBFMClassifier
import org.apache.jena.iri.IRIFactory
import org.apache.jena.ontapi.OntModelFactory
import org.apache.jena.ontapi.model.OntClass
import org.apache.jena.ontapi.model.OntIndividual
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaType
import org.openrewrite.kotlin.tree.K
import java.io.File
import kotlin.io.path.name

class MicroscopeService {
    val model: OntModel = MicroscopeService.createModel()
    val methodClassifiers =
        listOf(
            EndpointMethodClassifier(model),
            RetrofitClientMethodClassifier(model),
            EventProcessessorConfigurationBFMClassifier(model),
        )
    val constructorClassifiers =
        listOf(
            EventProcessorConstructorClassifier(model),
        )

    companion object {
        val NS_TBOX = "http://yourorg.com/ontology/"
        val NS_ABOX = "http://yourorg.com/data/"
        val CLASS_METHOD = "${NS_TBOX}Method"
        val PROPERTY_CALLS = "${NS_TBOX}calls"
        val PROPERTY_HAS_METHOD_NAME = "${NS_TBOX}hasMethodName"
        val PROPERTY_HAS_FQCN = "${NS_TBOX}hasFullyQualifiedClassName"

        val iriFactory = IRIFactory.iriImplementation()

        fun createModel(): OntModel {
            val model =
                OntModelFactory.createModel().apply {
                    createOntClass(CLASS_METHOD).apply {
                        createObjectProperty(PROPERTY_CALLS).also {
                            it.addDomain(this)
                            it.addRange(this)
                        }
                        createDataProperty(PROPERTY_HAS_METHOD_NAME).also { it.addDomain(this) }
                        createDataProperty(PROPERTY_HAS_FQCN).also { it.addDomain(this) }
                    }
                }
            return model
        }

        fun createABoxURI(ontClass: OntClass, vararg elements: String): String {
            val localClassName = ontClass.localName
            val uuid = Namespace.URL.uuid3(elements.joinToString("#"))
            return "$NS_ABOX$localClassName#$uuid"
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

    fun analyzeMethodDeclaration(
        methodDeclaration: J.MethodDeclaration,
        visitor: JavaVisitor<ExecutionContext>,
    ) {
        val methodType = methodDeclaration.methodType
        if (methodType != null) {
            classifyMethod(methodDeclaration, visitor)
        }
    }

    fun analyzeMethodInvocation(
        methodInvocation: J.MethodInvocation,
        visitor: JavaVisitor<ExecutionContext>,
    ) {
        val cursor = visitor.cursor
        val callerMethodDeclaration = cursor.firstEnclosing(J.MethodDeclaration::class.java)
        if (callerMethodDeclaration == null) {
            return
        }
        val callerMethodType = callerMethodDeclaration.methodType
        if (callerMethodType == null) {
            return
        }
        val individualCaller = model.createIndividualMethod(callerMethodType)

        val declaringTypeName: String? =
            methodInvocation.methodType?.declaringType?.fullyQualifiedName
        val relevantReceiverType = declaringTypeName?.startsWith("kotlin.") == false
        if (methodInvocation.methodType != null && relevantReceiverType && declaringTypeName != null) {
            val invokedMethodName = methodInvocation.name.toString()
            val classMethod = model.getOntClass(CLASS_METHOD)
            val individualCallee =
                model.createIndividual(
                    createABoxURI(classMethod,declaringTypeName, invokedMethodName),
                    classMethod,
                )
            individualCaller.addProperty(
                model.getObjectProperty(PROPERTY_CALLS),
                individualCallee,
            )
        }
    }

    private fun classifyMethod(
        jMethodDeclaration: J.MethodDeclaration,
        visitor: JavaVisitor<ExecutionContext>,
    ): Boolean {
        return methodClassifiers.any { it.classify(jMethodDeclaration, visitor) }
    }

    fun analyzeNewClass(newClass: J.NewClass, cursor: Cursor): OntIndividual? {
        val constructorMethod = newClass.constructorType
        if (constructorMethod == null || !constructorMethod.isConstructor) {
            return null
        }
        return constructorClassifiers.map {
            it.classify(cursor, newClass)
        }
            .firstOrNull()
    }
}

fun Cursor.getRepository(): String? {
    val sourcePath = this.firstEnclosing(K.CompilationUnit::class.java)?.sourcePath ?: this.firstEnclosing(J.CompilationUnit::class.java)?.sourcePath
    if (sourcePath == null) {
        return null
    }
    return sourcePath.takeUnless { it.name == "src" }?.parent.toString()
}

fun OntModel.createIndividualMethod(methodType: JavaType.Method): OntIndividual {
    val methodName = methodType.name
    val typeName = methodType.declaringType.getFullyQualifiedName()
    val classMethod = getOntClass(CLASS_METHOD)
    val methodUri = createABoxURI(classMethod, typeName, methodName)
    val individualMethod = createIndividual(methodUri, classMethod).apply {
        if ("<constructorY" != methodName) {
            addProperty(
                getDataProperty(PROPERTY_HAS_METHOD_NAME),
                methodName,
            )
        }
        addProperty(
            getDataProperty(PROPERTY_HAS_FQCN),
            methodType.declaringType.fullyQualifiedName,
        )
    }
    return individualMethod
}
