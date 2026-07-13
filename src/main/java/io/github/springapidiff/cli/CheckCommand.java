package io.github.springapidiff.cli;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.Severity;
import io.github.springapidiff.diff.SnapshotDiffer;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.report.ChangeAdvice;
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
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "check", description = "Check Spring REST API compatibility between Git states.")
public class CheckCommand implements Callable<Integer> {
    @Option(names = "--repo", description = "Git repository path. Defaults to current directory.")
    private Path repo = Paths.get(".");

    @Option(names = "--base", description = "Base Git ref. Defaults to origin/main, main, then master.")
    private String base;

    @Option(names = "--head", description = "Head Git ref. Defaults to HEAD when the worktree is clean.")
    private String head;

    @Option(names = "--worktree", description = "Compare the base ref with the current working tree, including uncommitted changes.")
    private boolean worktree;

    @Option(names = "--report", description = "Markdown report output file. Prints a summary to stdout either way.")
    private Path report;

    @Option(names = "--fail-on-breaking", description = "Return non-zero when breaking changes are found.")
    private boolean failOnBreaking;

    @Option(names = "--include", description = "Only include controllers whose package starts with this value.")
    private List<String> includes = new ArrayList<>();

    @Option(names = "--exclude", description = "Exclude controllers whose package starts with this value.")
    private List<String> excludes = new ArrayList<>();

    @Option(names = "--format", defaultValue = "markdown", description = "Report format. Supported: markdown.")
    private String format;

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
        if (!"markdown".equals(format.toLowerCase(Locale.ROOT))) {
            throw new UserFacingException("Unsupported report format: " + format + "\nSupported format: markdown");
        }
        if (worktree && head != null) {
            throw new UserFacingException("Use either --worktree or --head, not both.\nExample: spring-api-diff check --base main --worktree");
        }

        Path repoRoot = resolveRepoRoot(repo.toAbsolutePath().normalize());
        String baseRef = base == null ? detectBaseRef(repoRoot) : base;
        boolean useWorktree = worktree || (head == null && hasWorktreeChanges(repoRoot));
        String headRef = useWorktree ? "worktree" : (head == null ? "HEAD" : head);

        GitCheckout baseCheckout = null;
        GitCheckout headCheckout = null;
        try {
            baseCheckout = checkoutRef(repoRoot, baseRef, "base");
            headCheckout = useWorktree ? GitCheckout.current(repoRoot) : checkoutRef(repoRoot, headRef, "head");

            ProjectScanner scanner = new ProjectScanner();
            ApiSnapshot oldSnapshot = scanSnapshot(scanner, baseCheckout.path(), baseRef);
            ApiSnapshot newSnapshot = scanSnapshot(scanner, headCheckout.path(), headRef);
            List<Change> changes = new SnapshotDiffer().diff(oldSnapshot, newSnapshot);

            System.out.print(writeConsoleSummary(changes, baseRef, headRef, oldSnapshot, newSnapshot));
            writeReportIfRequested(changes);

            boolean hasBreaking = changes.stream().anyMatch(change -> change.severity() == Severity.BREAKING);
            return failOnBreaking && hasBreaking ? 1 : 0;
        } finally {
            closeQuietly(headCheckout, repoRoot);
            closeQuietly(baseCheckout, repoRoot);
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

    private String detectBaseRef(Path repoRoot) throws IOException, InterruptedException {
        for (String candidate : Arrays.asList("origin/main", "main", "master")) {
            if (git(repoRoot, false, "rev-parse", "--verify", candidate + "^{commit}").exitCode == 0) {
                return candidate;
            }
        }
        throw new UserFacingException(
            "No base ref could be detected.\n\n"
                + "Tried:\n"
                + "- origin/main\n"
                + "- main\n"
                + "- master\n\n"
                + "Pass a base ref explicitly, for example:\n"
                + "spring-api-diff check --base origin/main");
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

    private ApiSnapshot scanSnapshot(ProjectScanner scanner, Path path, String ref) throws IOException {
        try {
            return scanner.scan(path, includes, excludes);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Java source root not found")) {
                throw new UserFacingException(
                    "Java source directory was not found for " + ref + ": " + path.resolve("src/main/java") + "\n\n"
                        + "Please check:\n"
                        + "1. Run from the Spring Boot project root, or pass --repo <path>.\n"
                        + "2. For multi-module projects, run inside the module directory for now.\n"
                        + "3. Make sure the selected Git ref contains src/main/java.");
            }
            throw e;
        }
    }

    private void writeReportIfRequested(List<Change> changes) throws IOException {
        if (report == null) {
            return;
        }
        Path parent = report.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String markdown = new MarkdownReportWriter().write(changes);
        Files.write(report, markdown.getBytes(StandardCharsets.UTF_8));
        System.out.println("Wrote report: " + report.toAbsolutePath());
    }

    private String writeConsoleSummary(List<Change> changes, String baseRef, String headRef, ApiSnapshot oldSnapshot, ApiSnapshot newSnapshot) {
        StringBuilder summary = new StringBuilder();
        summary.append("API compatibility check\n\n");
        summary.append("Base: ").append(baseRef).append("\n");
        summary.append("Head: ").append(headRef).append("\n\n");
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
        summary.append("- This is a multi-module project; run from the target module directory for now.\n\n");
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

    private static class UserFacingException extends IOException {
        private UserFacingException(String message) {
            super(message);
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
