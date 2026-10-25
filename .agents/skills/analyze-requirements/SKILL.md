---
name: analyze-requirements
description: Analyze mobile requirement documents (PDF/MD/text) or API specs to extract core requirements, UI models, and execution steps. Output must be in Korean.
---

# Analyze Requirements

## Overview
Analyzes PRDs, problem specifications (PDF, MD, text), and API specifications to extract actionable mobile requirements and presentation UI models.

## 3-Step Analysis Workflow

### 1. Extract Core Requirements & Constraints
- **Core Interactions**: Key user actions and screen behaviors.
- **Data Source**: REST API endpoints (URL, HTTP method, parameters) or sample payload.
- **Key Constraints**:
  - External triggers (DeepLink, custom scheme, singleTask/singleTop instance).
  - State synchronization (SSOT, in-memory cache, optimistic update).
  - Performance & reactivity (search debounce, pagination, real-time polling).

### 2. Define Presentation Models (UiModel & UiState)
- **UiModel**: Decouple domain models (`com.nlab.androidboot.model`) from screen rendering by proposing dedicated UI model classes. Network models (`Network*`) never reach the UI layer.
- **UiState**: Design Unidirectional Data Flow (UDF) LCE (`Loading / Success / Error`) sealed interface.
- **Screen Navigation (When screens > 1)**: Propose lightweight `@Parcelize sealed interface Screen : Parcelable` routes without external navigation libraries.

### 3. Propose Sequential Implementation Steps
- Propose atomic implementation order:
  1. Data layer (Network models, domain models, Retrofit DataSource, Repository via `create-network-datasource`)
  2. Business logic & ViewModel (StateFlow, event handling, data transformation)
  3. UI presentation & Navigation (Compose screens, Screen + `SaveableStateHolder` when screens > 1)
- **Applicable Conventions**: End the briefing with the convention files to read for this task, chosen with the mapping table in `AGENTS.md` (e.g. `core.md`, `data-network.md`, `viewmodel-state.md`, `compose-ui.md`).

## Rules
- **Language**: Always output the final analysis briefing in natural Korean (per global rule).
- **No Premature Code**: Focus on specifications, UI models, and steps without generating bloated source code upfront.
- **Zero File Bloat**: Deliver the briefing directly in chat. Do NOT create arbitrary markdown files on disk unless explicitly requested.
- **When a Document Is Requested**: If the user asks for a document ("문서로 만들어줘", "md로 만들어줘"), write a local markdown file at `docs/{task-name}-analysis.md` (kebab-case task name, e.g. `docs/product-list-analysis.md`) instead of using an external document service or connector. Keep the chat reply to a short summary and the file path.
