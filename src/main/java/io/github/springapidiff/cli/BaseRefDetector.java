package io.github.springapidiff.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class BaseRefDetector {
    private final boolean fetch;
    private final Map<String, String> environment;
    private final GitClient gitClient;

    BaseRefDetector(boolean fetch, Map<String, String> environment) {
        this(fetch, environment, new GitClient());
    }

    BaseRefDetector(boolean fetch, Map<String, String> environment, GitClient gitClient) {
        this.fetch = fetch;
        this.environment = environment;
        this.gitClient = gitClient;
    }

    BaseSelection detect(Path repoRoot) throws IOException, InterruptedException {
        BaseSelection ciBase = detectCiBaseRef(repoRoot);
        if (ciBase != null) {
            return ciBase;
        }

        BaseSelection upstreamMergeBase = detectUpstreamMergeBase(repoRoot);
        if (upstreamMergeBase != null) {
            return upstreamMergeBase;
        }

        BaseSelection originHead = detectOriginHead(repoRoot);
        if (originHead != null) {
            return originHead;
        }

        for (String candidate : Arrays.asList("origin/main", "origin/master", "origin/develop", "main", "master", "develop")) {
            if (gitClient.execute(repoRoot, false, "rev-parse", "--verify", candidate + "^{commit}").exitCode() == 0) {
                return new BaseSelection(candidate, "fallback branch " + candidate);
            }
        }
        throw new UserFacingException(
            "No base ref could be detected.\n\n"
                + "Tried:\n"
                + "- CI target branch environment variables\n"
                + "- current branch upstream merge-base\n"
                + "- origin/HEAD\n"
                + "- origin/main\n"
                + "- origin/master\n"
                + "- origin/develop\n"
                + "- main\n"
                + "- master\n"
                + "- develop\n\n"
                + "Pass a base ref explicitly, for example:\n"
                + "spring-api-diff check --base origin/main");
    }

    private BaseSelection detectCiBaseRef(Path repoRoot) throws IOException, InterruptedException {
        CiTargetBranch targetBranch = ciTargetBranch();
        if (targetBranch == null) {
            return null;
        }
        for (String candidate : branchRefCandidates(targetBranch.name())) {
            if (gitClient.execute(repoRoot, false, "rev-parse", "--verify", candidate + "^{commit}").exitCode() == 0) {
                return new BaseSelection(candidate, "CI target branch " + targetBranch.envKey() + "=" + targetBranch.name());
            }
        }
        if (fetch) {
            gitClient.execute(repoRoot, false, "fetch", "origin", targetBranch.name() + ":refs/remotes/origin/" + targetBranch.name());
            for (String candidate : branchRefCandidates(targetBranch.name())) {
                if (gitClient.execute(repoRoot, false, "rev-parse", "--verify", candidate + "^{commit}").exitCode() == 0) {
                    return new BaseSelection(candidate, "CI target branch " + targetBranch.envKey() + "=" + targetBranch.name() + " after git fetch");
                }
            }
            throw new UserFacingException(
                "Detected CI target branch " + targetBranch.name() + ", but no matching local Git ref was found after git fetch.\n\n"
                    + "Tried:\n"
                    + "- git fetch origin " + targetBranch.name() + ":refs/remotes/origin/" + targetBranch.name() + "\n"
                    + "- origin/" + targetBranch.name() + "\n"
                    + "- " + targetBranch.name() + "\n\n"
                    + "Check CI checkout permissions, network access, and branch name.");
        }
        throw new UserFacingException(
            "Detected CI target branch " + targetBranch.name() + ", but no matching local Git ref was found.\n\n"
                + "Tried:\n"
                + "- origin/" + targetBranch.name() + "\n"
                + "- " + targetBranch.name() + "\n\n"
                + "Make sure checkout/fetch includes the target branch, for example:\n"
                + "git fetch origin " + targetBranch.name() + ":refs/remotes/origin/" + targetBranch.name());
    }

    private CiTargetBranch ciTargetBranch() {
        for (String key : Arrays.asList(
            "GITHUB_BASE_REF",
            "CI_MERGE_REQUEST_TARGET_BRANCH_NAME",
            "CI_DEFAULT_BRANCH",
            "CHANGE_TARGET")) {
            String value = trimToNull(environment.get(key));
            if (value != null) {
                return new CiTargetBranch(key, value);
            }
        }
        return null;
    }

    private Set<String> branchRefCandidates(String branch) {
        Set<String> candidates = new LinkedHashSet<>();
        if (branch.startsWith("origin/")) {
            candidates.add(branch);
            candidates.add(branch.substring("origin/".length()));
        } else {
            candidates.add("origin/" + branch);
            candidates.add(branch);
        }
        return candidates;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private BaseSelection detectUpstreamMergeBase(Path repoRoot) throws IOException, InterruptedException {
        if (gitClient.execute(repoRoot, false, "rev-parse", "--verify", "@{upstream}").exitCode() != 0) {
            return null;
        }
        GitClient.CommandResult result = gitClient.execute(repoRoot, false, "merge-base", "HEAD", "@{upstream}");
        if (result.exitCode() != 0 || result.output().trim().isEmpty()) {
            return null;
        }
        return new BaseSelection(result.output().trim(), "current branch upstream merge-base");
    }

    private BaseSelection detectOriginHead(Path repoRoot) throws IOException, InterruptedException {
        GitClient.CommandResult result = gitClient.execute(repoRoot, false, "symbolic-ref", "--short", "refs/remotes/origin/HEAD");
        if (result.exitCode() != 0 || result.output().trim().isEmpty()) {
            return null;
        }
        String originHead = result.output().trim();
        return gitClient.execute(repoRoot, false, "rev-parse", "--verify", originHead + "^{commit}").exitCode() == 0
            ? new BaseSelection(originHead, "origin/HEAD")
            : null;
    }

    private static class CiTargetBranch {
        private final String envKey;
        private final String name;

        private CiTargetBranch(String envKey, String name) {
            this.envKey = envKey;
            this.name = name;
        }

        private String envKey() {
            return envKey;
        }

        private String name() {
            return name;
        }
    }
}
