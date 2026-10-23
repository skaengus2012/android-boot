# Android Boot Architecture & Conventions

## 1. Tech Stack & Rules
- **UI**: Jetpack Compose Material 3 (No legacy XML, No Material 2).
- **DI**: Hilt (`@HiltViewModel`, `@AndroidEntryPoint`, `@Singleton`).
- **Network**: Retrofit 2 + Kotlinx Serialization. Delegate to `RetrofitFactory` (`com.nlab.androidboot.network.infra.RetrofitFactory`). Never use Gson, Moshi, or raw OkHttp.
- **Async & State**: Coroutines + `StateFlow`. Use `suspendRunCatching` (`com.nlab.androidboot.coroutines`). Never use RxJava or AsyncTask.
- **Image**: Coil 3 Compose (`AsyncImage`).

## 2. Data & Network Conventions
- **Single-File Encapsulation**: DTOs, private ApiService interface, and DataSource class reside in a single file (`network/datasource/{Name}DataSource.kt`).
- **DTO Annotations**: Every `@Serializable` model MUST define `@SerialName("...")` on all fields with safe default values.
- **Repository Pattern**: Wrap calls via `suspendRunCatching` and return `Result<T>`. Follow `.agents/skills/create-network-datasource/SKILL.md`.

## 3. Presentation & UI Conventions
- **Unidirectional Data Flow (UDF)**:
  - Expose a single `StateFlow<UiState>` from ViewModel.
  - Standard LCE pattern:
    ```kotlin
    sealed interface UiState {
        data object Loading : UiState
        data class Success(val items: List<ItemUiModel>) : UiState
        data class Error(val message: String) : UiState
    }
    ```
- **Compose Rules**:
  - Always provide unique keys in lazy lists: `items(items, key = { it.id })`.
  - Pass lambda callbacks rather than entire ViewModel instances to leaf composables.
  - Never reference Android `View` or `Activity` context inside ViewModels.
