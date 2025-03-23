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
package com.yourorg;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import org.apache.jena.ontapi.model.OntIndividual;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.*;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

@Value
@EqualsAndHashCode(callSuper = false)
public class Microscope extends ScanningRecipe<MicroscopeService> {
    @Option(
            displayName = "Relative file path",
            description = "File path of new file.",
            example = "/tmp/model.ttl"
    )
    @NonNull
    String relativeFileName;

    // All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
    @JsonCreator
    public Microscope(@NonNull @JsonProperty("relativeFileName") String relativeFileName) {
        this.relativeFileName = relativeFileName;
    }

    public Microscope() {
        relativeFileName = "model.ttl";
    }

    @Override
    public String getDisplayName() {
        return "Microscope";
    }

    @Override
    public String getDescription() {
        return "Produces a knowledge model describing microservice dependencies.";
    }

    @Override
    public MicroscopeService getInitialValue(ExecutionContext ctx) {
        return new MicroscopeService();
    }

    @Override
    public @NotNull Collection<? extends SourceFile> generate(MicroscopeService acc, ExecutionContext ctx) {
        dumpModel(acc);
        return Collections.emptyList();
    }

    private void dumpModel(MicroscopeService acc) {
        try (FileWriter fileWriter = new FileWriter(relativeFileName)) {
            acc.getModel().write(fileWriter, "TURTLE");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(MicroscopeService accumulator) {

        return new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public K.CompilationUnit visitCompilationUnit(K.CompilationUnit cu, ExecutionContext executionContext) {
                return super.visitCompilationUnit(cu, executionContext);
            }

            @Override
            public K.ClassDeclaration visitClassDeclaration(K.ClassDeclaration classDecl, ExecutionContext ctx) {
                System.out.println("KCD %s".formatted(classDecl.getClassDeclaration().getName().getSimpleName()));
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                System.out.println("JCD %s".formatted(classDecl.getName().getSimpleName()));
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext executionContext) {
                return super.visitVariable(variable, executionContext);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                Cursor cursor = getCursor();
                OntIndividual individualSomething = accumulator.analyzeNewClass(newClass, cursor);
                getCursor().putMessage("something", individualSomething);
                return super.visitNewClass(newClass, executionContext);
            }

            @Override
            public K.MethodDeclaration visitMethodDeclaration(K.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                accumulator.analyzeMethodDeclaration(methodDeclaration.getMethodDeclaration(), this);
                return super.visitMethodDeclaration(methodDeclaration, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                accumulator.analyzeMethodDeclaration(methodDeclaration, this);
                return super.visitMethodDeclaration(methodDeclaration, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
                accumulator.analyzeMethodInvocation(methodInvocation, this);
                return super.visitMethodInvocation(methodInvocation, ctx);
            }
        };
    }

}
