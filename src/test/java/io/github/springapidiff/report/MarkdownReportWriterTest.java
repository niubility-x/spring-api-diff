package io.github.springapidiff.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.ChangeType;
import io.github.springapidiff.diff.Severity;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownReportWriterTest {
    @Test
    void writesGroupedMarkdownReport() {
        Change breaking = new Change(
            Severity.BREAKING,
            ChangeType.RESPONSE_FIELD_REMOVED,
            "GET /api/users/{id}",
            "response.email",
            "String",
            null,
            "Response field 'email' was removed.");

        String markdown = new MarkdownReportWriter().write(Collections.singletonList(breaking));

        assertThat(markdown).contains("# API Compatibility Report");
        assertThat(markdown).contains("- Breaking changes: 1");
        assertThat(markdown).contains("### GET /api/users/{id}");
        assertThat(markdown).contains("Response field 'email' was removed.");
        assertThat(markdown).contains("**Impact:** Existing clients that read this response field");
        assertThat(markdown).contains("**Suggestion:** Keep returning the field as deprecated");
    }
}
