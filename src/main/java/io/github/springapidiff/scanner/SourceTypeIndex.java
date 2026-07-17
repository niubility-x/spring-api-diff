package io.github.springapidiff.scanner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class SourceTypeIndex {
    private final Map<String, List<ClassOrInterfaceDeclaration>> byQualifiedName = new LinkedHashMap<>();
    private final Map<String, Set<String>> qualifiedNamesBySimpleName = new HashMap<>();
    private final Map<ClassOrInterfaceDeclaration, TypeContext> contexts = new IdentityHashMap<>();

    SourceTypeIndex(List<CompilationUnit> units) {
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                String qualifiedName = qualifiedName(unit, type);
                TypeContext context = new TypeContext(packageName(unit), explicitImports(unit), qualifiedName);
                contexts.put(type, context);
                byQualifiedName.computeIfAbsent(qualifiedName, ignored -> new ArrayList<>()).add(type);
                qualifiedNamesBySimpleName
                    .computeIfAbsent(type.getNameAsString(), ignored -> new LinkedHashSet<>())
                    .add(qualifiedName);
            }
        }
    }

    Optional<ClassOrInterfaceDeclaration> resolve(ClassOrInterfaceType reference, ClassOrInterfaceDeclaration context) {
        return resolve(reference.getNameWithScope(), context);
    }

    Optional<ClassOrInterfaceDeclaration> resolve(String reference, ClassOrInterfaceDeclaration context) {
        TypeContext sourceContext = contexts.get(context);
        if (sourceContext == null || reference == null || reference.trim().isEmpty()) {
            return Optional.empty();
        }
        String name = reference.trim();
        if (name.contains(".")) {
            Optional<ClassOrInterfaceDeclaration> exact = unique(name);
            if (exact.isPresent() || byQualifiedName.containsKey(name)) {
                return exact;
            }
        }
        String imported = sourceContext.imports().get(name);
        if (imported != null) {
            return unique(imported);
        }
        String samePackage = sourceContext.packageName().isEmpty()
            ? name
            : sourceContext.packageName() + "." + name;
        Optional<ClassOrInterfaceDeclaration> local = unique(samePackage);
        if (local.isPresent() || byQualifiedName.containsKey(samePackage)) {
            return local;
        }
        Set<String> candidates = qualifiedNamesBySimpleName.get(name);
        if (candidates == null || candidates.size() != 1) {
            return Optional.empty();
        }
        return unique(candidates.iterator().next());
    }

    Optional<String> qualifiedName(ClassOrInterfaceDeclaration type) {
        TypeContext context = contexts.get(type);
        return context == null ? Optional.empty() : Optional.of(context.qualifiedName());
    }

    private Optional<ClassOrInterfaceDeclaration> unique(String qualifiedName) {
        List<ClassOrInterfaceDeclaration> values = byQualifiedName.get(qualifiedName);
        return values != null && values.size() == 1 ? Optional.of(values.get(0)) : Optional.empty();
    }

    private String qualifiedName(CompilationUnit unit, ClassOrInterfaceDeclaration type) {
        List<String> names = new ArrayList<>();
        Node current = type;
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration) {
                names.add(0, ((ClassOrInterfaceDeclaration) current).getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        String localName = String.join(".", names);
        String packageName = packageName(unit);
        return packageName.isEmpty() ? localName : packageName + "." + localName;
    }

    private String packageName(CompilationUnit unit) {
        return unit.getPackageDeclaration()
            .map(declaration -> declaration.getNameAsString())
            .orElse("");
    }

    private Map<String, String> explicitImports(CompilationUnit unit) {
        Map<String, String> imports = new HashMap<>();
        for (ImportDeclaration declaration : unit.getImports()) {
            if (declaration.isStatic() || declaration.isAsterisk()) {
                continue;
            }
            String qualifiedName = declaration.getNameAsString();
            int separator = qualifiedName.lastIndexOf('.');
            if (separator >= 0) {
                imports.put(qualifiedName.substring(separator + 1), qualifiedName);
            }
        }
        return imports;
    }

    private static class TypeContext {
        private final String packageName;
        private final Map<String, String> imports;
        private final String qualifiedName;

        private TypeContext(String packageName, Map<String, String> imports, String qualifiedName) {
            this.packageName = packageName;
            this.imports = Collections.unmodifiableMap(new HashMap<>(imports));
            this.qualifiedName = qualifiedName;
        }

        private String packageName() {
            return packageName;
        }

        private Map<String, String> imports() {
            return imports;
        }

        private String qualifiedName() {
            return qualifiedName;
        }
    }
}
