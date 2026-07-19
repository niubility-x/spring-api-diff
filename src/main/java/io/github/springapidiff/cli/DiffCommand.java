package io.github.springapidiff.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.Severity;
import io.github.springapidiff.diff.SnapshotDiffer;
import io.github.springapidiff.io.SnapshotReader;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.report.JsonReportWriter;
import io.github.springapidiff.report.MarkdownReportWriter;
import io.github.springapidiff.validation.DuplicateEndpointIdException;
import io.github.springapidiff.validation.InvalidSnapshotException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "diff", mixinStandardHelpOptions = true, description = "Compare two API snapshots.")
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
        try {
            String normalizedFormat = validateFormat();
            validateSnapshotFile("old", oldSnapshotPath);
            validateSnapshotFile("new", newSnapshotPath);
            ApiSnapshot oldSnapshot = readSnapshot("old", oldSnapshotPath);
            ApiSnapshot newSnapshot = readSnapshot("new", newSnapshotPath);
            List<Change> changes = new SnapshotDiffer().diff(oldSnapshot, newSnapshot);
            String output = writeReport(changes, normalizedFormat);
            writeOutput(output);
            boolean hasBreaking = changes.stream().anyMatch(change -> change.severity() == Severity.BREAKING);
            return failOnBreaking && hasBreaking ? 1 : 0;
        } catch (UserFacingException | DuplicateEndpointIdException | InvalidSnapshotException e) {
            System.err.println(e.getMessage());
            return 2;
        }
    }

    private String validateFormat() throws UserFacingException {
        String normalized = format.toLowerCase(Locale.ROOT);
        if (!"markdown".equals(normalized) && !"json".equals(normalized)) {
            throw new UserFacingException("Unsupported report format: " + format + ". Supported: markdown, json");
        }
        return normalized;
    }

    private void validateSnapshotFile(String label, Path path) throws UserFacingException {
        Path absolutePath = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolutePath)) {
            throw new UserFacingException("The " + label + " snapshot is not a file: " + absolutePath);
        }
    }

    private ApiSnapshot readSnapshot(String label, Path path) throws Exception {
        try {
            return new SnapshotReader().read(path);
        } catch (JsonProcessingException e) {
            throw new UserFacingException(
                "Invalid " + label + " snapshot JSON: " + path.toAbsolutePath().normalize() + "\n" + firstLine(e.getMessage()),
                e);
        }
    }

    private String firstLine(String message) {
        if (message == null) {
            return "Unable to parse snapshot JSON.";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private void writeOutput(String output) throws Exception {
        if (report == null) {
            System.out.print(output);
            return;
        }
        Path parent = report.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(report, output.getBytes(StandardCharsets.UTF_8));
        System.err.println("Wrote report: " + report.toAbsolutePath());
    }

    private String writeReport(List<Change> changes, String normalizedFormat) throws Exception {
        if ("json".equals(normalizedFormat)) {
            return new JsonReportWriter().write(changes);
        }
        return new MarkdownReportWriter().write(changes);
    }
}
