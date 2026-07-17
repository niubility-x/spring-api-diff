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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    void ignoresEndpointChangesFromConfigFile() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        copyFixture("demo-v2", repo);
        Files.write(repo.resolve("spring-api-diff.yml"), Arrays.asList(
            "base: main",
            "worktree: true",
            "failOnBreaking: true",
            "ignore:",
            "  endpoints:",
            "    - \"* /api/users**\""
        ), StandardCharsets.UTF_8);

        CommandResult result = runCheck("--repo", repo.toString());

        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.output).contains("Changes: 0", "No API changes detected.");
        assertThat(result.output).doesNotContain("Response field 'email' was removed.");
    }

    @Test
    void readsCheckOptionsFromConfigFile() throws Exception {
        Path repo = tempDir.resolve("config-repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        copyFixtureToPath("demo-v1", repo.resolve("services/user-service"));
        copyFixtureToPath("demo-v1", repo.resolve("tools/pdf-service"));
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial api");
        copyFixtureToPath("demo-v2", repo.resolve("services/user-service"));
        Files.write(repo.resolve("spring-api-diff.yml"), Arrays.asList(
            "base: main~1",
            "head: HEAD",
            "failOnBreaking: true",
            "modules:",
            "  include:",
            "    - services/*"
        ), StandardCharsets.UTF_8);
        git(repo, "add", ".");
        git(repo, "commit", "-m", "change api");

        CommandResult result = runCheck("--repo", repo.toString());

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains("Base: main~1", "Base source: config base", "services/user-service/src/main/java");
        assertThat(result.output).doesNotContain("tools/pdf-service/src/main/java");
    }

    @Test
    void commandLineOptionsOverrideConfigFile() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        copyFixture("demo-v2", repo);
        Files.write(repo.resolve("spring-api-diff.yml"), Arrays.asList(
            "base: main",
            "worktree: true",
            "failOnBreaking: false"
        ), StandardCharsets.UTF_8);

        CommandResult result = runCheck("--repo", repo.toString(), "--base", "main", "--worktree", "--fail-on-breaking");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains("Base source: explicit --base", "Head: worktree");
    }

    @Test
    void defaultsToMainAndChecksDirtyWorktree() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        copyFixture("demo-v2", repo);

        CommandResult result = runCheck("--repo", repo.toString(), "--fail-on-breaking");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains("Base: main", "Base source: fallback branch main", "Head: worktree", "Scan paths:", "- src/main/java", "Response field 'email' was removed.");
    }

    @Test
    void automaticallyChecksDiscoveredModulesFromGitRoot() throws Exception {
        Path repo = tempDir.resolve("auto-module-repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        copyFixtureToPath("demo-v1", repo.resolve("services/user-service"));
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial api");
        copyFixtureToPath("demo-v2", repo.resolve("services/user-service"));
        git(repo, "add", ".");
        git(repo, "commit", "-m", "change api");

        CommandResult result = runCheck(
            "--repo", repo.toString(),
            "--base", "main~1",
            "--head", "HEAD",
            "--fail-on-breaking");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains("BREAKING", "services/user-service/src/main/java", "Response field 'email' was removed.");
    }

    @Test
    void checksSpecifiedModuleRelativeToGitRoot() throws Exception {
        Path repo = tempDir.resolve("multi-module-repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        copyFixtureToPath("demo-v1", repo.resolve("user-service"));
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial api");
        copyFixtureToPath("demo-v2", repo.resolve("user-service"));
        git(repo, "add", ".");
        git(repo, "commit", "-m", "change api");

        CommandResult result = runCheck(
            "--repo", repo.toString(),
            "--base", "main~1",
            "--head", "HEAD",
            "--module", "user-service",
            "--fail-on-breaking");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains("Base source: explicit --base", "user-service/src/main/java", "Response field 'email' was removed.");
    }

    @Test
    void printsFriendlyMessageWhenModuleIsMissing() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");

        CommandResult result = runCheck(
            "--repo", repo.toString(),
            "--base", "main",
            "--head", "HEAD",
            "--module", "user-service");

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.errorOutput).contains(
            "模块目录不存在：user-service",
            "--module 是相对于 Git 仓库根目录的路径",
            "base 和 head 两个版本都包含该模块");
    }

    @Test
    void suggestsModuleOptionWhenSourceRootIsMissing() throws Exception {
        Path repo = tempDir.resolve("root-without-source");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        Files.write(repo.resolve("pom.xml"), "<project></project>".getBytes(StandardCharsets.UTF_8));
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial");

        CommandResult result = runCheck("--repo", repo.toString(), "--base", "main", "--head", "HEAD");

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.errorOutput).contains(
            "没有找到 src/main/java，也没有自动发现包含 src/main/java 的子模块。",
            "spring-api-diff check --module <module-path>");
    }

    @Test
    void printsFriendlyMessageWhenRepoIsMissing() throws Exception {
        Path notRepo = tempDir.resolve("not-repo");
        Files.createDirectories(notRepo);

        CommandResult result = runCheck("--repo", notRepo.toString());

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.errorOutput).contains("not a Git repository", "--repo <path>");
    }

    @Test
    void fetchesMissingCiTargetBranchWhenRequested() throws Exception {
        Path seed = tempDir.resolve("fetch-seed");
        Files.createDirectories(seed);
        git(seed, "init", "-b", "main");
        git(seed, "config", "user.name", "test");
        git(seed, "config", "user.email", "test@example.com");
        copyFixture("demo-v1", seed);
        git(seed, "add", ".");
        git(seed, "commit", "-m", "initial api");
        Path remote = tempDir.resolve("remote.git");
        git(seed, "init", "--bare", remote.toString());
        git(seed, "remote", "add", "origin", remote.toString());
        git(seed, "push", "origin", "main");
        git(seed, "checkout", "-b", "feature/api-change");
        copyFixture("demo-v2", seed);
        git(seed, "add", ".");
        git(seed, "commit", "-m", "change api");
        git(seed, "push", "origin", "feature/api-change");

        Path repo = tempDir.resolve("ci-fetch-repo");
        runGit(tempDir, "clone", "--branch", "feature/api-change", "--single-branch", remote.toString(), repo.toString());
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        Map<String, String> env = new HashMap<>();
        env.put("GITHUB_BASE_REF", "main");

        CommandResult result = runCheck(env, "--repo", repo.toString(), "--fetch", "--fail-on-breaking");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains(
            "Base: origin/main",
            "Base source: CI target branch GITHUB_BASE_REF=main after git fetch",
            "Response field 'email' was removed.");
    }

    @Test
    void detectsCiTargetBranchAsDefaultBase() throws Exception {
        Path repo = tempDir.resolve("ci-repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "develop");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        copyFixture("demo-v1", repo);
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial api");
        git(repo, "checkout", "-b", "feature/api-change");
        copyFixture("demo-v2", repo);
        Map<String, String> env = new HashMap<>();
        env.put("GITHUB_BASE_REF", "develop");

        CommandResult result = runCheck(env, "--repo", repo.toString(), "--fail-on-breaking");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains("Base: develop", "Base source: CI target branch GITHUB_BASE_REF=develop", "Head: worktree", "Response field 'email' was removed.");
    }

    @Test
    void printsFriendlyMessageWhenCiTargetBranchWasNotFetched() throws Exception {
        Path repo = tempDir.resolve("ci-missing-base-repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "feature/api-change");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        copyFixture("demo-v1", repo);
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial api");
        Map<String, String> env = new HashMap<>();
        env.put("CI_MERGE_REQUEST_TARGET_BRANCH_NAME", "develop");

        CommandResult result = runCheck(env, "--repo", repo.toString());

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.errorOutput).contains(
            "Detected CI target branch develop",
            "origin/develop",
            "git fetch origin develop:refs/remotes/origin/develop");
    }

    @Test
    void detectsDevelopAsDefaultBaseWhenMainIsMissing() throws Exception {
        Path repo = tempDir.resolve("develop-repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "develop");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        copyFixture("demo-v1", repo);
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial api");
        git(repo, "checkout", "-b", "feature/api-change");
        copyFixture("demo-v2", repo);

        CommandResult result = runCheck("--repo", repo.toString(), "--fail-on-breaking");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output).contains("Base: develop", "Base source: fallback branch develop", "Head: worktree", "Response field 'email' was removed.");
    }

    @Test
    void printsFriendlyMessageWhenBaseCannotBeDetected() throws Exception {
        Path repo = tempDir.resolve("dev-repo");
        Files.createDirectories(repo);
        git(repo, "init", "-b", "dev");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        Files.createDirectories(repo.resolve("src/main/java/com/example"));
        Files.write(repo.resolve("src/main/java/com/example/App.java"), "package com.example; class App {}".getBytes(StandardCharsets.UTF_8));
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial");

        CommandResult result = runCheck("--repo", repo.toString());

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.errorOutput).contains("No base ref could be detected", "origin/HEAD", "develop", "--base origin/main");
    }

    @Test
    void warnsWhenNoControllersAreScanned() throws Exception {
        Path repo = tempDir.resolve("empty-api-repo");
        Files.createDirectories(repo.resolve("src/main/java/com/example"));
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        Files.write(repo.resolve("src/main/java/com/example/App.java"), "package com.example; class App {}".getBytes(StandardCharsets.UTF_8));
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial");

        CommandResult result = runCheck("--repo", repo.toString(), "--base", "main", "--head", "HEAD");

        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.output).contains("Warning: No Spring Controller endpoints were found", "Possible causes");
    }

    @Test
    void printsProgressToErrorOutput() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        copyFixture("demo-v2", repo);

        CommandResult result = runCheck("--repo", repo.toString(), "--base", "main", "--worktree");

        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.errorOutput).contains(
            "[1/7] Selecting Git refs...",
            "[5/7] Scanning base APIs...",
            "[7/7] Comparing snapshots...");
        assertThat(result.output).startsWith("API compatibility check");
    }

    @Test
    void suppressesProgressWhenQuietIsRequested() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        copyFixture("demo-v2", repo);

        CommandResult result = runCheck("--repo", repo.toString(), "--base", "main", "--worktree", "--quiet");

        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.errorOutput).isEmpty();
        assertThat(result.output).contains("API compatibility check", "Head: worktree");
    }

    @Test
    void suppressesProgressFromConfigFile() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        copyFixture("demo-v2", repo);
        Files.write(repo.resolve("spring-api-diff.yml"), Arrays.asList(
            "base: main",
            "worktree: true",
            "quiet: true"
        ), StandardCharsets.UTF_8);

        CommandResult result = runCheck("--repo", repo.toString());

        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.errorOutput).isEmpty();
        assertThat(result.output).contains("API compatibility check", "Head: worktree");
    }

    @Test
    void writesJsonReportWhenRequested() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        copyFixture("demo-v2", repo);
        git(repo, "add", ".");
        git(repo, "commit", "-m", "change api");
        Path report = tempDir.resolve("api-diff.json");

        CommandResult result = runCheck(
            "--repo", repo.toString(),
            "--base", "main~1",
            "--head", "HEAD",
            "--report", report.toString(),
            "--format", "json");

        assertThat(result.exitCode).isEqualTo(0);
        assertThat(Files.readAllBytes(report))
            .asString(StandardCharsets.UTF_8)
            .contains("\"summary\"", "\"severity\" : \"BREAKING\"", "\"endpoint\" : \"GET /api/users/{id}\"");
    }

    @Test
    void returnsTwoWithoutWritingReportForDuplicateEndpoints() throws Exception {
        Path repo = initRepoWithFixture("duplicate-endpoints");
        Path report = tempDir.resolve("duplicate-report.md");
        Files.write(report, "existing".getBytes(StandardCharsets.UTF_8));

        CommandResult result = runCheck(
            "--repo", repo.toString(),
            "--base", "main",
            "--worktree",
            "--quiet",
            "--report", report.toString());

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.output).isEmpty();
        assertThat(result.errorOutput).contains("Duplicate endpoint IDs detected:", "GET /duplicate");
        assertThat(new String(Files.readAllBytes(report), StandardCharsets.UTF_8)).isEqualTo("existing");
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
        copyFixtureToPath(fixture, repo);
    }

    private void copyFixtureToPath(String fixture, Path targetRoot) throws IOException {
        deleteDirectory(targetRoot.resolve("src"));
        Path source = Paths.get("src/test/resources/fixtures", fixture);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path sourcePath : collect(paths)) {
                Path targetPath = targetRoot.resolve(source.relativize(sourcePath).toString());
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
        return runCheck(new CheckCommand(), args);
    }

    private CommandResult runCheck(Map<String, String> env, String... args) throws Exception {
        return runCheck(new TestCheckCommand(env), args);
    }

    private CommandResult runCheck(CheckCommand command, String... args) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8.name()));
        System.setErr(new PrintStream(errorOutput, true, StandardCharsets.UTF_8.name()));
        try {
            int exitCode = new CommandLine(command).execute(args);
            return new CommandResult(
                exitCode,
                new String(output.toByteArray(), StandardCharsets.UTF_8),
                new String(errorOutput.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private String git(Path repo, String... args) throws Exception {
        return runGit(repo, args);
    }

    private String runGit(Path directory, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(directory.toFile());
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

    private static class TestCheckCommand extends CheckCommand {
        private final Map<String, String> env;

        private TestCheckCommand(Map<String, String> env) {
            this.env = env;
        }

        @Override
        protected Map<String, String> environment() {
            return env;
        }
    }

    private static class CommandResult {
        private final int exitCode;
        private final String output;
        private final String errorOutput;

        private CommandResult(int exitCode, String output, String errorOutput) {
            this.exitCode = exitCode;
            this.output = output;
            this.errorOutput = errorOutput;
        }
    }
}
