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
