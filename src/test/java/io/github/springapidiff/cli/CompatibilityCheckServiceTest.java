package io.github.springapidiff.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompatibilityCheckServiceTest {
    @TempDir
    Path tempDir;

    private final GitClient gitClient = new GitClient();

    @Test
    void checksRefsReportsProgressAndCleansWorktrees() throws Exception {
        Path repo = initRepoWithModules("demo-v1");
        copyFixtureToPath("demo-v2", repo.resolve("services/user-service"));
        gitClient.output(repo, "add", ".");
        gitClient.output(repo, "commit", "-m", "change api");
        String worktreesBefore = gitClient.output(repo, "worktree", "list", "--porcelain");
        List<String> progress = new ArrayList<>();
        CheckRequest request = new CheckRequest(
            repo,
            "main~1",
            "HEAD",
            false,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.singletonList("* /api/users**"));

        CheckResult result = new CompatibilityCheckService(gitClient)
            .check(request, (step, total, message) -> progress.add(step + "/" + total + " " + message));

        assertThat(progress).containsExactly(
            "2/7 Preparing base source...",
            "3/7 Preparing head source...",
            "4/7 Resolving scan paths...",
            "5/7 Scanning base APIs...",
            "6/7 Scanning head APIs...",
            "7/7 Comparing snapshots...");
        assertThat(result.oldSnapshot().endpoints()).hasSize(4);
        assertThat(result.newSnapshot().endpoints()).hasSize(4);
        assertThat(result.changes()).isEmpty();
        assertThat(result.headScanPaths()).hasSize(2);
        assertThat(gitClient.output(repo, "worktree", "list", "--porcelain")).isEqualTo(worktreesBefore);
    }

    @Test
    void cleansBothWorktreesWhenScanPathResolutionFails() throws Exception {
        Path repo = initRepoWithFixture("demo-v1");
        String worktreesBefore = gitClient.output(repo, "worktree", "list", "--porcelain");
        List<Integer> progressSteps = new ArrayList<>();
        CheckRequest request = new CheckRequest(
            repo,
            "main",
            "HEAD",
            false,
            Paths.get("missing-module"),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());

        assertThatThrownBy(() -> new CompatibilityCheckService(gitClient)
            .check(request, (step, total, message) -> progressSteps.add(step)))
            .isInstanceOf(UserFacingException.class)
            .hasMessageContaining("模块目录不存在：missing-module");

        assertThat(progressSteps).containsExactly(2, 3, 4);
        assertThat(gitClient.output(repo, "worktree", "list", "--porcelain")).isEqualTo(worktreesBefore);
    }

    private Path initRepoWithModules(String fixture) throws Exception {
        Path repo = tempDir.resolve("repo-modules-" + fixture);
        Files.createDirectories(repo);
        gitClient.output(repo, "init", "-b", "main");
        gitClient.output(repo, "config", "user.name", "test");
        gitClient.output(repo, "config", "user.email", "test@example.com");
        copyFixtureToPath(fixture, repo.resolve("services/user-service"));
        copyFixtureToPath(fixture, repo.resolve("tools/audit-service"));
        gitClient.output(repo, "add", ".");
        gitClient.output(repo, "commit", "-m", "initial api");
        return repo;
    }

    private Path initRepoWithFixture(String fixture) throws Exception {
        Path repo = tempDir.resolve("repo-" + fixture);
        Files.createDirectories(repo);
        gitClient.output(repo, "init", "-b", "main");
        gitClient.output(repo, "config", "user.name", "test");
        gitClient.output(repo, "config", "user.email", "test@example.com");
        copyFixtureToPath(fixture, repo);
        gitClient.output(repo, "add", ".");
        gitClient.output(repo, "commit", "-m", "initial api");
        return repo;
    }

    private void copyFixtureToPath(String fixture, Path targetRoot) throws IOException {
        deleteDirectory(targetRoot.resolve("src"));
        Path source = Paths.get("src/test/resources/fixtures", fixture);
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path sourcePath : paths.collect(java.util.stream.Collectors.toList())) {
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

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> sorted = paths.collect(java.util.stream.Collectors.toList());
            sorted.sort(Collections.reverseOrder());
            for (Path path : sorted) {
                Files.deleteIfExists(path);
            }
        }
    }
}
