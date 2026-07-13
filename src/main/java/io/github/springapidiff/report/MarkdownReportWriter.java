package io.github.springapidiff.report;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.Severity;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MarkdownReportWriter {
    public String write(List<Change> changes) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# API Diff Report\n\n");
        markdown.append("## Summary\n\n");
        markdown.append("- Breaking changes: ").append(count(changes, Severity.BREAKING)).append("\n");
        markdown.append("- Warnings: ").append(count(changes, Severity.WARNING)).append("\n");
        markdown.append("- Non-breaking changes: ").append(count(changes, Severity.NON_BREAKING)).append("\n\n");

        appendSection(markdown, "Breaking Changes", changes, Severity.BREAKING);
        appendSection(markdown, "Warnings", changes, Severity.WARNING);
        appendSection(markdown, "Non-breaking Changes", changes, Severity.NON_BREAKING);
        return markdown.toString();
    }

    private long count(List<Change> changes, Severity severity) {
        return changes.stream().filter(change -> change.severity() == severity).count();
    }

    private void appendSection(StringBuilder markdown, String title, List<Change> changes, Severity severity) {
        markdown.append("## ").append(title).append("\n\n");
        List<Change> sectionChanges = changes.stream()
            .filter(change -> change.severity() == severity)
            .sorted(Comparator.comparing(Change::endpoint).thenComparing(Change::path))
            .collect(Collectors.toList());
        if (sectionChanges.isEmpty()) {
            markdown.append("None.\n\n");
            return;
        }

        Map<String, List<Change>> byEndpoint = sectionChanges.stream()
            .collect(Collectors.groupingBy(Change::endpoint, java.util.LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<Change>> entry : byEndpoint.entrySet()) {
            markdown.append("### ").append(entry.getKey()).append("\n\n");
            for (Change change : entry.getValue()) {
                markdown.append("- ").append(change.message());
                if (change.oldValue() != null || change.newValue() != null) {
                    markdown.append(" (`")
                        .append(change.oldValue() == null ? "-" : change.oldValue())
                        .append("` -> `")
                        .append(change.newValue() == null ? "-" : change.newValue())
                        .append("`)");
                }
                markdown.append("\n");
            }
            markdown.append("\n");
        }
    }
}
