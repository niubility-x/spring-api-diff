package io.github.springapidiff.validation;

import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.model.Endpoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public class SnapshotValidator {
    public void validate(ApiSnapshot snapshot) {
        validate(snapshot, endpoint -> null);
    }

    public void validate(ApiSnapshot snapshot, Function<Endpoint, String> sourceProvider) {
        validateStructure(snapshot);
        Map<String, List<Occurrence>> byId = new TreeMap<>();
        for (Endpoint endpoint : snapshot.endpoints()) {
            byId.computeIfAbsent(endpoint.id(), ignored -> new ArrayList<>())
                .add(new Occurrence(
                    sourceProvider.apply(endpoint),
                    endpoint.controller(),
                    endpoint.handler()));
        }
        Map<String, List<Occurrence>> conflicts = new TreeMap<>();
        for (Map.Entry<String, List<Occurrence>> entry : byId.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<Occurrence> occurrences = entry.getValue();
                occurrences.sort(Comparator
                    .comparing(Occurrence::source, Comparator.nullsLast(String::compareTo))
                    .thenComparing(Occurrence::controller)
                    .thenComparing(Occurrence::handler));
                conflicts.put(entry.getKey(), occurrences);
            }
        }
        if (!conflicts.isEmpty()) {
            throw new DuplicateEndpointIdException(message(conflicts));
        }
    }

    private void validateStructure(ApiSnapshot snapshot) {
        if (snapshot == null) {
            invalid("snapshot", "must not be null");
        }
        if (snapshot.endpoints() == null) {
            invalid("endpoints", "must be an array");
        }
        for (int index = 0; index < snapshot.endpoints().size(); index++) {
            Endpoint endpoint = snapshot.endpoints().get(index);
            String path = "endpoints[" + index + "]";
            if (endpoint == null) {
                invalid(path, "must not be null");
            }
            if (endpoint.id() == null || endpoint.id().trim().isEmpty()) {
                invalid(path + ".id", "must not be blank");
            }
            if (endpoint.request() == null) {
                invalid(path + ".request", "must not be null");
            }
            if (endpoint.response() == null) {
                invalid(path + ".response", "must not be null");
            }
            if (endpoint.request().pathVariables() == null) {
                invalid(path + ".request.pathVariables", "must be an array");
            }
            if (endpoint.request().queryParams() == null) {
                invalid(path + ".request.queryParams", "must be an array");
            }
            if (endpoint.request().body() != null && endpoint.request().body().fields() == null) {
                invalid(path + ".request.body.fields", "must be an array");
            }
            if (endpoint.response().fields() == null) {
                invalid(path + ".response.fields", "must be an array");
            }
        }
    }

    private void invalid(String path, String reason) {
        throw new InvalidSnapshotException("Invalid snapshot: " + path + " " + reason + ".");
    }

    private String message(Map<String, List<Occurrence>> conflicts) {
        StringBuilder message = new StringBuilder("Duplicate endpoint IDs detected:\n");
        for (Map.Entry<String, List<Occurrence>> entry : conflicts.entrySet()) {
            message.append("- ").append(entry.getKey()).append("\n");
            for (Occurrence occurrence : entry.getValue()) {
                message.append("  - ");
                if (occurrence.source != null && !occurrence.source.isEmpty()) {
                    message.append("[").append(occurrence.source).append("] ");
                }
                message.append(occurrence.controller).append("#").append(occurrence.handler).append("\n");
            }
        }
        message.append("Each HTTP method and path must identify exactly one endpoint.");
        return message.toString();
    }

    private static class Occurrence {
        private final String source;
        private final String controller;
        private final String handler;

        private Occurrence(String source, String controller, String handler) {
            this.source = source;
            this.controller = controller == null ? "<unknown>" : controller;
            this.handler = handler == null ? "<unknown>" : handler;
        }

        private String source() {
            return source;
        }

        private String controller() {
            return controller;
        }

        private String handler() {
            return handler;
        }
    }
}
