package io.github.springapidiff.scanner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.github.springapidiff.model.ApiBody;
import io.github.springapidiff.model.ApiParameter;
import io.github.springapidiff.model.ApiRequest;
import io.github.springapidiff.model.ApiResponse;
import io.github.springapidiff.model.Endpoint;
import io.github.springapidiff.util.PathUtils;
import io.github.springapidiff.util.TypeNameNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ControllerScanner {
    private final DtoScanner dtoScanner;
    private final SpringAnnotationParser annotationParser = new SpringAnnotationParser();

    public ControllerScanner(DtoScanner dtoScanner) {
        this.dtoScanner = dtoScanner;
    }

    public List<Endpoint> scan(CompilationUnit unit) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!annotationParser.hasAnnotation(type, "RestController")) {
                continue;
            }
            String controllerName = controllerName(unit, type);
            String classPath = annotationParser.findAnnotation(type, "RequestMapping")
                .flatMap(annotationParser::path)
                .orElse("/");
            for (MethodDeclaration method : type.getMethods()) {
                for (AnnotationExpr annotation : method.getAnnotations()) {
                    Optional<SpringAnnotationParser.MappingInfo> mapping = annotationParser.mapping(annotation);
                    if (!mapping.isPresent()) {
                        continue;
                    }
                    endpoints.add(endpoint(controllerName, method, classPath, mapping.get()));
                }
            }
        }
        return endpoints;
    }

    private Endpoint endpoint(
        String controllerName,
        MethodDeclaration method,
        String classPath,
        SpringAnnotationParser.MappingInfo mapping) {
        String path = PathUtils.join(classPath, mapping.path());
        ApiRequest request = request(method);
        String responseType = TypeNameNormalizer.normalize(method.getType());
        ApiResponse response = new ApiResponse(responseType, dtoScanner.fieldsFor(responseType));
        String id = mapping.method() + " " + path;
        return new Endpoint(id, mapping.method(), path, controllerName, method.getNameAsString(), request, response);
    }

    private ApiRequest request(MethodDeclaration method) {
        List<ApiParameter> pathVariables = new ArrayList<>();
        List<ApiParameter> queryParams = new ArrayList<>();
        ApiBody body = null;
        for (Parameter parameter : method.getParameters()) {
            String type = TypeNameNormalizer.normalize(parameter.getType());
            Optional<AnnotationExpr> pathVariable = annotationParser.findAnnotation(parameter, "PathVariable");
            if (pathVariable.isPresent()) {
                pathVariables.add(new ApiParameter(parameterName(pathVariable.get(), parameter), type, true));
                continue;
            }
            Optional<AnnotationExpr> requestParam = annotationParser.findAnnotation(parameter, "RequestParam");
            if (requestParam.isPresent()) {
                boolean required = annotationParser.required(parameter, "RequestParam", true);
                queryParams.add(new ApiParameter(parameterName(requestParam.get(), parameter), type, required));
                continue;
            }
            if (annotationParser.hasAnnotation(parameter, "RequestBody")) {
                boolean required = annotationParser.required(parameter, "RequestBody", true);
                body = new ApiBody(type, dtoScanner.fieldsFor(type).stream()
                    .map(field -> new io.github.springapidiff.model.ApiField(field.name(), field.type(), required || field.required()))
                    .collect(java.util.stream.Collectors.toList()));
            }
        }
        return new ApiRequest(pathVariables, queryParams, body);
    }

    private String parameterName(AnnotationExpr annotation, Parameter parameter) {
        return annotationParser.namedValue(annotation).orElse(parameter.getNameAsString());
    }

    private String controllerName(CompilationUnit unit, ClassOrInterfaceDeclaration type) {
        return unit.getPackageDeclaration()
            .map(packageDeclaration -> packageDeclaration.getNameAsString() + "." + type.getNameAsString())
            .orElse(type.getNameAsString());
    }
}
