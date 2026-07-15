package io.github.springapidiff.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.Severity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JsonReportWriter {
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public String write(List<Change> changes) throws IOException {
        List<ChangeEntry> entries = new ArrayList<>();
        for (Change change : changes) {
            entries.add(new ChangeEntry(
                change.severity().name(),
                change.type().name(),
                change.endpoint(),
                change.path(),
                change.oldValue(),
                change.newValue(),
                change.message(),
                ChangeAdvice.impact(change),
                ChangeAdvice.suggestion(change)));
        }
        return objectMapper.writeValueAsString(new Report(new Summary(
            changes.size(),
            count(changes, Severity.BREAKING),
            count(changes, Severity.WARNING),
            count(changes, Severity.NON_BREAKING)), entries)) + "\n";
    }

    private long count(List<Change> changes, Severity severity) {
        return changes.stream().filter(change -> change.severity() == severity).count();
    }

    public static class Report {
        public final Summary summary;
        public final List<ChangeEntry> changes;

        public Report(Summary summary, List<ChangeEntry> changes) {
            this.summary = summary;
            this.changes = changes;
        }
    }

    public static class Summary {
        public final int total;
        public final long breaking;
        public final long warning;
        public final long nonBreaking;

        public Summary(int total, long breaking, long warning, long nonBreaking) {
            this.total = total;
            this.breaking = breaking;
            this.warning = warning;
            this.nonBreaking = nonBreaking;
        }
    }

    public static class ChangeEntry {
        public final String severity;
        public final String type;
        public final String endpoint;
        public final String path;
        public final String oldValue;
        public final String newValue;
        public final String message;
        public final String impact;
        public final String suggestion;

        public ChangeEntry(
            String severity,
            String type,
            String endpoint,
            String path,
            String oldValue,
            String newValue,
            String message,
            String impact,
            String suggestion) {
            this.severity = severity;
            this.type = type;
            this.endpoint = endpoint;
            this.path = path;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.message = message;
            this.impact = impact;
            this.suggestion = suggestion;
        }
    }
}
