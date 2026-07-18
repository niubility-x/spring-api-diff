package io.github.springapidiff.io;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.validation.InvalidSnapshotException;
import io.github.springapidiff.validation.SnapshotValidator;
import java.io.IOException;
import java.nio.file.Path;

public class SnapshotReader {
    public ApiSnapshot read(Path path) throws IOException {
        JsonNode root = SnapshotJson.mapper().readTree(path.toFile());
        if (root == null || root.isNull()) {
            throw new InvalidSnapshotException("Invalid snapshot: snapshot must not be null.");
        }
        if (!root.has("endpoints") || !root.get("endpoints").isArray()) {
            throw new InvalidSnapshotException("Invalid snapshot: endpoints must be an array.");
        }
        ApiSnapshot snapshot = SnapshotJson.mapper().treeToValue(root, ApiSnapshot.class);
        String source = path.toAbsolutePath().normalize().toString();
        new SnapshotValidator().validate(snapshot, endpoint -> source);
        return snapshot;
    }
}
