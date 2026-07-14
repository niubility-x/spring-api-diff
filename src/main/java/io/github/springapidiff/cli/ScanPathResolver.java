package io.github.springapidiff.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

class ScanPathResolver {
    private final Path module;

    ScanPathResolver(Path module) {
        this.module = module;
    }

    List<Path> resolve(Path checkoutPath) throws IOException {
        if (module != null) {
            Path modulePath = checkoutPath.resolve(module).normalize();
            if (!Files.isDirectory(modulePath)) {
                throw new UserFacingException(
                    "模块目录不存在：" + module + "\n\n"
                        + "请确认：\n"
                        + "1. --module 是相对于 Git 仓库根目录的路径\n"
                        + "2. base 和 head 两个版本都包含该模块");
            }
            return Collections.singletonList(modulePath);
        }
        if (Files.isDirectory(checkoutPath.resolve("src/main/java"))) {
            return Collections.singletonList(checkoutPath);
        }
        List<Path> modulePaths = discoverModulePaths(checkoutPath);
        if (!modulePaths.isEmpty()) {
            return modulePaths;
        }
        return Collections.singletonList(checkoutPath);
    }

    UserFacingException sourceRootNotFound(String ref) {
        if (module != null) {
            return new UserFacingException(
                "没有找到 " + module + "/src/main/java。\n\n"
                    + "请确认：\n"
                    + "1. --module 是相对于 Git 仓库根目录的路径\n"
                    + "2. " + ref + " 版本中的该模块包含 src/main/java");
        }
        return new UserFacingException(
            "没有找到 src/main/java，也没有自动发现包含 src/main/java 的子模块。\n"
                + "如果只想扫描某个模块，请使用：\n"
                + "spring-api-diff check --module <module-path>");
    }

    private List<Path> discoverModulePaths(Path checkoutPath) throws IOException {
        List<Path> modulePaths = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(checkoutPath, 6)) {
            List<Path> sourceRoots = paths
                .filter(Files::isDirectory)
                .filter(path -> "java".equals(path.getFileName().toString()))
                .filter(path -> path.getParent() != null && "main".equals(path.getParent().getFileName().toString()))
                .filter(path -> path.getParent().getParent() != null && "src".equals(path.getParent().getParent().getFileName().toString()))
                .sorted()
                .collect(java.util.stream.Collectors.toList());
            for (Path sourceRoot : sourceRoots) {
                modulePaths.add(sourceRoot.getParent().getParent().getParent());
            }
        }
        return modulePaths;
    }
}
