# Contributing

Thank you for improving HomeLab Monitor.

## Before starting

Read `AGENTS.md`, `docs/PROJECT_SPEC.md`, and the relevant architecture section. Confirm the current phase and avoid implementing roadmap work ahead of core correctness.

Use a focused branch for meaningful work. Describe a real problem and acceptance criteria in the issue or branch context before large changes.

## Changes

- Keep the modular monolith understandable and proportional to a single-instance home-lab deployment.
- Add tests for behavior and regressions, especially state, incident, concurrency, authentication, and metrics logic.
- Do not commit secrets, generated output, machine-specific paths, fake screenshots, or unsupported documentation claims.
- Update documentation whenever behavior, configuration, architecture, or deployment changes.
- Use coherent Conventional Commit-style messages where practical.

## Verification

Run the commands in `AGENTS.md` that apply to the change. Include exact commands and results in the pull request. If a required check cannot run, explain why rather than claiming success.

## Pull requests

Keep pull requests reviewable and focused. Explain the problem, solution, important decisions, tests, known limitations, and related issue. Include genuine screenshots for visible UI changes after the application exists.
