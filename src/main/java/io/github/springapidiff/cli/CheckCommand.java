package io.github.springapidiff.cli;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.Severity;
import io.github.springapidiff.diff.SnapshotDiffer;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.report.ChangeAdvice;
import io.github.springapidiff.report.JsonReportWriter;
import io.github.springapidiff.report.MarkdownReportWriter;
import io.github.springapidiff.scanner.ProjectScanner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "check",
    description = "Check Spring REST API compatibility between Git states.",
    footer = {
        "Examples:",
        "  spring-api-diff check --base origin/main --head HEAD",
        "  spring-api-diff check --base origin/main --head HEAD --module user-service",
        "  spring-api-diff check --fetch --fail-on-breaking"
    })
public class CheckCommand implements Callable<Integer> {
    @Option(names = "--repo", description = "Git repository path. Defaults to current directory.")
    private Path repo = Paths.get(".");

    @Option(names = "--base", description = "Base Git ref. Defaults to origin/main, main, then master.")
    private String base;

    @Option(names = "--head", description = "Head Git ref. Defaults to HEAD when the worktree is clean.")
    private String head;

    @Option(names = "--worktree", description = "Compare the base ref with the current working tree, including uncommitted changes.")
    private Boolean worktree;

    @Option(names = "--module", description = "Module path relative to the Git repository root.")
    private Path module;

    @Option(names = "--include-module", description = "Only include auto-discovered modules whose path matches this glob.")
    private List<String> includeModules = new ArrayList<>();

    @Option(names = "--exclude-module", description = "Exclude auto-discovered modules whose path matches this glob.")
    private List<String> excludeModules = new ArrayList<>();

    @Option(names = "--report", description = "Markdown report output file. Prints a summary to stdout either way.")
    private Path report;

    @Option(names = "--fail-on-breaking", description = "Return non-zero when breaking changes are found.")
    private Boolean failOnBreaking;

    @Option(names = "--fetch", description = "Fetch the CI target branch when it is missing locally.")
    private Boolean fetch;

    @Option(names = "--include", description = "Only include controllers whose package starts with this value.")
    private List<String> includes = new ArrayList<>();

    @Option(names = "--exclude", description = "Exclude controllers whose package starts with this value.")
    private List<String> excludes = new ArrayList<>();

    @Option(names = "--ignore-endpoint", description = "Ignore changes for endpoints matching this glob, for example 'POST /internal/**'.")
    private List<String> ignoreEndpoints = new ArrayList<>();

    @Option(names = "--format", defaultValue = "markdown", description = "Report format. Supported: markdown, json.")
    private String format;

    @Option(names = "--quiet", description = "Suppress progress output.")
    private Boolean quiet;

    @Override
    public Integer call() throws Exception {
        try {
            return run();
        } catch (UserFacingException e) {
            System.err.println(e.getMessage());
            return 2;
        }
    }

