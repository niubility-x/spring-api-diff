# Development Guide

This guide covers local development for spring-api-diff.

## Requirements

- JDK 8 or later
- Maven 3.6 or later
- Git

## Encoding

All source and documentation files must be saved as UTF-8 without BOM.

The repository uses:

- `.editorconfig` for editor defaults
- `.gitattributes` for Git line-ending normalization
- LF line endings for source, Markdown, XML, YAML, JSON, and properties files
- CRLF line endings only for Windows batch scripts

On Windows PowerShell, Chinese text may display incorrectly if the console is not using UTF-8. The file content can still be valid UTF-8. To switch the current terminal:

```powershell
chcp 65001
```

## Maven Local Repository

The project uses `.mvn/maven.config` to set:

```text
-Dmaven.repo.local=.mvn/repository
```

This keeps downloaded dependencies inside the project workspace. It avoids permission problems in restricted environments and prevents writes to a machine-level Maven repository such as `D:\software\3.6.3\repository`.

The repository is intentionally outside `target/` so `mvn clean package` does not delete Maven's own plugin dependencies while Maven is running on Windows.

## Proxy

If dependency download fails because of network restrictions, use your local proxy. For example, with a proxy on `127.0.0.1:7897`:

```powershell
mvn "-Dhttp.proxyHost=127.0.0.1" "-Dhttp.proxyPort=7897" "-Dhttps.proxyHost=127.0.0.1" "-Dhttps.proxyPort=7897" test
```

For packaging:

```powershell
mvn "-Dhttp.proxyHost=127.0.0.1" "-Dhttp.proxyPort=7897" "-Dhttps.proxyHost=127.0.0.1" "-Dhttps.proxyPort=7897" clean package
```

## Test

Run all tests:

```bash
mvn test
```

The test suite includes scanner, diff, CLI, Git worktree, reporting, and snapshot validation tests.

## Build

Build the executable jar:

```bash
mvn clean package
```

The jar is generated at:

```text
target/spring-api-diff-0.1.1.jar
```

Verify the jar:

```bash
java -jar target/spring-api-diff-0.1.1.jar --version
java -jar target/spring-api-diff-0.1.1.jar check --help
```

## Release Checklist

Before publishing a release:

1. Ensure `pom.xml` version matches the release tag.
2. Run `mvn test`.
3. Run `mvn clean package`.
4. Verify the executable jar with `--version`.
5. Update `CHANGELOG.md`.
6. Push a tag like `v0.1.1`.

The GitHub release workflow builds the jar, verifies `--version`, generates a SHA-256 checksum, and uploads both files to GitHub Releases.
