package io.github.springapidiff.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

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
        Endpoint getUser = endpoint(endpoints, "GET /api/users/{id}");
        assertThat(getUser.request().pathVariables()).extracting("name", "type", "required")
            .containsExactly(tuple("id", "Long", true));
        assertThat(getUser.request().queryParams()).extracting("name", "type", "required")
            .containsExactly(tuple("status", "String", false));
        assertThat(getUser.response().fields()).extracting("name", "type")
            .containsExactly(
                tuple("id", "Long"),
                tuple("user_name", "String"),
                tuple("email", "String"));
    }

    @Test
    void scansAdvancedSpringMappingsWrappersAndValidationRequiredFields() throws Exception {
        Path fixture = Paths.get("src/test/resources/fixtures/advanced");

        List<Endpoint> endpoints = new ProjectScanner().scan(fixture, Collections.emptyList(), Collections.emptyList()).endpoints();

        assertThat(endpoints).extracting(Endpoint::id).contains(
            "GET /api/advanced/{id}",
            "GET /api/advanced/by-id/{id}",
            "POST /api/advanced",
            "PUT /api/advanced/{id}",
            "PATCH /api/advanced/{id}",
            "DELETE /api/advanced/{id}",
            "GET /api/advanced/search",
            "POST /api/advanced/search",
            "GET /api/advanced/paged",
            "GET /internal/advanced/{id}",
            "POST /internal/advanced/search",
            "ANY /api/advanced/any-method",
            "ANY /internal/advanced/any-method");

        Endpoint anyMethod = endpoint(endpoints, "ANY /api/advanced/any-method");
        assertThat(anyMethod.method()).isEqualTo("ANY");

        Endpoint get = endpoint(endpoints, "GET /api/advanced/{id}");
        assertThat(get.request().queryParams()).extracting("name", "type", "required")
            .containsExactly(tuple("status", "String", false));
        assertThat(get.response().type()).isEqualTo("ResponseEntity<AdvancedResponse>");
        assertThat(get.response().fields()).extracting("name", "type")
            .containsExactly(tuple("traceId", "String"), tuple("id", "Long"), tuple("display_name", "String"));

        Endpoint paged = endpoint(endpoints, "GET /api/advanced/paged");
        assertThat(paged.response().type()).isEqualTo("Result<Page<AdvancedResponse>>");
        assertThat(paged.response().fields()).extracting("name", "type")
            .containsExactly(tuple("traceId", "String"), tuple("id", "Long"), tuple("display_name", "String"));

        Endpoint post = endpoint(endpoints, "POST /api/advanced");
        assertThat(post.request().body().fields()).extracting("name", "type", "required")
            .containsExactly(
                tuple("name", "String", true),
                tuple("email", "String", true),
                tuple("age", "Integer", true),
                tuple("nickname", "String", false));

        Endpoint put = endpoint(endpoints, "PUT /api/advanced/{id}");
        assertThat(put.request().body().fields()).extracting("name", "required")
            .containsExactly(
                tuple("name", false),
                tuple("email", false),
                tuple("age", false),
                tuple("nickname", false));
    }

    private Endpoint endpoint(List<Endpoint> endpoints, String id) {
        return endpoints.stream()
            .filter(endpoint -> endpoint.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError(id + " endpoint not found"));
    }
}
