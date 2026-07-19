# Contributing

Thanks for helping improve spring-api-diff.

## Project Scope

spring-api-diff is an early-stage static REST API compatibility checker for Java Spring MVC projects. It scans source code instead of starting the target Spring application.

Good contributions include:

- Spring MVC annotation parsing improvements
- DTO and request/response contract detection
- Diff rules for breaking, warning, and non-breaking changes
- CLI usability improvements
- CI and report output improvements
- Documentation and fixture coverage

Please keep changes focused and include tests for behavior changes.

## Development Setup

See [docs/development.md](docs/development.md).

Quick check:

```bash
mvn test
```

Build:

```bash
mvn clean package
```

## Coding Guidelines

- Keep code simple, readable, and testable.
- Prefer small classes with clear responsibilities.
- Match existing package boundaries.
- Avoid adding runtime Spring application startup requirements.
- Keep static scanning safe: do not execute code from the target project.
- Use UTF-8 without BOM for all text files.
- Add or update fixtures when scanner behavior changes.

## Pull Request Checklist

- Tests pass with `mvn test`.
- New behavior is covered by focused tests.
- Documentation is updated when user-visible behavior changes.
- Public CLI changes are reflected in `README.md`.
- Generated artifacts, logs, IDE files, and local caches are not committed.

## Reporting Bugs

When reporting a false positive or false negative, include:

- The relevant controller method
- DTO classes involved
- The command you ran
- The actual output
- The expected output

Small reproducible fixtures are especially helpful.
