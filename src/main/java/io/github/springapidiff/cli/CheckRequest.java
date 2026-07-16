package io.github.springapidiff.cli;

import java.nio.file.Path;
import java.util.List;

class CheckRequest {
    private final Path repoRoot;
    private final String baseRef;
    private final String headRef;
    private final boolean worktree;
    private final Path module;
    private final List<String> includeModules;
    private final List<String> excludeModules;
    private final List<String> includes;
    private final List<String> excludes;
    private final List<String> ignoreEndpoints;

    CheckRequest(
        Path repoRoot,
        String baseRef,
        String headRef,
        boolean worktree,
        Path module,
        List<String> includeModules,
        List<String> excludeModules,
        List<String> includes,
        List<String> excludes,
        List<String> ignoreEndpoints) {
        this.repoRoot = repoRoot;
        this.baseRef = baseRef;
        this.headRef = headRef;
        this.worktree = worktree;
        this.module = module;
        this.includeModules = includeModules;
        this.excludeModules = excludeModules;
        this.includes = includes;
        this.excludes = excludes;
        this.ignoreEndpoints = ignoreEndpoints;
    }

    Path repoRoot() {
        return repoRoot;
    }

    String baseRef() {
        return baseRef;
    }

    String headRef() {
        return headRef;
    }

    boolean worktree() {
        return worktree;
    }

    Path module() {
        return module;
    }

    List<String> includeModules() {
        return includeModules;
    }

    List<String> excludeModules() {
        return excludeModules;
    }

    List<String> includes() {
        return includes;
    }

    List<String> excludes() {
        return excludes;
    }

    List<String> ignoreEndpoints() {
        return ignoreEndpoints;
    }
}
