package io.github.springapidiff.io;

import io.github.springapidiff.model.ApiSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SnapshotWriter {
    public void write(ApiSnapshot snapshot, Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        SnapshotJson.mapper().writeValue(path.toFile(), snapshot);
    }
}
