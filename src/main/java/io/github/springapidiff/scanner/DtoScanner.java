package io.github.springapidiff.scanner;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.github.springapidiff.model.ApiField;
import io.github.springapidiff.util.TypeNameNormalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DtoScanner {
    private final Map<String, ClassOrInterfaceDeclaration> classes;
    private final SpringAnnotationParser annotationParser = new SpringAnnotationParser();

    public DtoScanner(Map<String, ClassOrInterfaceDeclaration> classes) {
        this.classes = classes;
    }

    public List<ApiField> fieldsFor(String typeName) {
        ClassOrInterfaceDeclaration type = findType(typeName);
        if (type == null) {
            return Collections.emptyList();
        }
        return fieldsFor(type, new HashSet<String>());
    }

    private List<ApiField> fieldsFor(ClassOrInterfaceDeclaration type, Set<String> visited) {
        String typeName = type.getFullyQualifiedName().orElse(type.getNameAsString());
        if (!visited.add(typeName)) {
            return Collections.emptyList();
        }

        List<ApiField> fields = new ArrayList<>();
        type.getExtendedTypes().forEach(parent -> {
            ClassOrInterfaceDeclaration parentType = findType(parent.getNameAsString());
            if (parentType != null) {
                fields.addAll(fieldsFor(parentType, visited));
            }
        });
        type.getFields().stream()
            .filter(this::isApiField)
            .flatMap(field -> field.getVariables().stream().map(variable -> apiField(field, variable)))
            .forEach(fields::add);
        return fields;
    }

    private ClassOrInterfaceDeclaration findType(String typeName) {
        String lookupName = TypeNameNormalizer.dtoLookupName(typeName);
        ClassOrInterfaceDeclaration type = classes.get(lookupName);
        if (type == null) {
            type = classes.get(TypeNameNormalizer.rawSimpleName(lookupName));
        }
        return type;
    }

    private boolean isApiField(FieldDeclaration field) {
        return !field.isStatic()
            && !field.isTransient()
            && !annotationParser.hasAnnotation(field, "JsonIgnore");
    }

    private ApiField apiField(FieldDeclaration field, VariableDeclarator variable) {
        String name = annotationParser.findAnnotation(field, "JsonProperty")
            .flatMap(this::jsonPropertyName)
            .orElse(variable.getNameAsString());
        return new ApiField(name, TypeNameNormalizer.normalize(variable.getType()), required(field));
    }

    private boolean required(FieldDeclaration field) {
        return annotationParser.hasAnyAnnotation(
            field,
            "NotNull",
            "NotBlank",
            "NotEmpty",
            "Positive",
            "PositiveOrZero",
            "Negative",
            "NegativeOrZero")
            || annotationParser.findAnnotation(field, "JsonProperty")
                .flatMap(annotationParser::required)
                .orElse(false);
    }

    private Optional<String> jsonPropertyName(AnnotationExpr annotation) {
        return annotationParser.namedValue(annotation)
            .filter(value -> !value.trim().isEmpty());
    }
}
