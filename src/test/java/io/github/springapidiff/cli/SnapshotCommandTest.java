package io.github.springapidiff.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class SnapshotCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsTwoAndPreservesOutputFileForDuplicateEndpoints() throws Exception {
        Path output = tempDir.resolve("snapshot.json");
        Files.write(output, "existing".getBytes(StandardCharsets.UTF_8));
        CommandResult result = execute(
            "--project", "src/test/resources/fixtures/duplicate-endpoints",
            "--out", output.toString());

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.output).isEmpty();
        assertThat(result.error).contains("Duplicate endpoint IDs detected:", "GET /duplicate");
        assertThat(new String(Files.readAllBytes(output), StandardCharsets.UTF_8)).isEqualTo("existing");
    }

    private CommandResult execute(String... args) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8.name()));
        System.setErr(new PrintStream(error, true, StandardCharsets.UTF_8.name()));
        try {
            int exitCode = new CommandLine(new SnapshotCommand()).execute(args);
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
