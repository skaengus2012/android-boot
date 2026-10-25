# Android Boot Architecture & Conventions

Core rules that apply to every task. Topic-specific rules live in separate files as defined in [AGENTS.md](../../AGENTS.md).

## 1. Package Map
Source root: `app/src/main/kotlin/com/nlab/androidboot/`. Other docs and skills refer to packages by name from this table instead of repeating paths.

| Package | Role | Contents | Must NOT contain / import |
|---|---|---|---|
| `network.infra` | Retrofit setup | `RetrofitFactory` | Feature code |
| `network.datasource` | Everything built with Retrofit | `{Name}DataSource.kt` (single file: `Network*` models, private ApiService, DataSource) | Domain models, mapping |
| `data` | Data access API for the app | `{Name}Repository.kt`, domain models `{Item}.kt` | Business logic, in-memory state, `Network*` in public signatures |
| `domain.{feature}` | Domain state holders | `FavoriteStore` and similar singletons | Network access |
| `ui` | Presentation | ViewModel, UiState, UiModel, composables | `Network*` imports |
| `coroutines`, `utils` | Shared helpers | `suspendRunCatching`, `UserMessage` | Feature code |

Dependency direction: `ui` → `data` / `domain`; only `data` may import `network.datasource.Network*`.

## 2. Tech Stack & Rules
- **UI**: Jetpack Compose Material 3 (No legacy XML, No Material 2).
- **DI**: Hilt (`@HiltViewModel`, `@AndroidEntryPoint`, `@Singleton`).
- **Network**: Retrofit 2 + Kotlinx Serialization. Delegate to `RetrofitFactory` (`com.nlab.androidboot.network.infra.RetrofitFactory`). Never use Gson, Moshi, or raw OkHttp.
- **Async & State**: Coroutines + `StateFlow`. Use `suspendRunCatching` (`com.nlab.androidboot.coroutines`) in suspend functions to prevent swallowing `CancellationException`. Never use RxJava or AsyncTask.
- **Image**: Coil Compose (`AsyncImage`).
- **No Deprecated APIs**: Never use APIs marked `@Deprecated` (Android SDK, AndroidX, Compose, Kotlin/coroutines, or any library), even if they still compile. Use the replacement the deprecation message recommends (e.g. `TabRow` → `PrimaryTabRow` / `SecondaryTabRow`).
  - Training-data memory is not a reliable source for what is current; when unsure, check the compiler output (`./gradlew compileDebugKotlin` warnings containing `deprecated`) or the library's docs/KDoc before writing the call.
  - Before finishing a change, confirm the build output has no new deprecation warnings from the code you touched. If a deprecated API is truly unavoidable (no replacement exists), the developer/agent MUST state why to the user and add a `// TODO` with the planned migration next to the usage.

## 3. String Literals & Localization
- Whenever user-facing strings are hardcoded in code (Compose UI, ViewModel, error messages, etc.) instead of string resources, ALWAYS add a `// TODO 다국어 반영` comment adjacent to the string.
- Example:
  ```kotlin
  Text(text = "확인") // TODO 다국어 반영
  UserMessage(message = "데이터를 불러오지 못했습니다.") // TODO 다국어 반영
  ```

## 4. Extension Functions
- **Always Top-level**: Define all extension functions as top-level functions. Never declare member extension functions inside classes.

