package io.github.springapidiff.io;

import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.validation.SnapshotValidator;
import java.io.IOException;
import java.nio.file.Path;

public class SnapshotReader {
    public ApiSnapshot read(Path path) throws IOException {
        ApiSnapshot snapshot = SnapshotJson.mapper().readValue(path.toFile(), ApiSnapshot.class);
        String source = path.toAbsolutePath().normalize().toString();
        new SnapshotValidator().validate(snapshot, endpoint -> source);
        return snapshot;
    }
}
