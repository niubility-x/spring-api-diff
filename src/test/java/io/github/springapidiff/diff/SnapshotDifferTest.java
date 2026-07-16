package io.github.springapidiff.diff;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.springapidiff.model.ApiBody;
import io.github.springapidiff.model.ApiField;
import io.github.springapidiff.model.ApiParameter;
import io.github.springapidiff.model.ApiRequest;
import io.github.springapidiff.model.ApiResponse;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.model.Endpoint;
import io.github.springapidiff.model.ProjectInfo;
import io.github.springapidiff.scanner.ProjectScanner;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotDifferTest {
    @Test
    void detectsBreakingWarningAndNonBreakingChanges() throws Exception {
        ApiSnapshot oldSnapshot = new ProjectScanner().scan(Paths.get("src/test/resources/fixtures/demo-v1"), Collections.emptyList(), Collections.emptyList());
        ApiSnapshot newSnapshot = new ProjectScanner().scan(Paths.get("src/test/resources/fixtures/demo-v2"), Collections.emptyList(), Collections.emptyList());

        List<Change> changes = new SnapshotDiffer().diff(oldSnapshot, newSnapshot);

        assertThat(changes).extracting(Change::type)
            .contains(
                ChangeType.RESPONSE_FIELD_REMOVED,
                ChangeType.RESPONSE_FIELD_ADDED,
                ChangeType.REQUEST_BODY_FIELD_TYPE_CHANGED,
                ChangeType.REQUIRED_REQUEST_BODY_FIELD_ADDED);
        assertThat(changes)
            .filteredOn(change -> change.type() == ChangeType.RESPONSE_FIELD_REMOVED)
            .filteredOn(change -> change.endpoint().equals("GET /api/users/{id}"))
            .singleElement()
            .extracting(Change::severity, Change::path)
            .containsExactly(Severity.BREAKING, "response.email");
    }

    @Test
    void detectsExistingQueryParameterBecomingRequired() {
        ApiSnapshot oldSnapshot = snapshot(
            Collections.singletonList(new ApiParameter("filter", "String", false)),
            Collections.emptyList());
        ApiSnapshot newSnapshot = snapshot(
            Collections.singletonList(new ApiParameter("filter", "String", true)),
            Collections.emptyList());

        assertThat(new SnapshotDiffer().diff(oldSnapshot, newSnapshot))
            .singleElement()
            .extracting(Change::severity, Change::type, Change::path, Change::oldValue, Change::newValue)
            .containsExactly(
                Severity.BREAKING,
                ChangeType.QUERY_PARAM_BECAME_REQUIRED,
                "request.queryParams.filter",
                "optional",
                "required");
    }

    @Test
    void detectsExistingRequestBodyFieldBecomingRequired() {
        ApiSnapshot oldSnapshot = snapshot(
            Collections.emptyList(),
            Collections.singletonList(new ApiField("name", "String", false)));
        ApiSnapshot newSnapshot = snapshot(
            Collections.emptyList(),
            Collections.singletonList(new ApiField("name", "String", true)));

        assertThat(new SnapshotDiffer().diff(oldSnapshot, newSnapshot))
            .singleElement()
            .extracting(Change::severity, Change::type, Change::path, Change::oldValue, Change::newValue)
            .containsExactly(
                Severity.BREAKING,
                ChangeType.REQUEST_BODY_FIELD_BECAME_REQUIRED,
                "request.body.name",
                "optional",
                "required");
    }

    @Test
    void reportsTypeAndRequiredChangesIndependently() {
        ApiSnapshot oldSnapshot = snapshot(
            Collections.singletonList(new ApiParameter("limit", "String", false)),
            Collections.emptyList());
        ApiSnapshot newSnapshot = snapshot(
            Collections.singletonList(new ApiParameter("limit", "Integer", true)),
            Collections.emptyList());

        assertThat(new SnapshotDiffer().diff(oldSnapshot, newSnapshot)).extracting(Change::type)
            .containsExactly(
                ChangeType.QUERY_PARAM_TYPE_CHANGED,
                ChangeType.QUERY_PARAM_BECAME_REQUIRED);
    }

    @Test
    void ignoresRequiredBecomingOptional() {
        ApiSnapshot oldSnapshot = snapshot(
            Collections.singletonList(new ApiParameter("filter", "String", true)),
            Collections.singletonList(new ApiField("name", "String", true)));
        ApiSnapshot newSnapshot = snapshot(
            Collections.singletonList(new ApiParameter("filter", "String", false)),
            Collections.singletonList(new ApiField("name", "String", false)));

        assertThat(new SnapshotDiffer().diff(oldSnapshot, newSnapshot)).isEmpty();
    }

    private ApiSnapshot snapshot(List<ApiParameter> queryParams, List<ApiField> bodyFields) {
        ApiBody body = bodyFields.isEmpty() ? null : new ApiBody("Request", bodyFields);
        Endpoint endpoint = new Endpoint(
            "POST /users",
            "POST",
            "/users",
            "UserController",
            "create",
            new ApiRequest(Collections.emptyList(), queryParams, body),
            new ApiResponse("User", Arrays.asList(new ApiField("id", "Long", true))));
        return new ApiSnapshot(
            "1",
            Instant.EPOCH,
            new ProjectInfo("test", "8", "unknown"),
            Collections.singletonList(endpoint));
    }
}
