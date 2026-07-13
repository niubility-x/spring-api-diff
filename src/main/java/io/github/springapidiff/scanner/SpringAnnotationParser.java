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

    public Optional<MappingInfo> mapping(AnnotationExpr annotation) {
        String name = simpleName(annotation);
        switch (name) {
            case "GetMapping":
                return Optional.of(new MappingInfo("GET", path(annotation).orElse("/")));
            case "PostMapping":
                return Optional.of(new MappingInfo("POST", path(annotation).orElse("/")));
            case "PutMapping":
                return Optional.of(new MappingInfo("PUT", path(annotation).orElse("/")));
            case "PatchMapping":
                return Optional.of(new MappingInfo("PATCH", path(annotation).orElse("/")));
            case "DeleteMapping":
                return Optional.of(new MappingInfo("DELETE", path(annotation).orElse("/")));
            case "RequestMapping":
                return Optional.of(new MappingInfo(httpMethod(annotation).orElse("GET"), path(annotation).orElse("/")));
            default:
                return Optional.empty();
        }
    }

    public Optional<String> path(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr singleMember = (SingleMemberAnnotationExpr) annotation;
            return stringValue(singleMember.getMemberValue());
        }
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            Optional<String> path = pairValue(normal.getPairs(), "path").flatMap(this::stringValue);
            if (path.isPresent()) {
                return path;
            }
            return pairValue(normal.getPairs(), "value").flatMap(this::stringValue);
        }
        return Optional.empty();
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
        return findAnnotation(parameter, annotationName)
            .flatMap(this::required)
            .orElse(defaultValue);
    }

    public Optional<Boolean> required(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            return pairValue(normal.getPairs(), "required").flatMap(this::booleanValue);
        }
        return Optional.empty();
    }

    private Optional<String> httpMethod(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normal = (NormalAnnotationExpr) annotation;
            return pairValue(normal.getPairs(), "method")
                .flatMap(this::enumValue)
                .map(value -> value.toUpperCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    private Optional<Expression> pairValue(NodeList<MemberValuePair> pairs, String name) {
        return pairs.stream()
            .filter(pair -> pair.getNameAsString().equals(name))
            .map(MemberValuePair::getValue)
            .findFirst();
    }

    private Optional<String> stringValue(Expression expression) {
        if (expression instanceof StringLiteralExpr) {
            StringLiteralExpr literal = (StringLiteralExpr) expression;
            return Optional.of(literal.asString());
        }
        if (expression instanceof ArrayInitializerExpr) {
            ArrayInitializerExpr array = (ArrayInitializerExpr) expression;
            return array.getValues().stream().findFirst().flatMap(this::stringValue);
        }
        return Optional.empty();
    }

    private Optional<String> enumValue(Expression expression) {
        if (expression instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) expression;
            return Optional.of(fieldAccess.getNameAsString());
        }
        if (expression instanceof NameExpr) {
            NameExpr name = (NameExpr) expression;
            return Optional.of(name.getNameAsString());
        }
        if (expression instanceof ArrayInitializerExpr) {
            ArrayInitializerExpr array = (ArrayInitializerExpr) expression;
            return array.getValues().stream().findFirst().flatMap(this::enumValue);
        }
        return Optional.empty();
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
