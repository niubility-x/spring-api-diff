package io.github.springapidiff.scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.model.Endpoint;
import io.github.springapidiff.model.ProjectInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ProjectScanner {
    public ApiSnapshot scan(Path projectPath, List<String> includes, List<String> excludes) throws IOException {
        Path sourceRoot = projectPath.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("Java source root not found: " + sourceRoot);
        }

        List<CompilationUnit> units = parseUnits(sourceRoot);
        Map<String, ClassOrInterfaceDeclaration> classes = indexClasses(units);
        DtoScanner dtoScanner = new DtoScanner(classes);
        ControllerScanner controllerScanner = new ControllerScanner(dtoScanner);
        List<Endpoint> endpoints = units.stream()
            .flatMap(unit -> controllerScanner.scan(unit).stream())
            .filter(endpoint -> included(endpoint.controller(), includes, excludes))
            .sorted(Comparator.comparing(Endpoint::id))
            .collect(java.util.stream.Collectors.toList());

        return new ApiSnapshot(
            "1",
            Instant.now(),
            new ProjectInfo(projectPath.toAbsolutePath().getFileName().toString(), "unknown", "unknown"),
            endpoints);
    }

    private List<CompilationUnit> parseUnits(Path sourceRoot) throws IOException {
        List<CompilationUnit> units = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                .filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .collect(java.util.stream.Collectors.toList());
            for (Path javaFile : javaFiles) {
                units.add(StaticJavaParser.parse(javaFile));
            }
        }
        return units;
    }

    private Map<String, ClassOrInterfaceDeclaration> indexClasses(List<CompilationUnit> units) {
        Map<String, ClassOrInterfaceDeclaration> classes = new HashMap<>();
        for (CompilationUnit unit : units) {
            unit.findAll(ClassOrInterfaceDeclaration.class).forEach(type -> {
                classes.put(type.getNameAsString(), type);
                unit.getPackageDeclaration().ifPresent(packageDeclaration ->
                    classes.put(packageDeclaration.getNameAsString() + "." + type.getNameAsString(), type));
            });
        }
        return classes;
    }

    private boolean included(String controller, List<String> includes, List<String> excludes) {
        boolean includeMatch = includes == null || includes.isEmpty()
            || includes.stream().anyMatch(controller::startsWith);
        boolean excludeMatch = excludes != null && excludes.stream().anyMatch(controller::startsWith);
        return includeMatch && !excludeMatch;
    }
}
