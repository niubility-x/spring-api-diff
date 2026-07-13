package io.github.springapidiff.cli;

import io.github.springapidiff.io.SnapshotWriter;
import io.github.springapidiff.model.ApiSnapshot;
import io.github.springapidiff.scanner.ProjectScanner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "snapshot", description = "Generate an API snapshot from a Spring Boot project.")
public class SnapshotCommand implements Callable<Integer> {
    @Option(names = "--project", required = true, description = "Spring Boot project path.")
    private Path project;

    @Option(names = "--out", required = true, description = "Snapshot output file.")
    private Path out;

    @Option(names = "--include", description = "Only include controllers whose package starts with this value.")
    private List<String> includes = new ArrayList<>();

    @Option(names = "--exclude", description = "Exclude controllers whose package starts with this value.")
    private List<String> excludes = new ArrayList<>();

    @Option(names = "--format", defaultValue = "json", description = "Snapshot format. Supported: json.")
    private String format;

    @Override
    public Integer call() throws Exception {
        if (!"json".equals(format.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported snapshot format: " + format);
        }
        ApiSnapshot snapshot = new ProjectScanner().scan(project, includes, excludes);
        new SnapshotWriter().write(snapshot, out);
        System.out.println("Wrote snapshot: " + out.toAbsolutePath());
        return 0;
    }
}
