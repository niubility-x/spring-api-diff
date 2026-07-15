package io.github.springapidiff.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.ChangeType;
import io.github.springapidiff.diff.Severity;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeFilterTest {
    @Test
    void filtersEndpointByExactPattern() {
        Change ignored = change("POST /sdipReport/api/docTpl/getTableList");
        Change kept = change("GET /users/{id}");

        List<Change> changes = new ChangeFilter(Arrays.asList("POST /sdipReport/api/docTpl/getTableList"))
            .filter(Arrays.asList(ignored, kept));

        assertThat(changes).containsExactly(kept);
    }

    @Test
    void filtersEndpointByGlobPattern() {
        Change ignored = change("GET /actuator/health");
        Change kept = change("GET /api/users");

        List<Change> changes = new ChangeFilter(Arrays.asList("GET /actuator/**"))
            .filter(Arrays.asList(ignored, kept));

        assertThat(changes).containsExactly(kept);
    }

    @Test
    void returnsOriginalChangesWhenNoIgnorePatternExists() {
        Change change = change("GET /api/users");
        List<Change> changes = Arrays.asList(change);

        assertThat(new ChangeFilter(Collections.emptyList()).filter(changes)).isSameAs(changes);
    }

    private Change change(String endpoint) {
        return new Change(Severity.BREAKING, ChangeType.ENDPOINT_REMOVED, endpoint, "endpoint", endpoint, null, "Endpoint was removed.");
    }
}
