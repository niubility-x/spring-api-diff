package io.github.springapidiff.scanner;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.github.springapidiff.util.TypeNameNormalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

class ControllerContractResolver {
    private final SourceTypeIndex typeIndex;
    private final StringConstantResolver constantResolver;
    private final SpringAnnotationParser annotationParser;
    private final ParameterContractResolver parameterContractResolver;

    ControllerContractResolver(
        SourceTypeIndex typeIndex,
        StringConstantResolver constantResolver,
        SpringAnnotationParser annotationParser) {
        this.typeIndex = typeIndex;
        this.constantResolver = constantResolver;
        this.annotationParser = annotationParser;
        this.parameterContractResolver = new ParameterContractResolver(annotationParser);
    }

    Optional<ControllerContract> resolve(ClassOrInterfaceDeclaration implementation) {
        List<ClassOrInterfaceDeclaration> interfaces = directInterfaces(implementation);
        Optional<List<String>> classPaths = classPaths(implementation, interfaces);
        if (!classPaths.isPresent()) {
            return Optional.empty();
        }
        Map<MethodDeclaration, MethodContract> methods = new IdentityHashMap<>();
        for (MethodDeclaration method : implementation.getMethods()) {
            Optional<List<MethodDeclaration>> interfaceMethods = matchingInterfaceMethods(method, interfaces);
            if (!interfaceMethods.isPresent()) {
                continue;
            }
            Optional<List<SpringAnnotationParser.MappingInfo>> mappings = methodMappings(
                implementation, method, interfaceMethods.get());
            if (!mappings.isPresent() || mappings.get().isEmpty()) {
                continue;
            }
            Optional<List<ParameterContractResolver.ParameterContract>> parameters = parameterContractResolver.resolve(
                method, interfaceMethods.get());
            if (parameters.isPresent()) {
                methods.put(method, new MethodContract(mappings.get(), parameters.get()));
            }
        }
        return Optional.of(new ControllerContract(classPaths.get(), methods));
    }

    private List<ClassOrInterfaceDeclaration> directInterfaces(ClassOrInterfaceDeclaration type) {
        List<ClassOrInterfaceDeclaration> interfaces = new ArrayList<>();
        type.getImplementedTypes().forEach(reference -> typeIndex.resolve(reference, type)
            .filter(ClassOrInterfaceDeclaration::isInterface)
            .ifPresent(interfaces::add));
        return interfaces;
    }

    private Optional<List<String>> classPaths(
        ClassOrInterfaceDeclaration implementation,
        List<ClassOrInterfaceDeclaration> interfaces) {
        Optional<AnnotationExpr> ownMapping = annotationParser.findAnnotation(implementation, "RequestMapping");
        if (ownMapping.isPresent()) {
            return resolvedClassPaths(ownMapping.get(), implementation);
        }
        List<DeclaredAnnotation> inherited = new ArrayList<>();
        for (ClassOrInterfaceDeclaration contract : interfaces) {
            annotationParser.findAnnotation(contract, "RequestMapping")
                .ifPresent(annotation -> inherited.add(new DeclaredAnnotation(annotation, contract)));
        }
        if (inherited.isEmpty()) {
            return Optional.of(Collections.singletonList("/"));
        }
        List<String> selected = null;
        Set<String> selectedSet = null;
        for (DeclaredAnnotation mapping : inherited) {
            Optional<List<String>> paths = resolvedClassPaths(mapping.annotation, mapping.owner);
            if (!paths.isPresent()) {
                return Optional.empty();
            }
            Set<String> current = new TreeSet<>(paths.get());
            if (selectedSet != null && !selectedSet.equals(current)) {
                return Optional.empty();
            }
            selected = paths.get();
            selectedSet = current;
        }
        return Optional.of(selected);
    }

    private Optional<List<String>> resolvedClassPaths(AnnotationExpr annotation, ClassOrInterfaceDeclaration owner) {
        SpringAnnotationParser.PathValues values = annotationParser.pathValues(annotation, owner, constantResolver);
        if (values.status() == SpringAnnotationParser.PathStatus.UNRESOLVED
            || values.status() == SpringAnnotationParser.PathStatus.RESOLVED && values.values().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.status() == SpringAnnotationParser.PathStatus.ABSENT
            ? Collections.singletonList("/")
            : values.values());
    }

    private Optional<List<MethodDeclaration>> matchingInterfaceMethods(
        MethodDeclaration implementation,
        List<ClassOrInterfaceDeclaration> interfaces) {
        String key = methodKey(implementation);
        List<MethodDeclaration> matches = new ArrayList<>();
        for (ClassOrInterfaceDeclaration contract : interfaces) {
            List<MethodDeclaration> candidates = new ArrayList<>();
            for (MethodDeclaration method : contract.getMethods()) {
                if (methodKey(method).equals(key)) {
                    candidates.add(method);
                }
            }
            if (candidates.size() > 1) {
                return Optional.empty();
            }
            if (candidates.size() == 1) {
                matches.add(candidates.get(0));
            }
        }
        return Optional.of(matches);
    }

