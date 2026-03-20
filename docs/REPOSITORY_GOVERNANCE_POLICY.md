# Repository Governance Policy

## Purpose
This repository enforces policy-as-code controls for code quality, security, and maintainability.

## Active Enforcement Controls
- Pull-request based change control on `main`
- Mandatory CI checks (build/test/dependency review)
- CodeQL static analysis
- Secret scanning (Gitleaks)
- Dependency update automation (Dependabot)
- Third-party license compliance checks
- CODEOWNERS-based ownership
- Structured issue/PR templates
- Security reporting process in `SECURITY.md`

## Branch and Review Policy
- Protected branch: `main`
- No direct pushes to protected branch
- Pull requests required
- Required approvals:
  - Small/private profile: `1`
  - Standard profile: `2`
  - Strict/regulatory profile: `2+` and code-owner review
- Dismiss stale approvals on new commits
- Require conversation resolution before merge
- Block force-push and branch deletion on protected branch

## Security Policy
- Code scanning via CodeQL on push/PR/schedule
- Dependency review blocks high-severity vulnerable additions
- Secret scanning runs on push and pull requests
- Vulnerability reporting via GitHub Security Advisories
- Security remediation SLA:
  - Critical: 24-72 hours
  - High: 7 days
  - Medium: 30 days
  - Low: 90 days

## Dependency and Supply Chain Policy
- Dependabot for Maven and GitHub Actions
- Weekly dependency update cadence
- Security labels applied to dependency update PRs
- Recommended: signed releases and SBOM generation in release pipelines

## License Compliance Policy
- Repository must include a license file (`LICENSE` or `LICENSE.md`)
- CI produces dependency third-party license report
- CI fails on disallowed licenses (AGPL, GPL-3.0, SSPL, BUSL, Commons Clause)

## Issue Management Policy
- Blank issues disabled
- Standardized templates for bug/feature/task intake
- Security issues routed to private disclosure channel
- Standard labels should include:
  - `type:*` (bug, feature, task)
  - `priority:*` (p0-p3)
  - `status:*` (triage, in-progress, blocked, done)

## Open Source vs Private Profiles
- Open source:
  - Keep issue templates public and contributor-friendly
  - Enable private vulnerability reporting
  - Use CONTRIBUTING and DCO/CLA as needed
- Private/internal:
  - Enforce SSO/MFA for maintainers
  - Restrict admin bypass to platform/security owners
  - Apply stricter review and audit cadence for sensitive systems

## Administration
Repository settings that cannot be fully declared in workflow files are applied using:
- `scripts/github/apply-repository-policy.sh`

That script configures branch protection, security features, and baseline labels via GitHub API.

