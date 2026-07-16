package io.github.springapidiff.cli;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.model.ApiSnapshot;
import java.nio.file.Path;
import java.util.List;

class CheckResult {
    private final List<Change> changes;
    private final ApiSnapshot oldSnapshot;
    private final ApiSnapshot newSnapshot;
    private final Path headCheckoutPath;
    private final List<Path> headScanPaths;

    CheckResult(
        List<Change> changes,
        ApiSnapshot oldSnapshot,
        ApiSnapshot newSnapshot,
        Path headCheckoutPath,
        List<Path> headScanPaths) {
        this.changes = changes;
        this.oldSnapshot = oldSnapshot;
        this.newSnapshot = newSnapshot;
        this.headCheckoutPath = headCheckoutPath;
        this.headScanPaths = headScanPaths;
    }

    List<Change> changes() {
        return changes;
    }

    ApiSnapshot oldSnapshot() {
        return oldSnapshot;
    }

    ApiSnapshot newSnapshot() {
        return newSnapshot;
    }

    Path headCheckoutPath() {
        return headCheckoutPath;
    }

    List<Path> headScanPaths() {
        return headScanPaths;
    }
}
