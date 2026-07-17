package io.github.springapidiff.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.springapidiff.model.ApiRequest;
import io.github.springapidiff.model.ApiResponse;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.model.Endpoint;
import io.github.springapidiff.model.ProjectInfo;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SnapshotValidatorTest {
    private final SnapshotValidator validator = new SnapshotValidator();

    @Test
    void acceptsUniqueEndpointIds() {
        ApiSnapshot snapshot = snapshot(
            endpoint("GET /users", "UserController", "list"),
            endpoint("POST /users", "UserController", "create"));

        assertThatCode(() -> validator.validate(snapshot)).doesNotThrowAnyException();
    }

    @Test
    void reportsAllConflictsInDeterministicOrderWithSources() {
        Endpoint secondPost = endpoint("POST /users", "ZController", "create");
        Endpoint firstGet = endpoint("GET /users", "BController", "list");
        Endpoint secondGet = endpoint("GET /users", "AController", "list");
        Endpoint firstPost = endpoint("POST /users", "AController", "create");
        ApiSnapshot snapshot = snapshot(secondPost, firstGet, secondGet, firstPost);

        assertThatThrownBy(() -> validator.validate(snapshot, endpoint ->
            endpoint.controller().startsWith("A") ? "module-a" : "module-z"))
            .isInstanceOf(DuplicateEndpointIdException.class)
            .hasMessage(
                "Duplicate endpoint IDs detected:\n"
                    + "- GET /users\n"
                    + "  - [module-a] AController#list\n"
                    + "  - [module-z] BController#list\n"
                    + "- POST /users\n"
                    + "  - [module-a] AController#create\n"
                    + "  - [module-z] ZController#create\n"
                    + "Each HTTP method and path must identify exactly one endpoint.");
    }

    @Test
    void preservesIdenticalDuplicateOccurrences() {
        ApiSnapshot snapshot = snapshot(
            endpoint("GET /users", "UserController", "list"),
            endpoint("GET /users", "UserController", "list"));

        assertThatThrownBy(() -> validator.validate(snapshot))
            .isInstanceOf(DuplicateEndpointIdException.class)
            .hasMessageContaining(
                "  - UserController#list\n"
                    + "  - UserController#list");
    }

    private ApiSnapshot snapshot(Endpoint... endpoints) {
        return new ApiSnapshot(
            "1",
            Instant.EPOCH,
            new ProjectInfo("test", "8", "unknown"),
            Arrays.asList(endpoints));
    }

    private Endpoint endpoint(String id, String controller, String handler) {
        int separator = id.indexOf(' ');
        return new Endpoint(
            id,
            id.substring(0, separator),
            id.substring(separator + 1),
            controller,
            handler,
            new ApiRequest(Collections.emptyList(), Collections.emptyList(), null),
            new ApiResponse("void", Collections.emptyList()));
    }
}
