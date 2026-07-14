package io.github.springapidiff.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaseRefDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsCiTargetBranchWhenLocalRefExists() throws Exception {
        Path repo = initRepo("develop");
        git(repo, "checkout", "-b", "feature/api-change");
        Map<String, String> env = new HashMap<>();
        env.put("GITHUB_BASE_REF", "develop");

        BaseSelection selection = new BaseRefDetector(false, env).detect(repo);

        assertThat(selection.ref()).isEqualTo("develop");
        assertThat(selection.source()).isEqualTo("CI target branch GITHUB_BASE_REF=develop");
    }

    @Test
    void reportsMissingCiTargetBranchWhenFetchIsDisabled() throws Exception {
        Path repo = initRepo("feature/api-change");
        Map<String, String> env = new HashMap<>();
        env.put("CI_MERGE_REQUEST_TARGET_BRANCH_NAME", "develop");

        assertThatThrownBy(() -> new BaseRefDetector(false, env).detect(repo))
            .isInstanceOf(UserFacingException.class)
            .hasMessageContaining("Detected CI target branch develop")
            .hasMessageContaining("origin/develop")
            .hasMessageContaining("git fetch origin develop:refs/remotes/origin/develop");
    }

    @Test
    void fallsBackToDevelopBranch() throws Exception {
        Path repo = initRepo("develop");
        git(repo, "checkout", "-b", "feature/api-change");

        BaseSelection selection = new BaseRefDetector(false, Collections.emptyMap()).detect(repo);

        assertThat(selection.ref()).isEqualTo("develop");
        assertThat(selection.source()).isEqualTo("fallback branch develop");
    }

    @Test
    void reportsWhenNoBaseCanBeDetected() throws Exception {
        Path repo = initRepo("dev");

        assertThatThrownBy(() -> new BaseRefDetector(false, Collections.emptyMap()).detect(repo))
            .isInstanceOf(UserFacingException.class)
            .hasMessageContaining("No base ref could be detected")
            .hasMessageContaining("origin/HEAD")
            .hasMessageContaining("develop")
            .hasMessageContaining("spring-api-diff check --base origin/main");
    }

    private Path initRepo(String branch) throws Exception {
        Path repo = tempDir.resolve("repo-" + branch.replace('/', '-'));
        Files.createDirectories(repo);
        git(repo, "init", "-b", branch);
        git(repo, "config", "user.name", "test");
        git(repo, "config", "user.email", "test@example.com");
        Files.write(repo.resolve("file.txt"), "content".getBytes(StandardCharsets.UTF_8));
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial");
        return repo;
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
}
