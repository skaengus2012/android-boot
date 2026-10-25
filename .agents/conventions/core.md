# Android Boot Architecture & Conventions

Core rules that apply to every task. Topic-specific rules live in separate files; which ones to read for a task is defined in the mapping table in [AGENTS.md](../../AGENTS.md).

- [data-network.md](data-network.md): DataSource, Network/domain models, Repository.
- [viewmodel-state.md](viewmodel-state.md): ViewModel state, UDF, Flow pipelines, concurrency, one-off events.
- [compose-ui.md](compose-ui.md): Compose UI rules, skipping/performance, navigation.

## 1. Tech Stack & Rules
- **UI**: Jetpack Compose Material 3 (No legacy XML, No Material 2).
- **DI**: Hilt (`@HiltViewModel`, `@AndroidEntryPoint`, `@Singleton`).
- **Network**: Retrofit 2 + Kotlinx Serialization. Delegate to `RetrofitFactory` (`com.nlab.androidboot.network.infra.RetrofitFactory`). Never use Gson, Moshi, or raw OkHttp.
- **Async & State**: Coroutines + `StateFlow`. Use `suspendRunCatching` (`com.nlab.androidboot.coroutines`) in suspend functions to prevent swallowing `CancellationException`. Never use RxJava or AsyncTask.
- **Image**: Coil 3 Compose (`AsyncImage`).
- **No Deprecated APIs**: Never use APIs marked `@Deprecated` (Android SDK, AndroidX, Compose, Kotlin/coroutines, or any library), even if they still compile. Use the replacement the deprecation message recommends (e.g. `TabRow` → `PrimaryTabRow` / `SecondaryTabRow`).
  - Training-data memory is not a reliable source for what is current; when unsure, check the compiler output (`./gradlew compileDebugKotlin` warnings containing `deprecated`) or the library's docs/KDoc before writing the call.
  - Before finishing a change, confirm the build output has no new deprecation warnings from the code you touched. If a deprecated API is truly unavoidable (no replacement exists), the developer/agent MUST state why to the user and add a `// TODO` with the planned migration next to the usage.

## 2. String Literals & Localization
- Whenever user-facing strings are hardcoded in code (Compose UI, ViewModel, error messages, etc.) instead of string resources, ALWAYS add a `// TODO 다국어 반영` comment adjacent to the string.
- Example:
  ```kotlin
  Text(text = "확인") // TODO 다국어 반영
  UserMessage(message = "데이터를 불러오지 못했습니다.") // TODO 다국어 반영
  ```
