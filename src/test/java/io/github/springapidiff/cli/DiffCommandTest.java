package io.github.springapidiff.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.springapidiff.model.ApiRequest;
import io.github.springapidiff.model.ApiResponse;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.model.Endpoint;
import io.github.springapidiff.model.ProjectInfo;
import io.github.springapidiff.io.SnapshotWriter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class DiffCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsTwoAndPreservesReportForDuplicateSnapshotEndpoints() throws Exception {
        Path oldSnapshot = tempDir.resolve("old.json");
        Path newSnapshot = tempDir.resolve("new.json");
        writeDuplicateSnapshot(oldSnapshot);
        new SnapshotWriter().write(snapshot(Collections.singletonList(endpoint("POST /users", "create"))), newSnapshot);
        Path report = tempDir.resolve("report.md");
        Files.write(report, "existing".getBytes(StandardCharsets.UTF_8));

        CommandResult result = execute(
            "--old", oldSnapshot.toString(),
            "--new", newSnapshot.toString(),
            "--report", report.toString());

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.output).isEmpty();
        assertThat(result.error).contains("Duplicate endpoint IDs detected:", "GET /users");
        assertThat(new String(Files.readAllBytes(report), StandardCharsets.UTF_8)).isEqualTo("existing");
    }

    private void writeDuplicateSnapshot(Path path) throws Exception {
        ApiSnapshot snapshot = snapshot(Arrays.asList(
            endpoint("GET /users", "first"),
            endpoint("GET /users", "second")));
        new SnapshotWriter().write(snapshot, path);
    }

    private ApiSnapshot snapshot(java.util.List<Endpoint> endpoints) {
        return new ApiSnapshot("1", Instant.EPOCH, new ProjectInfo("test", "8", "unknown"), endpoints);
    }

    private Endpoint endpoint(String id, String handler) {
        int separator = id.indexOf(' ');
        return new Endpoint(
            id,
            id.substring(0, separator),
            id.substring(separator + 1),
            "UserController",
            handler,
            new ApiRequest(Collections.emptyList(), Collections.emptyList(), null),
            new ApiResponse("void", Collections.emptyList()));
    }

    private CommandResult execute(String... args) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8.name()));
        System.setErr(new PrintStream(error, true, StandardCharsets.UTF_8.name()));
        try {
            int exitCode = new CommandLine(new DiffCommand()).execute(args);
            return new CommandResult(
                exitCode,
                new String(output.toByteArray(), StandardCharsets.UTF_8),
                new String(error.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static class CommandResult {
        private final int exitCode;
        private final String output;
        private final String error;

        private CommandResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }
    }
}
