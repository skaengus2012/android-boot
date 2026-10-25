# Data & Network Conventions

Read for tasks that call a REST API or add a DataSource / Repository / domain model. Follow `.agents/skills/create-network-datasource/SKILL.md` to generate the files.

## Layers and Packages
```
network/datasource/{Name}DataSource.kt   # Network models (Network*), private ApiService, DataSource
model/{Item}.kt                          # Domain models consumed by ViewModels (com.nlab.androidboot.model)
data/{Name}Repository.kt                 # Maps Network* -> domain model, returns Result<T>
domain/{...}/                            # Domain state holders such as FavoriteStore (com.nlab.androidboot.domain)
ui/...                                   # ViewModel, UiState, UiModel, composables
```
- Dependency direction: `ui` → `data` / `domain` / `model`. Only `data` (Repository) may import `network.datasource.Network*`. ViewModels, UiStates and composables MUST NOT import `Network*` models.

## Rules
- **Single-File Encapsulation**: Network models, private ApiService interface, and DataSource class reside in a single file (`network/datasource/{Name}DataSource.kt`).
- **Network Model Annotations & Naming**: Every `@Serializable` model MUST define `@SerialName("...")` on all fields. Avoid arbitrary default values (e.g. `= emptyList()`, `= 0`, `= false`); model fields strictly according to the API contract. All network model names MUST start with the `Network` prefix (e.g., `NetworkProduct`, `NetworkUser`). A list-with-metadata wrapper is `Network{Item}Page`.
- **Domain Models**: ViewModels consume plain Kotlin models from `com.nlab.androidboot.model` (e.g., `Product`, `ProductPage`) with no serialization annotations, no default values, preserved nullability, and enums for fixed-value strings (with an explicit fallback for unknown values).
- **Plain Value Responses**: A response that is just `List<String>` or a primitive needs no Network model and no mapping.
- **Repository Pattern**:
  - Repositories MUST only orchestrate and delegate calls to DataSources, wrapping them via `suspendRunCatching` and returning `Result<T>` where `T` is a domain model.
  - **Mapping in Repository**: Convert `Network*` to domain models with `private` `asModel()` extension functions at the bottom of the Repository file. Do the mapping inside the `suspendRunCatching` block. Never map in the DataSource, ViewModel, or UI.
  - **No Business Logic or State in Repository**: Repositories must remain stateless data pipes. Field-for-field mapping is allowed. Never place business logic, in-memory state caching, or domain state management (e.g. Favorite toggle, Cart items, mutable `StateFlow`) inside Repositories. Domain state synchronization must be handled by dedicated domain state holders (e.g. `FavoriteStore`) or UseCases.
- **Domain State Holders Location**: Singleton state holders such as `FavoriteStore` live in `com.nlab.androidboot.domain.{feature}` (e.g. `domain/favorite/FavoriteStore.kt`), not in `data/`.
