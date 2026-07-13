package io.github.springapidiff.diff;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.scanner.ProjectScanner;
import java.nio.file.Paths;
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
}
