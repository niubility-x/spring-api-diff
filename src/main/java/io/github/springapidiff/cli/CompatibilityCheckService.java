package io.github.springapidiff.cli;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.SnapshotDiffer;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.model.Endpoint;
import io.github.springapidiff.model.ProjectInfo;
import io.github.springapidiff.scanner.ProjectScanner;
import io.github.springapidiff.validation.SnapshotValidator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

class CompatibilityCheckService {
    private final GitClient gitClient;
    private final ProjectScanner scanner;
    private final SnapshotDiffer snapshotDiffer;

    CompatibilityCheckService(GitClient gitClient) {
        this(gitClient, new ProjectScanner(), new SnapshotDiffer());
    }

    CompatibilityCheckService(GitClient gitClient, ProjectScanner scanner, SnapshotDiffer snapshotDiffer) {
        this.gitClient = gitClient;
        this.scanner = scanner;
        this.snapshotDiffer = snapshotDiffer;
    }

    CheckResult check(CheckRequest request, ProgressListener progressListener) throws IOException, InterruptedException {
        GitCheckout baseCheckout = null;
        GitCheckout headCheckout = null;
        try {
            progressListener.onProgress(2, 7, "Preparing base source...");
            baseCheckout = checkoutRef(request.repoRoot(), request.baseRef(), "base");

            progressListener.onProgress(3, 7, "Preparing head source...");
            headCheckout = request.worktree()
                ? GitCheckout.current(request.repoRoot())
                : checkoutRef(request.repoRoot(), request.headRef(), "head");

            progressListener.onProgress(4, 7, "Resolving scan paths...");
            ScanPathResolver scanPathResolver = new ScanPathResolver(
                request.module(), request.includeModules(), request.excludeModules());
            List<Path> baseScanPaths = scanPathResolver.resolve(baseCheckout.path());
            List<Path> headScanPaths = scanPathResolver.resolve(headCheckout.path());

            progressListener.onProgress(5, 7, "Scanning base APIs...");
            ApiSnapshot oldSnapshot = scanSnapshot(
                scanPathResolver,
                baseCheckout.path(),
                baseScanPaths,
                request.baseRef(),
                request.includes(),
                request.excludes());

            progressListener.onProgress(6, 7, "Scanning head APIs...");
            ApiSnapshot newSnapshot = scanSnapshot(
                scanPathResolver,
                headCheckout.path(),
                headScanPaths,
                request.headRef(),
                request.includes(),
                request.excludes());

            progressListener.onProgress(7, 7, "Comparing snapshots...");
            List<Change> changes = new ChangeFilter(request.ignoreEndpoints())
                .filter(snapshotDiffer.diff(oldSnapshot, newSnapshot));
            return new CheckResult(changes, oldSnapshot, newSnapshot, headCheckout.path(), headScanPaths);
        } finally {
            closeQuietly(headCheckout);
            closeQuietly(baseCheckout);
        }
    }

    private GitCheckout checkoutRef(Path repoRoot, String ref, String label) throws IOException, InterruptedException {
        String commit;
        try {
            commit = gitClient.output(repoRoot, "rev-parse", "--verify", ref + "^{commit}").trim();
        } catch (IOException e) {
            throw new UserFacingException(
                "Git ref not found: " + ref + "\n"
                    + "Check the branch, tag, or commit exists locally. If it is a remote branch, run git fetch first.");
        }
        return GitCheckout.temporary(gitClient, repoRoot, commit, label);
    }

    private ApiSnapshot scanSnapshot(
        ScanPathResolver scanPathResolver,
        Path checkoutRoot,
        List<Path> paths,
        String ref,
        List<String> includes,
        List<String> excludes) throws IOException {
        List<ApiSnapshot> snapshots = new ArrayList<>();
        Map<Endpoint, String> sources = new IdentityHashMap<>();
        for (Path path : paths) {
            String source = modulePath(checkoutRoot, path);
            try {
                ApiSnapshot snapshot = scanner.scan(path, includes, excludes, source);
                snapshots.add(snapshot);
                for (Endpoint endpoint : snapshot.endpoints()) {
                    sources.put(endpoint, source);
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("Java source root not found")) {
                    throw scanPathResolver.sourceRootNotFound(ref);
                }
                throw e;
            }
        }
        if (snapshots.size() == 1) {
            return snapshots.get(0);
        }
        List<Endpoint> endpoints = new ArrayList<>();
        for (ApiSnapshot snapshot : snapshots) {
            endpoints.addAll(snapshot.endpoints());
        }
        Collections.sort(endpoints, java.util.Comparator.comparing(Endpoint::id));
        ApiSnapshot aggregate = new ApiSnapshot(
            "1",
            snapshots.get(0).generatedAt(),
            new ProjectInfo(paths.get(0).getParent().getFileName().toString(), "unknown", "unknown"),
            endpoints);
        new SnapshotValidator().validate(aggregate, sources::get);
        return aggregate;
    }

    private String modulePath(Path checkoutRoot, Path path) {
        Path relative = checkoutRoot.relativize(path).normalize();
        String value = relative.toString().replace('\\', '/');
        return value.isEmpty() ? "." : value;
    }

    private void closeQuietly(GitCheckout checkout) {
        if (checkout == null) {
            return;
        }
        try {
            checkout.close();
        } catch (IOException | InterruptedException ignored) {
            // Cleanup failures should not hide the actual check result.
        }
    }
}
