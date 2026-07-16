package io.github.springapidiff.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class GitClient {
    CommandResult execute(Path workingDirectory, boolean failOnError, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = read(process.getInputStream());
        int exitCode = process.waitFor();
        if (failOnError && exitCode != 0) {
            throw new IOException("Git command failed: " + command + "\n" + output);
        }
        return new CommandResult(exitCode, output);
    }

    String output(Path workingDirectory, String... args) throws IOException, InterruptedException {
        return execute(workingDirectory, true, args).output();
    }

    Path pathOutput(Path workingDirectory, String... args) throws IOException, InterruptedException {
        return Paths.get(output(workingDirectory, args).trim()).toAbsolutePath().normalize();
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

    static class CommandResult {
        private final int exitCode;
        private final String output;

        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        int exitCode() {
            return exitCode;
        }

        String output() {
            return output;
        }
    }
}
