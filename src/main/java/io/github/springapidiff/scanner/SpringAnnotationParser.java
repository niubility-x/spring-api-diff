package io.github.springapidiff.scanner;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SpringAnnotationParser {
    public Optional<AnnotationExpr> findAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream()
            .filter(annotation -> simpleName(annotation).equals(simpleName))
            .findFirst();
    }

    public boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return findAnnotation(node, simpleName).isPresent();
    }

    public boolean hasAnyAnnotation(NodeWithAnnotations<?> node, String... simpleNames) {
        for (String simpleName : simpleNames) {
            if (hasAnnotation(node, simpleName)) {
                return true;
            }
        }
        return false;
    }

    public Optional<MappingInfo> mapping(AnnotationExpr annotation) {
        List<MappingInfo> mappings = mappings(annotation);
        return mappings.isEmpty() ? Optional.empty() : Optional.of(mappings.get(0));
    }

    public List<MappingInfo> mappings(AnnotationExpr annotation) {
        return mappings(annotation, null, null);
    }

    public List<MappingInfo> mappings(
        AnnotationExpr annotation,
        ClassOrInterfaceDeclaration owner,
        StringConstantResolver constantResolver) {
        String name = simpleName(annotation);
        switch (name) {
            case "GetMapping":
                return fixedMethodMappings("GET", annotation, owner, constantResolver);
            case "PostMapping":
                return fixedMethodMappings("POST", annotation, owner, constantResolver);
            case "PutMapping":
                return fixedMethodMappings("PUT", annotation, owner, constantResolver);
            case "PatchMapping":
                return fixedMethodMappings("PATCH", annotation, owner, constantResolver);
            case "DeleteMapping":
                return fixedMethodMappings("DELETE", annotation, owner, constantResolver);
            case "RequestMapping":
                return requestMappings(annotation, owner, constantResolver);
            default:
                return Collections.emptyList();
        }
    }

    public Optional<String> path(AnnotationExpr annotation) {
        List<String> values = paths(annotation);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<String> paths(AnnotationExpr annotation) {
        return pathValues(annotation, null, null).values();
    }

    PathValues pathValues(
        AnnotationExpr annotation,
        ClassOrInterfaceDeclaration owner,
        StringConstantResolver constantResolver) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr singleMember = (SingleMemberAnnotationExpr) annotation;
            return resolvePathExpression(singleMember.getMemberValue(), owner, constantResolver);
        }
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            Optional<Expression> path = pairValue(normal.getPairs(), "path");
            if (path.isPresent()) {
                return resolvePathExpression(path.get(), owner, constantResolver);
            }
            Optional<Expression> value = pairValue(normal.getPairs(), "value");
            if (value.isPresent()) {
                return resolvePathExpression(value.get(), owner, constantResolver);
            }
        }
        return PathValues.absent();
    }

    public Optional<String> namedValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr singleMember = (SingleMemberAnnotationExpr) annotation;
            return stringValue(singleMember.getMemberValue());
        }
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            Optional<String> name = pairValue(normal.getPairs(), "name").flatMap(this::stringValue);
            if (name.isPresent()) {
                return name;
            }
            return pairValue(normal.getPairs(), "value").flatMap(this::stringValue);
        }
        return Optional.empty();
    }

    public boolean required(Parameter parameter, String annotationName, boolean defaultValue) {
        Optional<AnnotationExpr> annotation = findAnnotation(parameter, annotationName);
        if (!annotation.isPresent()) {
            return defaultValue;
        }
        if ("RequestParam".equals(annotationName) && hasPair(annotation.get(), "defaultValue")) {
            return false;
        }
        return required(annotation.get()).orElse(defaultValue);
    }

    public Optional<Boolean> required(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            return pairValue(normal.getPairs(), "required").flatMap(this::booleanValue);
        }
        return Optional.empty();
    }

    public boolean hasPair(AnnotationExpr annotation, String name) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            return pairValue(normal.getPairs(), name).isPresent();
        }
        return false;
    }

    private List<MappingInfo> fixedMethodMappings(
        String method,
        AnnotationExpr annotation,
        ClassOrInterfaceDeclaration owner,
        StringConstantResolver constantResolver) {
        List<MappingInfo> mappings = new ArrayList<>();
        for (String path : pathOrRoot(annotation, owner, constantResolver)) {
            mappings.add(new MappingInfo(method, path));
        }
        return mappings;
    }

    private List<MappingInfo> requestMappings(
        AnnotationExpr annotation,
        ClassOrInterfaceDeclaration owner,
        StringConstantResolver constantResolver) {
        List<String> methods = httpMethods(annotation);
        if (methods.isEmpty()) {
            methods = Collections.singletonList("ANY");
        }
        List<MappingInfo> mappings = new ArrayList<>();
        for (String method : methods) {
            for (String path : pathOrRoot(annotation, owner, constantResolver)) {
                mappings.add(new MappingInfo(method, path));
            }
        }
        return mappings;
    }

    private List<String> pathOrRoot(
        AnnotationExpr annotation,
        ClassOrInterfaceDeclaration owner,
        StringConstantResolver constantResolver) {
        PathValues values = pathValues(annotation, owner, constantResolver);
        return values.status() == PathStatus.ABSENT ? Collections.singletonList("/") : values.values();
    }

    private PathValues resolvePathExpression(
        Expression expression,
        ClassOrInterfaceDeclaration owner,
        StringConstantResolver constantResolver) {
        if (expression instanceof ArrayInitializerExpr) {
            ArrayInitializerExpr array = (ArrayInitializerExpr) expression;
            if (array.getValues().isEmpty()) {
                return PathValues.resolved(Collections.emptyList());
            }
            List<String> values = new ArrayList<>();
            for (Expression value : array.getValues()) {
                Optional<String> resolved = resolvePathValue(value, owner, constantResolver);
                if (!resolved.isPresent()) {
                    return PathValues.unresolved();
                }
                values.add(resolved.get());
            }
            return PathValues.resolved(values);
        }
        Optional<String> value = resolvePathValue(expression, owner, constantResolver);
        return value.isPresent()
            ? PathValues.resolved(Collections.singletonList(value.get()))
            : PathValues.unresolved();
    }

    private Optional<String> resolvePathValue(
        Expression expression,
        ClassOrInterfaceDeclaration owner,
        StringConstantResolver constantResolver) {
        if (constantResolver != null && owner != null) {
            return constantResolver.resolve(expression, owner);
        }
        if (expression instanceof StringLiteralExpr) {
            return Optional.of(((StringLiteralExpr) expression).asString());
        }
        if (expression instanceof EnclosedExpr) {
            return resolvePathValue(((EnclosedExpr) expression).getInner(), owner, constantResolver);
        }
        if (expression instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expression;
            if (binary.getOperator() != BinaryExpr.Operator.PLUS) {
                return Optional.empty();
            }
            Optional<String> left = resolvePathValue(binary.getLeft(), owner, constantResolver);
            Optional<String> right = resolvePathValue(binary.getRight(), owner, constantResolver);
            return left.isPresent() && right.isPresent()
                ? Optional.of(left.get() + right.get())
                : Optional.empty();
        }
        return Optional.empty();
    }

    private List<String> httpMethods(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            return pairValue(normal.getPairs(), "method")
                .map(this::enumValues)
                .orElse(Collections.emptyList());
        }
        return Collections.emptyList();
    }

    private Optional<Expression> pairValue(NodeList<MemberValuePair> pairs, String name) {
        return pairs.stream()
            .filter(pair -> pair.getNameAsString().equals(name))
            .map(MemberValuePair::getValue)
            .findFirst();
    }

    private Optional<String> stringValue(Expression expression) {
        List<String> values = stringValues(expression);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private List<String> stringValues(Expression expression) {
        if (expression instanceof StringLiteralExpr) {
            StringLiteralExpr literal = (StringLiteralExpr) expression;
            return Collections.singletonList(literal.asString());
        }
        if (expression instanceof ArrayInitializerExpr) {
            ArrayInitializerExpr array = (ArrayInitializerExpr) expression;
            List<String> values = new ArrayList<>();
            for (Expression value : array.getValues()) {
                values.addAll(stringValues(value));
            }
            return values;
        }
        return Collections.emptyList();
    }

    private List<String> enumValues(Expression expression) {
        if (expression instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) expression;
            return Collections.singletonList(fieldAccess.getNameAsString().toUpperCase(Locale.ROOT));
        }
        if (expression instanceof NameExpr) {
            NameExpr name = (NameExpr) expression;
            return Collections.singletonList(name.getNameAsString().toUpperCase(Locale.ROOT));
        }
        if (expression instanceof ArrayInitializerExpr) {
            ArrayInitializerExpr array = (ArrayInitializerExpr) expression;
            List<String> values = new ArrayList<>();
            for (Expression value : array.getValues()) {
                values.addAll(enumValues(value));
            }
            return values;
        }
        return Collections.emptyList();
    }

    private Optional<Boolean> booleanValue(Expression expression) {
        if (expression instanceof BooleanLiteralExpr) {
            BooleanLiteralExpr literal = (BooleanLiteralExpr) expression;
            return Optional.of(literal.getValue());
        }
        return Optional.empty();
    }

    private String simpleName(AnnotationExpr annotation) {
        return annotation.getName().getIdentifier();
    }

    enum PathStatus {
        ABSENT,
        RESOLVED,
        UNRESOLVED
    }

    static class PathValues {
        private final PathStatus status;
        private final List<String> values;

        private PathValues(PathStatus status, List<String> values) {
            this.status = status;
            this.values = values;
        }

        private static PathValues absent() {
            return new PathValues(PathStatus.ABSENT, Collections.emptyList());
        }

        private static PathValues resolved(List<String> values) {
            return new PathValues(PathStatus.RESOLVED, values);
        }

        private static PathValues unresolved() {
            return new PathValues(PathStatus.UNRESOLVED, Collections.emptyList());
        }

        PathStatus status() {
            return status;
        }

        List<String> values() {
            return values;
        }
    }

    public static class MappingInfo {
        private final String method;
        private final String path;

        public MappingInfo(String method, String path) {
            this.method = method;
            this.path = path;
        }

        public String method() {
            return method;
        }

        public String path() {
            return path;
        }
    }
}
