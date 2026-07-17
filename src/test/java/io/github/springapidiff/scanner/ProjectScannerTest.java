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

    @Test
    void resolvesConstantMappingPathsAndSkipsUnresolvedMappings() throws Exception {
        Path fixture = Paths.get("src/test/resources/fixtures/constants");

        List<Endpoint> endpoints = new ProjectScanner().scan(fixture, Collections.emptyList(), Collections.emptyList()).endpoints();

        assertThat(endpoints).extracting(Endpoint::id).containsExactly(
            "GET /api/admin/lookup",
            "GET /api/admin/search",
            "GET /api/admin/{id}",
            "GET /api/users/lookup",
            "GET /api/users/search",
            "GET /api/users/{id}",
            "POST /api/admin/lookup",
            "POST /api/admin/search",
            "POST /api/users/lookup",
            "POST /api/users/search");
        assertThat(endpoints).extracting(Endpoint::path)
            .doesNotContain("/", "/unknown", "/leaked");
    }

    @Test
    void inheritsDirectInterfaceControllerContractsAndSkipsConflicts() throws Exception {
        Path fixture = Paths.get("src/test/resources/fixtures/interface-contracts");

        List<Endpoint> endpoints = new ProjectScanner().scan(fixture, Collections.emptyList(), Collections.emptyList()).endpoints();

        assertThat(endpoints).extracting(Endpoint::id).containsExactly(
            "GET /api/contracts/by-id",
            "GET /api/contracts/by-name",
            "GET /api/contracts/{id}",
            "GET /api/shared/same",
            "POST /api/contracts/body",
            "POST /api/contracts/custom");

        Endpoint inherited = endpoint(endpoints, "GET /api/contracts/{id}");
        assertThat(inherited.request().pathVariables()).extracting("name", "type", "required")
            .containsExactly(tuple("contractId", "Long", true));
        assertThat(inherited.request().queryParams()).extracting("name", "type", "required")
            .containsExactly(tuple("filter", "String", false));
        assertThat(inherited.response().fields()).extracting("name", "type")
            .containsExactly(tuple("id", "Long"), tuple("name", "String"));

        Endpoint body = endpoint(endpoints, "POST /api/contracts/body");
        assertThat(body.request().body().fields()).extracting("name", "required")
            .containsExactly(tuple("name", false));

        Endpoint overridden = endpoint(endpoints, "POST /api/contracts/custom");
        assertThat(overridden.request().queryParams()).extracting("name", "required")
            .containsExactly(tuple("implementationName", false));

        assertThat(endpoints).extracting(Endpoint::path)
            .doesNotContain(
                "/api/shared/mapping-conflict-a",
                "/api/shared/mapping-conflict-b",
                "/api/shared/parameter-conflict",
                "/api/left/leaked",
                "/api/right/leaked");
    }

    private Endpoint endpoint(List<Endpoint> endpoints, String id) {
        return endpoints.stream()
            .filter(endpoint -> endpoint.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError(id + " endpoint not found"));
    }
}
