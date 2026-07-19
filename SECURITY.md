# Security Policy

## Supported Versions

spring-api-diff is currently in early `0.x` development. Security fixes are provided for the latest released version.

## Reporting a Vulnerability

Please do not open a public issue for a suspected security vulnerability.

Report it by contacting the maintainer through the GitHub repository owner profile, or by opening a private security advisory if GitHub enables that option for this repository.

Please include:

- Affected version or commit
- Steps to reproduce
- Impact
- Any suggested mitigation

## Security Model

spring-api-diff performs static source analysis. It should not start the target Spring application and should not execute code from the target project.

When contributing scanner features, preserve this boundary.
