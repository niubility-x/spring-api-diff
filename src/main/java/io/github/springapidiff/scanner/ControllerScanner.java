package io.github.springapidiff.scanner;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import io.github.springapidiff.model.ApiBody;
import io.github.springapidiff.model.ApiParameter;
import io.github.springapidiff.model.ApiRequest;
import io.github.springapidiff.model.ApiResponse;
import io.github.springapidiff.model.Endpoint;
import io.github.springapidiff.util.PathUtils;
import io.github.springapidiff.util.TypeNameNormalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ControllerScanner {
    private final DtoScanner dtoScanner;
    private final SpringAnnotationParser annotationParser = new SpringAnnotationParser();
    private final ControllerContractResolver contractResolver;

    public ControllerScanner(DtoScanner dtoScanner) {
        this(
            dtoScanner,
            new StringConstantResolver(Collections.emptyList()),
            new SourceTypeIndex(Collections.emptyList()));
    }

    ControllerScanner(DtoScanner dtoScanner, StringConstantResolver constantResolver) {
        this(dtoScanner, constantResolver, new SourceTypeIndex(Collections.emptyList()));
    }

    ControllerScanner(DtoScanner dtoScanner, StringConstantResolver constantResolver, SourceTypeIndex typeIndex) {
        this.dtoScanner = dtoScanner;
        this.contractResolver = new ControllerContractResolver(typeIndex, constantResolver, annotationParser);
    }

    public List<Endpoint> scan(CompilationUnit unit) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!isRestController(type)) {
                continue;
            }
            Optional<ControllerContractResolver.ControllerContract> contract = contractResolver.resolve(type);
            if (!contract.isPresent()) {
                continue;
            }
            String controllerName = controllerName(unit, type);
            for (MethodDeclaration method : type.getMethods()) {
                Optional<ControllerContractResolver.MethodContract> methodContract = contract.get().method(method);
                if (!methodContract.isPresent()) {
                    continue;
                }
                for (SpringAnnotationParser.MappingInfo mapping : methodContract.get().mappings()) {
                    for (String classPath : contract.get().classPaths()) {
                        endpoints.add(endpoint(
                            controllerName,
                            method,
                            methodContract.get().parameters(),
                            classPath,
                            mapping));
                    }
                }
            }
        }
        return endpoints;
    }

    private boolean isRestController(ClassOrInterfaceDeclaration type) {
        return annotationParser.hasAnnotation(type, "RestController")
            || (annotationParser.hasAnnotation(type, "Controller") && annotationParser.hasAnnotation(type, "ResponseBody"));
    }

    private Endpoint endpoint(
        String controllerName,
        MethodDeclaration method,
        List<ParameterContractResolver.ParameterContract> parameters,
        String classPath,
        SpringAnnotationParser.MappingInfo mapping) {
        String path = PathUtils.join(classPath, mapping.path());
        ApiRequest request = request(method, parameters);
        String responseType = TypeNameNormalizer.normalize(method.getType());
        ApiResponse response = new ApiResponse(responseType, dtoScanner.fieldsFor(responseType));
        String id = mapping.method() + " " + path;
        return new Endpoint(id, mapping.method(), path, controllerName, method.getNameAsString(), request, response);
    }

    private ApiRequest request(
        MethodDeclaration method,
        List<ParameterContractResolver.ParameterContract> contracts) {
        List<ApiParameter> pathVariables = new ArrayList<>();
        List<ApiParameter> queryParams = new ArrayList<>();
        ApiBody body = null;
        for (int index = 0; index < method.getParameters().size(); index++) {
            Parameter parameter = method.getParameter(index);
            ParameterContractResolver.ParameterContract contract = contracts.get(index);
            String type = TypeNameNormalizer.normalize(parameter.getType());
            if (contract.kind() == ParameterContractResolver.BindingKind.PATH_VARIABLE) {
                pathVariables.add(new ApiParameter(contract.name(), type, true));
            } else if (contract.kind() == ParameterContractResolver.BindingKind.REQUEST_PARAM) {
                queryParams.add(new ApiParameter(contract.name(), type, contract.required()));
            } else if (contract.kind() == ParameterContractResolver.BindingKind.REQUEST_BODY) {
                body = new ApiBody(type, dtoScanner.fieldsFor(type).stream()
                    .map(field -> new io.github.springapidiff.model.ApiField(
                        field.name(), field.type(), contract.required() && field.required()))
                    .collect(java.util.stream.Collectors.toList()));
            }
        }
        return new ApiRequest(pathVariables, queryParams, body);
    }

    private String controllerName(CompilationUnit unit, ClassOrInterfaceDeclaration type) {
        return unit.getPackageDeclaration()
            .map(packageDeclaration -> packageDeclaration.getNameAsString() + "." + type.getNameAsString())
            .orElse(type.getNameAsString());
    }
}
