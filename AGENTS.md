# android-boot

This repository is a starter template for Android development.

## Conventions
All architecture and coding conventions are strictly maintained under `.agents/conventions/`.

**Before writing or reviewing code, ALWAYS read [core.md](.agents/conventions/core.md) first, then read every topic file that matches the task in the table below.** Read the matching files in full; do not rely on memory of their contents. If the task spans several rows, read all of them.

| If the task involves... | Read |
|---|---|
| Any task (always) | [core.md](.agents/conventions/core.md): tech stack, no deprecated APIs, string/localization rule |
| Calling a REST API, DataSource, Repository, Network/domain models | [data-network.md](.agents/conventions/data-network.md) |
| ViewModel, UiState, Flow/StateFlow pipelines, polling, API-call timing/concurrency, snackbar/dialog events | [viewmodel-state.md](.agents/conventions/viewmodel-state.md) |
| Compose screens, list items, animations, recomposition/performance, multi-screen navigation | [compose-ui.md](.agents/conventions/compose-ui.md) |

Typical combinations: API list screen → `data-network` + `viewmodel-state` + `compose-ui`; real-time/polling screen → the same three; pure UI or animation task → `compose-ui`.

## Skills
Available agent workflows and tool runbooks are located in `.agents/skills/`:
- `analyze-requirements`: Requirement analysis and UI model breakdown.
- `create-network-datasource`: Network DataSource, domain model and Repository generation.
