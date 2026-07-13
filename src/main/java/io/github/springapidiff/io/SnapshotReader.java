package io.github.springapidiff.io;

import io.github.springapidiff.model.ApiSnapshot;
import java.io.IOException;
import java.nio.file.Path;

public class SnapshotReader {
    public ApiSnapshot read(Path path) throws IOException {
        return SnapshotJson.mapper().readValue(path.toFile(), ApiSnapshot.class);
    }
}
