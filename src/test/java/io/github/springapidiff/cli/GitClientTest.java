package io.github.springapidiff.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitClientTest {
    @TempDir
    Path tempDir;

    private final GitClient gitClient = new GitClient();

    @Test
    void executesGitCommandAndResolvesPathOutput() throws Exception {
        gitClient.output(tempDir, "init");

        GitClient.CommandResult result = gitClient.execute(tempDir, false, "rev-parse", "--show-toplevel");

        assertThat(result.exitCode()).isZero();
        assertThat(Paths.get(result.output().trim()).toRealPath()).isEqualTo(tempDir.toRealPath());
        assertThat(gitClient.pathOutput(tempDir, "rev-parse", "--show-toplevel")).isEqualTo(tempDir.toRealPath());
    }

    @Test
    void returnsNonZeroResultWhenFailureIsAllowed() throws Exception {
        gitClient.output(tempDir, "init");

        GitClient.CommandResult result = gitClient.execute(tempDir, false, "rev-parse", "--verify", "missing^{commit}");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).isNotEmpty();
    }

    @Test
    void includesCommandAndOutputInStrictFailure() throws Exception {
        gitClient.output(tempDir, "init");

        assertThatThrownBy(() -> gitClient.output(tempDir, "rev-parse", "--verify", "missing^{commit}"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Git command failed: [git, rev-parse, --verify, missing^{commit}]")
            .hasMessageContaining("missing");
    }
}