    private String methodKey(MethodDeclaration method) {
        StringBuilder key = new StringBuilder(method.getNameAsString()).append('(');
        for (Parameter parameter : method.getParameters()) {
            if (key.charAt(key.length() - 1) != '(') {
                key.append(',');
            }
            key.append(TypeNameNormalizer.normalize(parameter.getType()));
        }
        return key.append(')').toString();
    }

    private Optional<List<SpringAnnotationParser.MappingInfo>> methodMappings(
        ClassOrInterfaceDeclaration implementation,
        MethodDeclaration method,
        List<MethodDeclaration> interfaceMethods) {
        List<AnnotationExpr> ownMappings = mappingAnnotations(method);
        if (!ownMappings.isEmpty()) {
            return resolveMappings(ownMappings, implementation);
        }
        List<DeclaredAnnotation> inherited = new ArrayList<>();
        for (MethodDeclaration interfaceMethod : interfaceMethods) {
            ClassOrInterfaceDeclaration owner = declaringType(interfaceMethod);
            if (owner != null) {
                for (AnnotationExpr annotation : mappingAnnotations(interfaceMethod)) {
                    inherited.add(new DeclaredAnnotation(annotation, owner));
                }
            }
        }
        if (inherited.isEmpty()) {
            return Optional.of(Collections.emptyList());
        }
        List<SpringAnnotationParser.MappingInfo> selected = null;
        Set<String> selectedSet = null;
        for (DeclaredAnnotation mapping : inherited) {
            Optional<List<SpringAnnotationParser.MappingInfo>> resolved = resolveMappings(
                Collections.singletonList(mapping.annotation), mapping.owner);
            if (!resolved.isPresent() || resolved.get().isEmpty()) {
                return Optional.empty();
            }
            Set<String> current = mappingKeys(resolved.get());
            if (selectedSet != null && !selectedSet.equals(current)) {
                return Optional.empty();
            }
            selected = resolved.get();
            selectedSet = current;
        }
        return Optional.of(selected);
    }

    private ClassOrInterfaceDeclaration declaringType(MethodDeclaration method) {
        Node current = method.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration) {
                return (ClassOrInterfaceDeclaration) current;
            }
            current = current.getParentNode().orElse(null);
        }
        return null;
    }

    private List<AnnotationExpr> mappingAnnotations(MethodDeclaration method) {
        List<AnnotationExpr> mappings = new ArrayList<>();
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String name = annotation.getName().getIdentifier();
            if ("GetMapping".equals(name)
                || "PostMapping".equals(name)
                || "PutMapping".equals(name)
                || "PatchMapping".equals(name)
                || "DeleteMapping".equals(name)
                || "RequestMapping".equals(name)) {
                mappings.add(annotation);
            }
        }
        return mappings;
    }

    private Optional<List<SpringAnnotationParser.MappingInfo>> resolveMappings(
        List<AnnotationExpr> annotations,
        ClassOrInterfaceDeclaration owner) {
        List<SpringAnnotationParser.MappingInfo> mappings = new ArrayList<>();
        for (AnnotationExpr annotation : annotations) {
            List<SpringAnnotationParser.MappingInfo> resolved = annotationParser.mappings(
                annotation, owner, constantResolver);
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            mappings.addAll(resolved);
        }
        return Optional.of(mappings);
    }

    private Set<String> mappingKeys(List<SpringAnnotationParser.MappingInfo> mappings) {
        Set<String> keys = new TreeSet<>();
        for (SpringAnnotationParser.MappingInfo mapping : mappings) {
            keys.add(mapping.method() + " " + mapping.path());
        }
        return keys;
    }

    static class ControllerContract {
        private final List<String> classPaths;
        private final Map<MethodDeclaration, MethodContract> methods;

        private ControllerContract(List<String> classPaths, Map<MethodDeclaration, MethodContract> methods) {
            this.classPaths = classPaths;
            this.methods = methods;
        }

        List<String> classPaths() {
            return classPaths;
        }

        Optional<MethodContract> method(MethodDeclaration method) {
            return Optional.ofNullable(methods.get(method));
        }
    }

    static class MethodContract {
        private final List<SpringAnnotationParser.MappingInfo> mappings;
        private final List<ParameterContractResolver.ParameterContract> parameters;

        private MethodContract(
            List<SpringAnnotationParser.MappingInfo> mappings,
            List<ParameterContractResolver.ParameterContract> parameters) {
            this.mappings = mappings;
            this.parameters = parameters;
        }

        List<SpringAnnotationParser.MappingInfo> mappings() {
            return mappings;
        }

        List<ParameterContractResolver.ParameterContract> parameters() {
            return parameters;
        }
    }

    private static class DeclaredAnnotation {
        private final AnnotationExpr annotation;
        private final ClassOrInterfaceDeclaration owner;

        private DeclaredAnnotation(AnnotationExpr annotation, ClassOrInterfaceDeclaration owner) {
            this.annotation = annotation;
            this.owner = owner;
        }
    }
}
