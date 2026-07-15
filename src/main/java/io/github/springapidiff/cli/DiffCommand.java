package io.github.springapidiff.cli;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.Severity;
import io.github.springapidiff.diff.SnapshotDiffer;
import io.github.springapidiff.io.SnapshotReader;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.report.JsonReportWriter;
import io.github.springapidiff.report.MarkdownReportWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "diff", description = "Compare two API snapshots.")
public class DiffCommand implements Callable<Integer> {
    @Option(names = "--old", required = true, description = "Old snapshot file.")
    private Path oldSnapshotPath;

    @Option(names = "--new", required = true, description = "New snapshot file.")
    private Path newSnapshotPath;

    @Option(names = "--report", description = "Markdown report output file. Prints to stdout if omitted.")
    private Path report;

    @Option(names = "--fail-on-breaking", description = "Return non-zero when breaking changes are found.")
    private boolean failOnBreaking;

    @Option(names = "--format", defaultValue = "markdown", description = "Report format. Supported: markdown, json.")
    private String format;

    @Override
    public Integer call() throws Exception {
        String normalizedFormat = format.toLowerCase(Locale.ROOT);
        if (!"markdown".equals(normalizedFormat) && !"json".equals(normalizedFormat)) {
            throw new IllegalArgumentException("Unsupported report format: " + format + ". Supported: markdown, json");
        }
        SnapshotReader reader = new SnapshotReader();
        ApiSnapshot oldSnapshot = reader.read(oldSnapshotPath);
        ApiSnapshot newSnapshot = reader.read(newSnapshotPath);
        List<Change> changes = new SnapshotDiffer().diff(oldSnapshot, newSnapshot);
        String output = writeReport(changes, normalizedFormat);
        if (report == null) {
            System.out.print(output);
        } else {
            Path parent = report.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(report, output.getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote report: " + report.toAbsolutePath());
        }
        boolean hasBreaking = changes.stream().anyMatch(change -> change.severity() == Severity.BREAKING);
        return failOnBreaking && hasBreaking ? 1 : 0;
    }

    private String writeReport(List<Change> changes, String normalizedFormat) throws Exception {
        if ("json".equals(normalizedFormat)) {
            return new JsonReportWriter().write(changes);
        }
        return new MarkdownReportWriter().write(changes);
    }
}
