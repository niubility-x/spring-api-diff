package io.github.springapidiff.scanner;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
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
        String name = simpleName(annotation);
        switch (name) {
            case "GetMapping":
                return fixedMethodMappings("GET", annotation);
            case "PostMapping":
                return fixedMethodMappings("POST", annotation);
            case "PutMapping":
                return fixedMethodMappings("PUT", annotation);
            case "PatchMapping":
                return fixedMethodMappings("PATCH", annotation);
            case "DeleteMapping":
                return fixedMethodMappings("DELETE", annotation);
            case "RequestMapping":
                return requestMappings(annotation);
            default:
                return Collections.emptyList();
        }
    }

    public Optional<String> path(AnnotationExpr annotation) {
        List<String> values = paths(annotation);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    public List<String> paths(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr singleMember = (SingleMemberAnnotationExpr) annotation;
            return stringValues(singleMember.getMemberValue());
        }
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            List<String> path = pairValue(normal.getPairs(), "path")
                .map(this::stringValues)
                .orElse(Collections.emptyList());
            if (!path.isEmpty()) {
                return path;
            }
            return pairValue(normal.getPairs(), "value")
                .map(this::stringValues)
                .orElse(Collections.emptyList());
        }
        return Collections.emptyList();
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

    private List<MappingInfo> fixedMethodMappings(String method, AnnotationExpr annotation) {
        List<MappingInfo> mappings = new ArrayList<>();
        for (String path : pathOrRoot(annotation)) {
            mappings.add(new MappingInfo(method, path));
        }
        return mappings;
    }

    private List<MappingInfo> requestMappings(AnnotationExpr annotation) {
        List<String> methods = httpMethods(annotation);
        if (methods.isEmpty()) {
            methods = Collections.singletonList("ANY");
        }
        List<MappingInfo> mappings = new ArrayList<>();
        for (String method : methods) {
            for (String path : pathOrRoot(annotation)) {
                mappings.add(new MappingInfo(method, path));
            }
        }
        return mappings;
    }

    private List<String> pathOrRoot(AnnotationExpr annotation) {
        List<String> values = paths(annotation);
        return values.isEmpty() ? Collections.singletonList("/") : values;
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
