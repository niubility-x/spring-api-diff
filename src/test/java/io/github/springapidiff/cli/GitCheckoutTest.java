package io.github.springapidiff.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCheckoutTest {
    @TempDir
    Path tempDir;

    private final GitClient gitClient = new GitClient();

    @Test
    void currentCheckoutDoesNotRemoveRepository() throws Exception {
        gitClient.output(tempDir, "init");
        GitCheckout checkout = GitCheckout.current(tempDir);

        checkout.close();

        assertThat(checkout.path()).isEqualTo(tempDir);
        assertThat(tempDir).exists();
    }

    @Test
    void createsAndRemovesTemporaryWorktree() throws Exception {
        gitClient.output(tempDir, "init");
        gitClient.output(tempDir, "config", "user.name", "test");
        gitClient.output(tempDir, "config", "user.email", "test@example.com");
        Files.write(tempDir.resolve("api.txt"), "v1".getBytes(StandardCharsets.UTF_8));
        gitClient.output(tempDir, "add", "api.txt");
        gitClient.output(tempDir, "commit", "-m", "initial");
        String commit = gitClient.output(tempDir, "rev-parse", "HEAD").trim();

        GitCheckout checkout = GitCheckout.temporary(gitClient, tempDir, commit, "base");
        Path checkoutPath = checkout.path();
        Path checkoutRoot = checkoutPath.getParent();

        assertThat(checkoutPath.resolve("api.txt")).hasContent("v1");
        assertThat(gitClient.output(tempDir, "worktree", "list", "--porcelain")).contains(checkoutPath.toString().replace('\\', '/'));

        checkout.close();

        assertThat(checkoutPath).doesNotExist();
        assertThat(checkoutRoot).doesNotExist();
        assertThat(gitClient.output(tempDir, "worktree", "list", "--porcelain")).doesNotContain(checkoutPath.toString().replace('\\', '/'));
    }
}
