package io.github.springapidiff.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanPathResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsProjectRootWhenSourceRootExists() throws Exception {
        Files.createDirectories(tempDir.resolve("src/main/java"));

        List<Path> paths = resolver(null).resolve(tempDir);

        assertThat(paths).containsExactly(tempDir);
    }

    @Test
    void returnsSpecifiedModulePath() throws Exception {
        Path module = tempDir.resolve("user-service");
        Files.createDirectories(module.resolve("src/main/java"));

        List<Path> paths = resolver(Paths.get("user-service")).resolve(tempDir);

        assertThat(paths).containsExactly(module);
    }

    @Test
    void discoversNestedModules() throws Exception {
        Path userService = tempDir.resolve("services/user-service");
        Path orderService = tempDir.resolve("services/order-service");
        Files.createDirectories(userService.resolve("src/main/java"));
        Files.createDirectories(orderService.resolve("src/main/java"));

        List<Path> paths = resolver(null).resolve(tempDir);

        assertThat(paths).containsExactly(orderService, userService);
    }

    @Test
    void reportsMissingSpecifiedModule() {
        assertThatThrownBy(() -> resolver(Paths.get("user-service")).resolve(tempDir))
            .isInstanceOf(UserFacingException.class)
            .hasMessageContaining("模块目录不存在：user-service")
            .hasMessageContaining("--module 是相对于 Git 仓库根目录的路径");
    }

    @Test
    void returnsCheckoutRootWhenNoSourceRootExists() throws Exception {
        Files.write(tempDir.resolve("pom.xml"), "<project></project>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        List<Path> paths = resolver(null).resolve(tempDir);

        assertThat(paths).containsExactly(tempDir);
    }

    @Test
    void filtersDiscoveredModulesByIncludeGlob() throws Exception {
        Path userService = tempDir.resolve("services/user-service");
        Path orderService = tempDir.resolve("services/order-service");
        Files.createDirectories(userService.resolve("src/main/java"));
        Files.createDirectories(orderService.resolve("src/main/java"));

        List<Path> paths = new ScanPathResolver(null, Arrays.asList("services/user-*"), Collections.emptyList()).resolve(tempDir);

        assertThat(paths).containsExactly(userService);
    }

    @Test
    void filtersDiscoveredModulesByExcludeGlob() throws Exception {
        Path userService = tempDir.resolve("services/user-service");
        Path orderService = tempDir.resolve("services/order-service");
        Files.createDirectories(userService.resolve("src/main/java"));
        Files.createDirectories(orderService.resolve("src/main/java"));

        List<Path> paths = new ScanPathResolver(null, Collections.emptyList(), Arrays.asList("services/order-*")).resolve(tempDir);

        assertThat(paths).containsExactly(userService);
    }

    @Test
    void ignoresModuleFiltersWhenExplicitModuleIsSpecified() throws Exception {
        Path userService = tempDir.resolve("services/user-service");
        Files.createDirectories(userService.resolve("src/main/java"));

        List<Path> paths = new ScanPathResolver(Paths.get("services/user-service"), Arrays.asList("other-*"), Arrays.asList("services/*")).resolve(tempDir);

        assertThat(paths).containsExactly(userService);
    }

    private ScanPathResolver resolver(Path module) {
        return new ScanPathResolver(module, Collections.emptyList(), Collections.emptyList());
    }
}
