package io.github.springapidiff;

import io.github.springapidiff.cli.CheckCommand;
import io.github.springapidiff.cli.DiffCommand;
import io.github.springapidiff.cli.SnapshotCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "spring-api-diff",
    mixinStandardHelpOptions = true,
    version = "spring-api-diff 0.1.0",
    description = "Detect breaking REST API changes in Spring Boot projects.",
    subcommands = {SnapshotCommand.class, DiffCommand.class, CheckCommand.class})
public class Main implements Runnable {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
