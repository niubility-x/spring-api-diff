package io.github.springapidiff.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.springapidiff.diff.ChangeType;
import io.github.springapidiff.model.Endpoint;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectScannerTest {
    @Test
    void scansSpringControllerAndDtoFields() throws Exception {
        Path fixture = Paths.get("src/test/resources/fixtures/demo-v1");

        List<Endpoint> endpoints = new ProjectScanner().scan(fixture, Collections.emptyList(), Collections.emptyList()).endpoints();

        assertThat(endpoints).extracting(Endpoint::id)
            .containsExactly("GET /api/users/{id}", "POST /api/users");
        Endpoint getUser = endpoints.stream()
            .filter(endpoint -> endpoint.id().equals("GET /api/users/{id}"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("GET endpoint not found"));
        assertThat(getUser.request().pathVariables()).extracting("name", "type", "required")
            .containsExactly(org.assertj.core.groups.Tuple.tuple("id", "Long", true));
        assertThat(getUser.request().queryParams()).extracting("name", "type", "required")
            .containsExactly(org.assertj.core.groups.Tuple.tuple("status", "String", false));
        assertThat(getUser.response().fields()).extracting("name", "type")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("id", "Long"),
                org.assertj.core.groups.Tuple.tuple("user_name", "String"),
                org.assertj.core.groups.Tuple.tuple("email", "String"));
    }
}
