package io.github.springapidiff.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class CheckCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void checksCommittedGitRefsAndWritesActionableReport() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        copyFixture("demo-v2", repo);
        git(repo, "add", ".");
        git(repo, "commit", "-m", "change api");
        Path report = tempDir.resolve("api-diff.md");

        CommandResult result = runCheck(
            "--repo", repo.toString(),
            "--base", "main~1",
            "--head", "HEAD",
            "--report", report.toString(),
            "--fail-on-breaking");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains("BREAKING", "Impact:", "Suggestion:");
        assertThat(Files.readAllBytes(report))
            .asString(StandardCharsets.UTF_8)
            .contains("# API Compatibility Report", "**Impact:**", "**Suggestion:**");
    }

    @Test
    void defaultsToMainAndChecksDirtyWorktree() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        copyFixture("demo-v2", repo);

        CommandResult result = runCheck("--repo", repo.toString(), "--fail-on-breaking");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains("Base: main", "Head: worktree", "Response field 'email' was removed.");
    }

    private Path initRepoWithFixture(String fixture) throws Exception {
        Path repo = tempDir.resolve("repo-" + fixture);
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        copyFixture(fixture, repo);
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial api");
        return repo;
    }

    private void copyFixture(String fixture, Path repo) throws IOException {
        deleteDirectory(repo.resolve("src"));
        Path source = Paths.get("src/test/resources/fixtures", fixture);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path sourcePath : collect(paths)) {
                Path targetPath = repo.resolve(source.relativize(sourcePath).toString());
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private CommandResult runCheck(String... args) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8.name()));
        try {
            int exitCode = new CommandLine(new CheckCommand()).execute(args);
            return new CommandResult(exitCode, new String(output.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
        }
    }

    private String git(Path repo, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(repo.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = process.getInputStream().read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        int exitCode = process.waitFor();
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        if (exitCode != 0) {
            throw new IOException("Git command failed: " + command + "\n" + text);
        }
        return text;
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> sorted = collect(paths);
            sorted.sort(Comparator.reverseOrder());
            for (Path path : sorted) {
                Files.deleteIfExists(path);
            }
        }
    }

    private List<Path> collect(Stream<Path> paths) {
        List<Path> values = new ArrayList<>();
        paths.forEach(values::add);
        return values;
    }

    private static class CommandResult {
        private final int exitCode;
        private final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
