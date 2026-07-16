package io.github.springapidiff.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

class GitCheckout {
    private final GitClient gitClient;
    private final Path repoRoot;
    private final Path path;
    private final Path tempRoot;
    private final boolean temporaryWorktree;

    private GitCheckout(GitClient gitClient, Path repoRoot, Path path, Path tempRoot, boolean temporaryWorktree) {
        this.gitClient = gitClient;
        this.repoRoot = repoRoot;
        this.path = path;
        this.tempRoot = tempRoot;
        this.temporaryWorktree = temporaryWorktree;
    }

    static GitCheckout current(Path path) {
        return new GitCheckout(null, null, path, null, false);
    }

    static GitCheckout temporary(GitClient gitClient, Path repoRoot, String commit, String label)
        throws IOException, InterruptedException {
        Path tempRoot = Files.createTempDirectory("spring-api-diff-");
        Path checkoutPath = tempRoot.resolve(label);
        try {
            gitClient.output(repoRoot, "worktree", "add", "--detach", checkoutPath.toString(), commit);
            return new GitCheckout(gitClient, repoRoot, checkoutPath, tempRoot, true);
        } catch (IOException | InterruptedException e) {
            try {
                deleteDirectory(tempRoot);
            } catch (IOException ignored) {
                // Preserve the worktree creation failure.
            }
            throw e;
        }
    }

    Path path() {
        return path;
    }

    void close() throws IOException, InterruptedException {
        if (!temporaryWorktree) {
            return;
        }
        gitClient.execute(repoRoot, false, "worktree", "remove", "--force", path.toString());
        deleteDirectory(tempRoot);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> sorted = new ArrayList<>();
            paths.forEach(sorted::add);
            Collections.sort(sorted, Comparator.reverseOrder());
            for (Path path : sorted) {
                Files.deleteIfExists(path);
            }
        }
    }
}
