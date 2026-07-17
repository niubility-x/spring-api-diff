package io.github.springapidiff.scanner;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class ParameterContractResolver {
    private final SpringAnnotationParser annotationParser;

    ParameterContractResolver(SpringAnnotationParser annotationParser) {
        this.annotationParser = annotationParser;
    }

    Optional<List<ParameterContract>> resolve(
        MethodDeclaration implementation,
        List<MethodDeclaration> interfaceMethods) {
        List<ParameterContract> contracts = new ArrayList<>();
        for (int index = 0; index < implementation.getParameters().size(); index++) {
            BindingLookup ownBinding = binding(implementation.getParameter(index));
            if (ownBinding.conflict) {
                return Optional.empty();
            }
            List<ParameterContract> inherited = inheritedBindings(interfaceMethods, index);
            if (inherited == null) {
                return Optional.empty();
            }
            if (ownBinding.contract.isPresent()) {
                for (ParameterContract candidate : inherited) {
                    if (candidate.kind != ownBinding.contract.get().kind) {
                        return Optional.empty();
                    }
                }
                contracts.add(ownBinding.contract.get());
            } else if (inherited.isEmpty()) {
                contracts.add(ParameterContract.none());
            } else {
                ParameterContract selected = inherited.get(0);
                for (int candidate = 1; candidate < inherited.size(); candidate++) {
                    if (!selected.sameContract(inherited.get(candidate))) {
                        return Optional.empty();
                    }
                }
                contracts.add(selected);
            }
        }
        return Optional.of(contracts);
    }

    private List<ParameterContract> inheritedBindings(List<MethodDeclaration> interfaceMethods, int index) {
        List<ParameterContract> inherited = new ArrayList<>();
        for (MethodDeclaration interfaceMethod : interfaceMethods) {
            if (interfaceMethod.getParameters().size() <= index) {
                return null;
            }
            BindingLookup candidate = binding(interfaceMethod.getParameter(index));
            if (candidate.conflict) {
                return null;
            }
            candidate.contract.ifPresent(inherited::add);
        }
        return inherited;
    }

    private BindingLookup binding(Parameter parameter) {
        List<ParameterContract> contracts = new ArrayList<>();
        annotationParser.findAnnotation(parameter, "PathVariable")
            .ifPresent(annotation -> contracts.add(ParameterContract.pathVariable(
                annotationParser.namedValue(annotation).orElse(parameter.getNameAsString()))));
        annotationParser.findAnnotation(parameter, "RequestParam")
            .ifPresent(annotation -> contracts.add(ParameterContract.requestParam(
                annotationParser.namedValue(annotation).orElse(parameter.getNameAsString()),
                annotationParser.hasPair(annotation, "defaultValue")
                    ? false
                    : annotationParser.required(annotation).orElse(true))));
        annotationParser.findAnnotation(parameter, "RequestBody")
            .ifPresent(annotation -> contracts.add(ParameterContract.requestBody(
                annotationParser.required(annotation).orElse(true))));
        return contracts.size() > 1
            ? BindingLookup.conflict()
            : new BindingLookup(contracts.isEmpty() ? Optional.empty() : Optional.of(contracts.get(0)), false);
    }

    static class ParameterContract {
        private final BindingKind kind;
        private final String name;
        private final boolean required;

        private ParameterContract(BindingKind kind, String name, boolean required) {
            this.kind = kind;
            this.name = name;
            this.required = required;
        }

        private static ParameterContract none() {
            return new ParameterContract(BindingKind.NONE, null, false);
        }

        private static ParameterContract pathVariable(String name) {
            return new ParameterContract(BindingKind.PATH_VARIABLE, name, true);
        }

        private static ParameterContract requestParam(String name, boolean required) {
            return new ParameterContract(BindingKind.REQUEST_PARAM, name, required);
        }

        private static ParameterContract requestBody(boolean required) {
            return new ParameterContract(BindingKind.REQUEST_BODY, null, required);
        }

        BindingKind kind() {
            return kind;
        }

        String name() {
            return name;
        }

        boolean required() {
            return required;
        }

        private boolean sameContract(ParameterContract other) {
            if (kind != other.kind || required != other.required) {
                return false;
            }
            return name == null ? other.name == null : name.equals(other.name);
        }
    }

    enum BindingKind {
        NONE,
        PATH_VARIABLE,
        REQUEST_PARAM,
        REQUEST_BODY
    }

    private static class BindingLookup {
        private final Optional<ParameterContract> contract;
        private final boolean conflict;

        private BindingLookup(Optional<ParameterContract> contract, boolean conflict) {
            this.contract = contract;
            this.conflict = conflict;
        }

        private static BindingLookup conflict() {
            return new BindingLookup(Optional.empty(), true);
        }
    }
}
