package io.github.springapidiff.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.ChangeType;
import io.github.springapidiff.diff.Severity;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class JsonReportWriterTest {
    @Test
    void writesMachineReadableJsonReport() throws Exception {
        Change breaking = new Change(
            Severity.BREAKING,
            ChangeType.RESPONSE_FIELD_REMOVED,
            "GET /api/users/{id}",
            "response.email",
            "String",
            null,
            "Response field 'email' was removed.");

        String json = new JsonReportWriter().write(Collections.singletonList(breaking));

        assertThat(json).contains("\"summary\"", "\"breaking\" : 1", "\"severity\" : \"BREAKING\"");
        assertThat(json).contains("\"endpoint\" : \"GET /api/users/{id}\"");
        assertThat(json).contains("\"impact\"", "\"suggestion\"");
    }
}
