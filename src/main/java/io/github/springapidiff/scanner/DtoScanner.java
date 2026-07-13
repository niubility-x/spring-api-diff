package io.github.springapidiff.scanner;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.github.springapidiff.model.ApiField;
import io.github.springapidiff.util.TypeNameNormalizer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DtoScanner {
    private final Map<String, ClassOrInterfaceDeclaration> classes;
    private final SpringAnnotationParser annotationParser = new SpringAnnotationParser();

    public DtoScanner(Map<String, ClassOrInterfaceDeclaration> classes) {
        this.classes = classes;
    }

    public List<ApiField> fieldsFor(String typeName) {
        String lookupName = TypeNameNormalizer.dtoLookupName(typeName);
        ClassOrInterfaceDeclaration type = classes.get(lookupName);
        if (type == null) {
            type = classes.get(TypeNameNormalizer.rawSimpleName(lookupName));
        }
        if (type == null) {
            return Collections.emptyList();
        }
        return type.getFields().stream()
            .filter(this::isApiField)
            .flatMap(field -> field.getVariables().stream().map(variable -> apiField(field, variable)))
            .collect(java.util.stream.Collectors.toList());
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
        return new ApiField(name, TypeNameNormalizer.normalize(variable.getType()), false);
    }

    private Optional<String> jsonPropertyName(AnnotationExpr annotation) {
        return annotationParser.namedValue(annotation)
            .filter(value -> !value.trim().isEmpty());
    }
}
