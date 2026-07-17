package io.github.springapidiff.diff;

import io.github.springapidiff.model.ApiBody;
import io.github.springapidiff.model.ApiField;
import io.github.springapidiff.model.ApiParameter;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.model.Endpoint;
import io.github.springapidiff.validation.SnapshotValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SnapshotDiffer {
    public List<Change> diff(ApiSnapshot oldSnapshot, ApiSnapshot newSnapshot) {
        SnapshotValidator validator = new SnapshotValidator();
        validator.validate(oldSnapshot, endpoint -> "old snapshot");
        validator.validate(newSnapshot, endpoint -> "new snapshot");
        Map<String, Endpoint> oldEndpoints = byId(oldSnapshot.endpoints());
        Map<String, Endpoint> newEndpoints = byId(newSnapshot.endpoints());
        List<Change> changes = new ArrayList<>();

        for (Endpoint oldEndpoint : oldEndpoints.values()) {
            Endpoint newEndpoint = newEndpoints.get(oldEndpoint.id());
            if (newEndpoint == null) {
                changes.add(change(
                    Severity.BREAKING,
                    ChangeType.ENDPOINT_REMOVED,
                    oldEndpoint.id(),
                    "endpoint",
                    oldEndpoint.id(),
                    null,
                    "Endpoint was removed."));
                continue;
            }
            compareEndpoint(oldEndpoint, newEndpoint, changes);
        }

        for (Endpoint newEndpoint : newEndpoints.values()) {
            if (!oldEndpoints.containsKey(newEndpoint.id())) {
                changes.add(change(
                    Severity.NON_BREAKING,
                    ChangeType.ENDPOINT_ADDED,
                    newEndpoint.id(),
                    "endpoint",
                    null,
                    newEndpoint.id(),
                    "Endpoint was added."));
            }
        }
        return changes;
    }

    private void compareEndpoint(Endpoint oldEndpoint, Endpoint newEndpoint, List<Change> changes) {
        compareParameters(
            oldEndpoint.id(),
            "request.pathVariables",
            oldEndpoint.request().pathVariables(),
            newEndpoint.request().pathVariables(),
            ParameterRules.PATH_VARIABLE,
            changes);
        compareParameters(
            oldEndpoint.id(),
            "request.queryParams",
            oldEndpoint.request().queryParams(),
            newEndpoint.request().queryParams(),
            ParameterRules.QUERY_PARAM,
            changes);
        compareBodyFields(oldEndpoint, newEndpoint, changes);
        compareFields(
            oldEndpoint.id(),
            "response",
            oldEndpoint.response().fields(),
            newEndpoint.response().fields(),
            FieldRules.RESPONSE,
            changes);
    }

    private void compareParameters(
        String endpoint,
        String pathPrefix,
        List<ApiParameter> oldParameters,
        List<ApiParameter> newParameters,
        ParameterRules rules,
        List<Change> changes) {
        Map<String, ApiParameter> oldByName = byName(oldParameters, ApiParameter::name);
        Map<String, ApiParameter> newByName = byName(newParameters, ApiParameter::name);

        for (ApiParameter oldParameter : oldByName.values()) {
            ApiParameter newParameter = newByName.get(oldParameter.name());
            if (newParameter == null) {
                if (rules.removedSeverity != null) {
                    changes.add(change(
                        rules.removedSeverity,
                        rules.removedType,
                        endpoint,
                        pathPrefix + "." + oldParameter.name(),
                        oldParameter.type(),
                        null,
                        rules.removedMessage(oldParameter.name())));
                }
            } else {
                if (!oldParameter.type().equals(newParameter.type())) {
                    changes.add(change(
                        Severity.BREAKING,
                        rules.typeChangedType,
                        endpoint,
                        pathPrefix + "." + oldParameter.name(),
                        oldParameter.type(),
                        newParameter.type(),
                        rules.typeChangedMessage(oldParameter.name(), oldParameter.type(), newParameter.type())));
                }
                if (rules == ParameterRules.QUERY_PARAM && !oldParameter.required() && newParameter.required()) {
                    changes.add(change(
                        Severity.BREAKING,
                        ChangeType.QUERY_PARAM_BECAME_REQUIRED,
                        endpoint,
                        pathPrefix + "." + oldParameter.name(),
                        "optional",
                        "required",
                        "Query parameter '" + oldParameter.name() + "' became required."));
                }
            }
        }

        if (rules == ParameterRules.QUERY_PARAM) {
            for (ApiParameter newParameter : newByName.values()) {
                if (!oldByName.containsKey(newParameter.name()) && newParameter.required()) {
                    changes.add(change(
                        Severity.BREAKING,
                        ChangeType.REQUIRED_QUERY_PARAM_ADDED,
                        endpoint,
                        pathPrefix + "." + newParameter.name(),
                        null,
                        newParameter.type(),
                        "Required query parameter '" + newParameter.name() + "' was added."));
                }
            }
        }
    }

    private void compareBodyFields(Endpoint oldEndpoint, Endpoint newEndpoint, List<Change> changes) {
        ApiBody oldBody = oldEndpoint.request().body();
        ApiBody newBody = newEndpoint.request().body();
        List<ApiField> oldFields = oldBody == null ? Collections.emptyList() : oldBody.fields();
        List<ApiField> newFields = newBody == null ? Collections.emptyList() : newBody.fields();
        compareFields(oldEndpoint.id(), "request.body", oldFields, newFields, FieldRules.REQUEST_BODY, changes);
    }

    private void compareFields(
        String endpoint,
        String pathPrefix,
        List<ApiField> oldFields,
        List<ApiField> newFields,
        FieldRules rules,
        List<Change> changes) {
        Map<String, ApiField> oldByName = byName(oldFields, ApiField::name);
        Map<String, ApiField> newByName = byName(newFields, ApiField::name);

        for (ApiField oldField : oldByName.values()) {
            ApiField newField = newByName.get(oldField.name());
            if (newField == null) {
                changes.add(change(
                    rules.removedSeverity,
                    rules.removedType,
                    endpoint,
                    pathPrefix + "." + oldField.name(),
                    oldField.type(),
                    null,
                    rules.removedMessage(oldField.name())));
            } else {
                if (!oldField.type().equals(newField.type())) {
                    changes.add(change(
                        Severity.BREAKING,
                        rules.typeChangedType,
                        endpoint,
                        pathPrefix + "." + oldField.name(),
                        oldField.type(),
                        newField.type(),
                        rules.typeChangedMessage(oldField.name(), oldField.type(), newField.type())));
                }
                if (rules == FieldRules.REQUEST_BODY && !oldField.required() && newField.required()) {
                    changes.add(change(
                        Severity.BREAKING,
                        ChangeType.REQUEST_BODY_FIELD_BECAME_REQUIRED,
                        endpoint,
                        pathPrefix + "." + oldField.name(),
                        "optional",
                        "required",
                        "Request body field '" + oldField.name() + "' became required."));
                }
            }
        }

        for (ApiField newField : newByName.values()) {
            if (oldByName.containsKey(newField.name())) {
                continue;
            }
            if (rules == FieldRules.REQUEST_BODY && newField.required()) {
                changes.add(change(
                    Severity.BREAKING,
                    ChangeType.REQUIRED_REQUEST_BODY_FIELD_ADDED,
                    endpoint,
                    pathPrefix + "." + newField.name(),
                    null,
                    newField.type(),
                    "Required request body field '" + newField.name() + "' was added."));
            } else if (rules == FieldRules.RESPONSE) {
                changes.add(change(
                    Severity.NON_BREAKING,
                    ChangeType.RESPONSE_FIELD_ADDED,
                    endpoint,
                    pathPrefix + "." + newField.name(),
                    null,
                    newField.type(),
                    "Response field '" + newField.name() + "' was added."));
            }
        }
    }

    private Map<String, Endpoint> byId(List<Endpoint> endpoints) {
        Map<String, Endpoint> byId = new LinkedHashMap<>();
        for (Endpoint endpoint : endpoints) {
            byId.put(endpoint.id(), endpoint);
        }
        return byId;
    }

    private <T> Map<String, T> byName(List<T> values, Function<T, String> key) {
        return values.stream()
            .collect(Collectors.toMap(key, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Change change(
        Severity severity,
        ChangeType type,
        String endpoint,
        String path,
        String oldValue,
        String newValue,
        String message) {
        return new Change(severity, type, endpoint, path, oldValue, newValue, message);
    }

    private enum ParameterRules {
        PATH_VARIABLE(
            Severity.BREAKING,
            ChangeType.PATH_VARIABLE_REMOVED,
            ChangeType.PATH_VARIABLE_TYPE_CHANGED,
            "Path variable"),
        QUERY_PARAM(
            Severity.WARNING,
            ChangeType.QUERY_PARAM_REMOVED,
            ChangeType.QUERY_PARAM_TYPE_CHANGED,
            "Query parameter");

        private final Severity removedSeverity;
        private final ChangeType removedType;
        private final ChangeType typeChangedType;
        private final String label;

        ParameterRules(Severity removedSeverity, ChangeType removedType, ChangeType typeChangedType, String label) {
            this.removedSeverity = removedSeverity;
            this.removedType = removedType;
            this.typeChangedType = typeChangedType;
            this.label = label;
        }

        private String removedMessage(String name) {
            return label + " '" + name + "' was removed.";
        }

        private String typeChangedMessage(String name, String oldType, String newType) {
            return label + " '" + name + "' changed type: " + oldType + " -> " + newType + ".";
        }
    }

    private enum FieldRules {
        REQUEST_BODY(
            Severity.WARNING,
            ChangeType.REQUEST_BODY_FIELD_REMOVED,
            ChangeType.REQUEST_BODY_FIELD_TYPE_CHANGED,
            "Request body field"),
        RESPONSE(
            Severity.BREAKING,
            ChangeType.RESPONSE_FIELD_REMOVED,
            ChangeType.RESPONSE_FIELD_TYPE_CHANGED,
            "Response field");

        private final Severity removedSeverity;
        private final ChangeType removedType;
        private final ChangeType typeChangedType;
        private final String label;

        FieldRules(Severity removedSeverity, ChangeType removedType, ChangeType typeChangedType, String label) {
            this.removedSeverity = removedSeverity;
            this.removedType = removedType;
            this.typeChangedType = typeChangedType;
            this.label = label;
        }

        private String removedMessage(String name) {
            return label + " '" + name + "' was removed.";
        }

        private String typeChangedMessage(String name, String oldType, String newType) {
            return label + " '" + name + "' changed type: " + oldType + " -> " + newType + ".";
        }
    }
}
