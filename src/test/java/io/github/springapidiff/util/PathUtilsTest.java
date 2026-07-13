package io.github.springapidiff.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PathUtilsTest {
    @Test
    void joinsAndNormalizesPaths() {
        assertThat(PathUtils.join("/api/users/", "/{id}")).isEqualTo("/api/users/{id}");
        assertThat(PathUtils.join("api", "users")).isEqualTo("/api/users");
        assertThat(PathUtils.normalize("//api///users/")).isEqualTo("/api/users");
    }
}
