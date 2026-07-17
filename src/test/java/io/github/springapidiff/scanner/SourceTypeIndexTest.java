package io.github.springapidiff.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SourceTypeIndexTest {
    @Test
    void resolvesQualifiedImportedAndSamePackageTypes() {
        CompilationUnit contract = StaticJavaParser.parse("package contracts; interface ApiContract {}");
        CompilationUnit imported = StaticJavaParser.parse(
            "package app; import contracts.ApiContract; class Imported implements ApiContract {}");
        CompilationUnit localContract = StaticJavaParser.parse("package local; interface LocalContract {}");
        CompilationUnit local = StaticJavaParser.parse("package local; class Local implements LocalContract {}");
        SourceTypeIndex index = new SourceTypeIndex(Arrays.asList(contract, imported, localContract, local));
        ClassOrInterfaceDeclaration importedType = imported.getClassByName("Imported").get();
        ClassOrInterfaceDeclaration localType = local.getClassByName("Local").get();

        assertThat(index.resolve("contracts.ApiContract", importedType)).contains(contract.getInterfaceByName("ApiContract").get());
        assertThat(index.resolve("ApiContract", importedType)).contains(contract.getInterfaceByName("ApiContract").get());
        assertThat(index.resolve("LocalContract", localType)).contains(localContract.getInterfaceByName("LocalContract").get());
    }

    @Test
    void resolvesOnlyGloballyUniqueSimpleNames() {
        CompilationUnit first = StaticJavaParser.parse("package one; interface Unique {}");
        CompilationUnit context = StaticJavaParser.parse("package app; class Controller {}");
        SourceTypeIndex index = new SourceTypeIndex(Arrays.asList(first, context));

        assertThat(index.resolve("Unique", context.getClassByName("Controller").get()))
            .contains(first.getInterfaceByName("Unique").get());
    }

    @Test
    void rejectsAmbiguousSimpleNames() {
        CompilationUnit first = StaticJavaParser.parse("package one; interface Contract {}");
        CompilationUnit second = StaticJavaParser.parse("package two; interface Contract {}");
        CompilationUnit context = StaticJavaParser.parse("package app; class Controller {}");
        SourceTypeIndex index = new SourceTypeIndex(Arrays.asList(first, second, context));

        assertThat(index.resolve("Contract", context.getClassByName("Controller").get())).isEmpty();
    }

    @Test
    void indexesNestedTypesByRealQualifiedName() {
        CompilationUnit unit = StaticJavaParser.parse("package app; class Outer { interface Contract {} }");
        SourceTypeIndex index = new SourceTypeIndex(Arrays.asList(unit));
        ClassOrInterfaceDeclaration nested = unit.findAll(ClassOrInterfaceDeclaration.class).stream()
            .filter(type -> type.getNameAsString().equals("Contract"))
            .findFirst()
            .get();

        assertThat(index.qualifiedName(nested)).contains("app.Outer.Contract");
    }
}
