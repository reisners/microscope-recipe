package com.yourorg.classifiers.method.beanfactory

import com.yourorg.classifiers.method.AbstractMethodClassifier
import org.apache.jena.ontapi.model.OntModel
import org.openrewrite.Cursor
import org.openrewrite.ExecutionContext
import org.openrewrite.java.AnnotationMatcher
import org.openrewrite.java.JavaVisitor
import org.openrewrite.java.service.AnnotationService
import org.openrewrite.java.tree.J

abstract class AbstractBeanFactoryMethodClassifier(model: OntModel) : AbstractMethodClassifier(model) {
    val BEAN = "org.springframework.context.annotation.Bean";
    val BEAN_ANNOTATION_MATCHER = AnnotationMatcher("@" + BEAN);

    override fun classify(
        jMethodDeclaration: J.MethodDeclaration,
        visitor: JavaVisitor<ExecutionContext>
    ): Boolean {
        val cursor = visitor.cursor
        return if (visitor.service(AnnotationService::class.java).matches(cursor, BEAN_ANNOTATION_MATCHER)) {
            // it's a bean factory method
            doClassify(cursor, jMethodDeclaration)
        } else {
            false
        }
    }

    abstract fun doClassify(cursor: Cursor, jMethodDeclaration: J.MethodDeclaration): Boolean

}
