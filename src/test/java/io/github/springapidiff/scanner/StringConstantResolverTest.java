package io.github.springapidiff.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class StringConstantResolverTest {
    @Test
    void resolvesLocalConstantsParenthesesAndConcatenation() {
        CompilationUnit unit = StaticJavaParser.parse(
            "package test; class Controller {"
                + " static final String BASE = \"/api\";"
                + " static final String USERS = BASE + \"/users\";"
                + " }");
        ClassOrInterfaceDeclaration owner = unit.getClassByName("Controller").get();
        StringConstantResolver resolver = new StringConstantResolver(Collections.singletonList(unit));

        assertThat(resolver.resolve(expression("(USERS + \"/{id}\")"), owner))
            .contains("/api/users/{id}");
    }

    @Test
    void resolvesImportedCrossClassAndStaticConstants() {
        CompilationUnit paths = StaticJavaParser.parse(
            "package shared; class ApiPaths {"
                + " static final String BASE = \"/api\";"
                + " static final String USERS = BASE + \"/users\";"
                + " }");
        CompilationUnit controller = StaticJavaParser.parse(
            "package app; import shared.ApiPaths; import static shared.ApiPaths.USERS;"
                + " class Controller {}");
        ClassOrInterfaceDeclaration owner = controller.getClassByName("Controller").get();
        StringConstantResolver resolver = new StringConstantResolver(Arrays.asList(paths, controller));

        assertThat(resolver.resolve(expression("ApiPaths.BASE + USERS"), owner))
            .contains("/api/api/users");
        assertThat(resolver.resolve(expression("shared.ApiPaths.USERS"), owner))
            .contains("/api/users");
    }

    @Test
    void rejectsUnsupportedAndNonConstantExpressions() {
        CompilationUnit unit = StaticJavaParser.parse(
            "class Controller {"
                + " static String MUTABLE = \"/mutable\";"
                + " static final int VERSION = 1;"
                + " }");
        ClassOrInterfaceDeclaration owner = unit.getClassByName("Controller").get();
        StringConstantResolver resolver = new StringConstantResolver(Collections.singletonList(unit));

        assertThat(resolver.resolve(expression("MUTABLE"), owner)).isEmpty();
        assertThat(resolver.resolve(expression("VERSION + \"/users\""), owner)).isEmpty();
        assertThat(resolver.resolve(expression("path()"), owner)).isEmpty();
    }

    @Test
    void detectsConstantCycles() {
        CompilationUnit unit = StaticJavaParser.parse(
            "class Controller {"
                + " static final String A = B;"
                + " static final String B = A;"
                + " }");
        ClassOrInterfaceDeclaration owner = unit.getClassByName("Controller").get();
        StringConstantResolver resolver = new StringConstantResolver(Collections.singletonList(unit));

        assertThat(resolver.resolve(expression("A"), owner)).isEmpty();
    }

    private Expression expression(String source) {
        return StaticJavaParser.parseExpression(source);
    }
}