    private Integer run() throws Exception {
        String normalizedFormat = format.toLowerCase(Locale.ROOT);
        if (!"markdown".equals(normalizedFormat) && !"json".equals(normalizedFormat)) {
            throw new UserFacingException("Unsupported report format: " + format + "\nSupported formats: markdown, json");
        }

        Path repoRoot = resolveRepoRoot(repo.toAbsolutePath().normalize());
        CheckConfig config = new CheckConfigLoader().load(repoRoot);
        String effectiveBase = firstNonBlank(base, config.base());
        String effectiveHead = firstNonBlank(head, config.head());
        Boolean effectiveWorktree = firstNonNull(worktree, config.worktree(), Boolean.FALSE);
        Path effectiveModule = firstNonBlankPath(module, config.module());
        List<String> effectiveIncludeModules = firstNonEmpty(includeModules, config.modules().include());
        List<String> effectiveExcludeModules = firstNonEmpty(excludeModules, config.modules().exclude());
        Path effectiveReport = firstNonBlankPath(report, config.report());
        boolean effectiveFailOnBreaking = firstNonNull(failOnBreaking, config.failOnBreaking(), Boolean.FALSE);
        boolean effectiveFetch = firstNonNull(fetch, config.fetch(), Boolean.FALSE);
        List<String> effectiveIncludes = firstNonEmpty(includes, config.include());
        List<String> effectiveExcludes = firstNonEmpty(excludes, config.exclude());
        List<String> effectiveIgnoreEndpoints = firstNonEmpty(ignoreEndpoints, config.ignore().endpoints());
        boolean effectiveQuiet = firstNonNull(quiet, config.quiet(), Boolean.FALSE);

        if (effectiveWorktree && effectiveHead != null) {
            throw new UserFacingException("Use either --worktree or --head, not both.\nExample: spring-api-diff check --base main --worktree");
        }

        progress(effectiveQuiet, 1, 7, "Selecting Git refs...");
        BaseSelection baseSelection = effectiveBase == null
            ? new BaseRefDetector(effectiveFetch, environment()).detect(repoRoot)
            : new BaseSelection(effectiveBase, base == null ? "config base" : "explicit --base");
        String baseRef = baseSelection.ref();
        boolean useWorktree = effectiveWorktree || (effectiveHead == null && hasWorktreeChanges(repoRoot));
        String headRef = useWorktree ? "worktree" : (effectiveHead == null ? "HEAD" : effectiveHead);

        GitCheckout baseCheckout = null;
        GitCheckout headCheckout = null;
        try {
            progress(effectiveQuiet, 2, 7, "Preparing base source...");
            baseCheckout = checkoutRef(repoRoot, baseRef, "base");

            progress(effectiveQuiet, 3, 7, "Preparing head source...");
            headCheckout = useWorktree ? GitCheckout.current(repoRoot) : checkoutRef(repoRoot, headRef, "head");

            progress(effectiveQuiet, 4, 7, "Resolving scan paths...");
            ScanPathResolver scanPathResolver = new ScanPathResolver(effectiveModule, effectiveIncludeModules, effectiveExcludeModules);
            List<Path> baseScanPaths = scanPathResolver.resolve(baseCheckout.path());
            List<Path> headScanPaths = scanPathResolver.resolve(headCheckout.path());
            ProjectScanner scanner = new ProjectScanner();

            progress(effectiveQuiet, 5, 7, "Scanning base APIs...");
            ApiSnapshot oldSnapshot = scanSnapshot(scanner, scanPathResolver, baseScanPaths, baseRef, effectiveIncludes, effectiveExcludes);

            progress(effectiveQuiet, 6, 7, "Scanning head APIs...");
            ApiSnapshot newSnapshot = scanSnapshot(scanner, scanPathResolver, headScanPaths, headRef, effectiveIncludes, effectiveExcludes);

            progress(effectiveQuiet, 7, 7, "Comparing snapshots...");
            List<Change> changes = new ChangeFilter(effectiveIgnoreEndpoints)
                .filter(new SnapshotDiffer().diff(oldSnapshot, newSnapshot));

            System.out.print(writeConsoleSummary(changes, baseSelection, headRef, repoRoot, headCheckout.path(), headScanPaths, oldSnapshot, newSnapshot));
            writeReportIfRequested(changes, effectiveReport, normalizedFormat);

            boolean hasBreaking = changes.stream().anyMatch(change -> change.severity() == Severity.BREAKING);
            return effectiveFailOnBreaking && hasBreaking ? 1 : 0;
        } finally {
            closeQuietly(headCheckout, repoRoot);
            closeQuietly(baseCheckout, repoRoot);
        }
    }

    private void progress(boolean quiet, int step, int total, String message) {
        if (!quiet) {
            System.err.println("[" + step + "/" + total + "] " + message);
        }
    }

    private Path resolveRepoRoot(Path repoPath) throws IOException, InterruptedException {
        try {
            return gitPathOutput(repoPath, "rev-parse", "--show-toplevel");
        } catch (IOException e) {
            throw new UserFacingException(
                "The path is not a Git repository: " + repoPath + "\n"
                    + "Run this command from a Spring Boot project repository root, or pass --repo <path>.");
        }
    }

    protected Map<String, String> environment() {
        return System.getenv();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second == null || second.trim().isEmpty() ? null : second;
    }

    private Path firstNonBlankPath(Path first, String second) {
        if (first != null) {
            return first;
        }
        return second == null || second.trim().isEmpty() ? null : Paths.get(second);
    }

    private <T> T firstNonNull(T first, T second, T defaultValue) {
        if (first != null) {
            return first;
        }
        return second == null ? defaultValue : second;
    }

    private List<String> firstNonEmpty(List<String> first, List<String> second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second == null ? new ArrayList<>() : second;
    }

    private boolean hasWorktreeChanges(Path repoRoot) throws IOException, InterruptedException {
        return !gitOutputString(repoRoot, "status", "--porcelain").trim().isEmpty();
    }

