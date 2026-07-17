package io.github.springapidiff.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.scanner.ProjectScanner;
import io.github.springapidiff.validation.DuplicateEndpointIdException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotIoTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsSnapshotJson() throws Exception {
        ApiSnapshot snapshot = new ProjectScanner().scan(Paths.get("src/test/resources/fixtures/demo-v1"), Collections.emptyList(), Collections.emptyList());
        Path output = tempDir.resolve("snapshot.json");

        new SnapshotWriter().write(snapshot, output);
        ApiSnapshot read = new SnapshotReader().read(output);

        assertThat(new String(Files.readAllBytes(output), StandardCharsets.UTF_8)).contains("\"endpoints\"");
        assertThat(read.endpoints()).hasSameSizeAs(snapshot.endpoints());
        assertThat(read.endpoints()).extracting("id").containsExactly("GET /api/users/{id}", "POST /api/users");
    }

    @Test
    void rejectsDuplicateEndpointIdsInSnapshotJson() throws Exception {
        Path input = tempDir.resolve("duplicate.json");
        Files.write(input, ("{\n"
            + "  \"version\": \"1\",\n"
            + "  \"endpoints\": [\n"
            + endpointJson("GET /users", "FirstController", "first") + ",\n"
            + endpointJson("GET /users", "SecondController", "second") + "\n"
            + "  ]\n"
            + "}").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new SnapshotReader().read(input))
            .isInstanceOf(DuplicateEndpointIdException.class)
            .hasMessageContaining(
                input.toAbsolutePath().normalize().toString(),
                "FirstController#first",
                "SecondController#second");
    }

    private String endpointJson(String id, String controller, String handler) {
        return "    {\"id\": \"" + id + "\", \"method\": \"GET\", \"path\": \"/users\", "
            + "\"controller\": \"" + controller + "\", \"handler\": \"" + handler + "\", "
            + "\"request\": {\"pathVariables\": [], \"queryParams\": []}, "
            + "\"response\": {\"type\": \"void\", \"fields\": []}}";
    }
}
