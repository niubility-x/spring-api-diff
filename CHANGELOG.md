# Changelog

All notable changes to this project will be documented in this file.

This project follows semantic versioning while it is in early `0.x` development. Breaking behavior in scanner output may still change between minor versions.

## 0.1.1 - 2026-07-18

### Added

- Multi-module scan path discovery and module include/exclude filters.
- CI target branch based base-ref detection.
- Optional `--fetch` support for missing CI target refs.
- YAML configuration file support through `spring-api-diff.yml`.
- Endpoint ignore rules through `--ignore-endpoint`.
- JSON report output.
- Controller interface contract resolution.
- String constant based path resolution.
- Snapshot structure validation and duplicate endpoint detection.
- GitHub Actions CI and release workflows.

### Changed

- `check` flow split into smaller Git, configuration, scan-path, and compatibility service classes.
- README moved to `README.md` and expanded with CI, module, config, and report examples.

### Fixed

- Detect query parameters and request body fields that become required.
- Make Git worktree tests tolerant of Windows long-path and 8.3 short-path differences.

## 0.1.0 - 2026-07-13

### Added

- Initial CLI with `snapshot`, `diff`, and `check` commands.
- Static Spring MVC controller and DTO scanning.
- Markdown report output.
- Breaking, warning, and non-breaking diff categories.
- `--fail-on-breaking` support for CI usage.