    private GitCheckout checkoutRef(Path repoRoot, String ref, String label) throws IOException, InterruptedException {
        String commit;
        try {
            commit = gitOutputString(repoRoot, "rev-parse", "--verify", ref + "^{commit}").trim();
        } catch (IOException e) {
            throw new UserFacingException(
                "Git ref not found: " + ref + "\n"
                    + "Check the branch, tag, or commit exists locally. If it is a remote branch, run git fetch first.");
        }
        Path tempRoot = Files.createTempDirectory("spring-api-diff-");
        Path checkoutPath = tempRoot.resolve(label);
        gitOutputString(repoRoot, "worktree", "add", "--detach", checkoutPath.toString(), commit);
        return new GitCheckout(checkoutPath, tempRoot, true);
    }

    private ApiSnapshot scanSnapshot(
        ProjectScanner scanner,
        ScanPathResolver scanPathResolver,
        List<Path> paths,
        String ref,
        List<String> effectiveIncludes,
        List<String> effectiveExcludes) throws IOException {
        List<ApiSnapshot> snapshots = new ArrayList<>();
        for (Path path : paths) {
            try {
                snapshots.add(scanner.scan(path, effectiveIncludes, effectiveExcludes));
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
        List<io.github.springapidiff.model.Endpoint> endpoints = new ArrayList<>();
        for (ApiSnapshot snapshot : snapshots) {
            endpoints.addAll(snapshot.endpoints());
        }
        Collections.sort(endpoints, Comparator.comparing(io.github.springapidiff.model.Endpoint::id));
        return new ApiSnapshot(
            "1",
            snapshots.get(0).generatedAt(),
            new io.github.springapidiff.model.ProjectInfo(paths.get(0).getParent().getFileName().toString(), "unknown", "unknown"),
            endpoints);
    }

    private void writeReportIfRequested(List<Change> changes, Path effectiveReport, String normalizedFormat) throws IOException {
        if (effectiveReport == null) {
            return;
        }
        Path parent = effectiveReport.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String output = writeReport(changes, normalizedFormat);
        Files.write(effectiveReport, output.getBytes(StandardCharsets.UTF_8));
        System.out.println("Wrote report: " + effectiveReport.toAbsolutePath());
    }

    private String writeReport(List<Change> changes, String normalizedFormat) throws IOException {
        if ("json".equals(normalizedFormat)) {
            return new JsonReportWriter().write(changes);
        }
        return new MarkdownReportWriter().write(changes);
    }

    private String writeConsoleSummary(
        List<Change> changes,
        BaseSelection baseSelection,
        String headRef,
        Path repoRoot,
        Path headCheckoutPath,
        List<Path> scanPaths,
        ApiSnapshot oldSnapshot,
        ApiSnapshot newSnapshot) {
        StringBuilder summary = new StringBuilder();
        summary.append("API compatibility check\n\n");
        summary.append("Base: ").append(baseSelection.ref()).append("\n");
        summary.append("Base source: ").append(baseSelection.source()).append("\n");
        summary.append("Head: ").append(headRef).append("\n");
        appendScanPaths(summary, repoRoot, headCheckoutPath, scanPaths);
        summary.append("\n");
        summary.append("Changes: ").append(changes.size()).append("\n");
        summary.append("- BREAKING: ").append(count(changes, Severity.BREAKING)).append("\n");
        summary.append("- WARNING: ").append(count(changes, Severity.WARNING)).append("\n");
        summary.append("- NON_BREAKING: ").append(count(changes, Severity.NON_BREAKING)).append("\n\n");

        appendEmptyScanWarning(summary, oldSnapshot, newSnapshot);
        if (changes.isEmpty()) {
            summary.append("No API changes detected.\n");
            return summary.toString();
        }

        appendConsoleSection(summary, "BREAKING", changes, Severity.BREAKING);
        appendConsoleSection(summary, "WARNING", changes, Severity.WARNING);
        appendConsoleSection(summary, "NON_BREAKING", changes, Severity.NON_BREAKING);
        return summary.toString();
    }

    private void appendScanPaths(StringBuilder summary, Path repoRoot, Path headCheckoutPath, List<Path> scanPaths) {
        summary.append("Scan paths:\n");
        for (Path scanPath : scanPaths) {
            summary.append("- ").append(displayScanPath(repoRoot, headCheckoutPath, scanPath)).append("\n");
        }
    }

    private String displayScanPath(Path repoRoot, Path headCheckoutPath, Path scanPath) {
        Path sourceRoot = scanPath.resolve("src/main/java");
        Path base = headCheckoutPath.equals(repoRoot) ? repoRoot : headCheckoutPath;
        Path displayPath = base.relativize(sourceRoot).normalize();
        return displayPath.toString().isEmpty() ? "." : displayPath.toString().replace('\\', '/');
    }

    private void appendEmptyScanWarning(StringBuilder summary, ApiSnapshot oldSnapshot, ApiSnapshot newSnapshot) {
        if (!oldSnapshot.endpoints().isEmpty() && !newSnapshot.endpoints().isEmpty()) {
            return;
        }
        summary.append("Warning: No Spring Controller endpoints were found in ");
        if (oldSnapshot.endpoints().isEmpty() && newSnapshot.endpoints().isEmpty()) {
            summary.append("base or head");
        } else if (oldSnapshot.endpoints().isEmpty()) {
            summary.append("base");
        } else {
            summary.append("head");
        }
        summary.append(".\n");
        summary.append("Possible causes:\n");
        summary.append("- Controllers are not under src/main/java.\n");
        summary.append("- Controllers do not use supported Spring MVC annotations.\n");
        summary.append("- --include / --exclude filtered all controllers.\n");
        summary.append("- This is a multi-module project; pass --module <module-path>.\n\n");
    }

    private long count(List<Change> changes, Severity severity) {
        return changes.stream().filter(change -> change.severity() == severity).count();
    }

    private void appendConsoleSection(StringBuilder summary, String title, List<Change> changes, Severity severity) {
        List<Change> sectionChanges = new ArrayList<>();
        for (Change change : changes) {
            if (change.severity() == severity) {
                sectionChanges.add(change);
            }
        }
        if (sectionChanges.isEmpty()) {
            return;
        }
        Collections.sort(sectionChanges, Comparator.comparing(Change::endpoint).thenComparing(Change::path));
        summary.append(title).append("\n");
        for (Change change : sectionChanges) {
            summary.append("- ").append(change.endpoint()).append(" | ").append(change.message());
            if (change.oldValue() != null || change.newValue() != null) {
                summary.append(" (")
                    .append(change.oldValue() == null ? "-" : change.oldValue())
                    .append(" -> ")
                    .append(change.newValue() == null ? "-" : change.newValue())
                    .append(")");
            }
            summary.append("\n");
            summary.append("  Impact: ").append(ChangeAdvice.impact(change)).append("\n");
            summary.append("  Suggestion: ").append(ChangeAdvice.suggestion(change)).append("\n");
        }
        summary.append("\n");
    }

    private Path gitPathOutput(Path repoRoot, String... args) throws IOException, InterruptedException {
        String output = gitOutputString(repoRoot, args).trim();
        return Paths.get(output).toAbsolutePath().normalize();
    }

    private String gitOutputString(Path repoRoot, String... args) throws IOException, InterruptedException {
        CommandResult result = git(repoRoot, true, args);
        return result.output;
    }

    private CommandResult git(Path repoRoot, boolean failOnError, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(repoRoot.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = read(process.getInputStream());
        int exitCode = process.waitFor();
        if (failOnError && exitCode != 0) {
            throw new IOException("Git command failed: " + command + "\n" + output);
        }
        return new CommandResult(exitCode, output);
    }

    private String read(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private void closeQuietly(GitCheckout checkout, Path repoRoot) {
        if (checkout == null) {
            return;
        }
        try {
            checkout.close(repoRoot);
        } catch (IOException | InterruptedException ignored) {
            // Cleanup failures should not hide the actual check result.
        }
    }

    private static class CommandResult {
        private final int exitCode;
        private final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static class GitCheckout {
        private final Path path;
        private final Path tempRoot;
        private final boolean temporaryWorktree;

        private GitCheckout(Path path, Path tempRoot, boolean temporaryWorktree) {
            this.path = path;
            this.tempRoot = tempRoot;
            this.temporaryWorktree = temporaryWorktree;
        }

        private static GitCheckout current(Path path) {
            return new GitCheckout(path, null, false);
        }

        private Path path() {
            return path;
        }

        private void close(Path repoRoot) throws IOException, InterruptedException {
            if (!temporaryWorktree) {
                return;
            }
            ProcessBuilder processBuilder = new ProcessBuilder("git", "worktree", "remove", "--force", path.toString());
            processBuilder.directory(repoRoot.toFile());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.waitFor();
            deleteDirectory(tempRoot);
        }

        private void deleteDirectory(Path directory) throws IOException {
            if (directory == null || !Files.exists(directory)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(directory)) {
                List<Path> sorted = new ArrayList<>();
                paths.forEach(sorted::add);
                Collections.sort(sorted, Comparator.reverseOrder());
                for (Path path : sorted) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
