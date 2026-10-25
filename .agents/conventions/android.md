# Android Boot Architecture & Conventions

## 1. Tech Stack & Rules
- **UI**: Jetpack Compose Material 3 (No legacy XML, No Material 2).
- **DI**: Hilt (`@HiltViewModel`, `@AndroidEntryPoint`, `@Singleton`).
- **Network**: Retrofit 2 + Kotlinx Serialization. Delegate to `RetrofitFactory` (`com.nlab.androidboot.network.infra.RetrofitFactory`). Never use Gson, Moshi, or raw OkHttp.
- **Async & State**: Coroutines + `StateFlow`. Use `suspendRunCatching` (`com.nlab.androidboot.coroutines`) in suspend functions to prevent swallowing `CancellationException`. Never use RxJava or AsyncTask.
- **Image**: Coil 3 Compose (`AsyncImage`).

## 2. Data & Network Conventions
- **Single-File Encapsulation**: Response Resource models, private ApiService interface, and DataSource class reside in a single file (`network/datasource/{Name}DataSource.kt`).
- **Resource Model Annotations & Naming**: Every `@Serializable` model MUST define `@SerialName("...")` on all fields. Avoid arbitrary default values (e.g. `= emptyList()`, `= 0`, `= false`); model fields strictly according to the API contract. All response model names MUST end with the `Resource` suffix (e.g., `ProductResource`, `UserResource`, `CategoryResource`).
- **Repository Pattern**:
  - Repositories MUST only orchestrate and delegate calls to DataSources, wrapping them via `suspendRunCatching` and returning `Result<T>`. Follow `.agents/skills/create-network-datasource/SKILL.md`.
  - **No Business Logic or State in Repository**: Repositories must remain stateless data pipes. Never place business logic, in-memory state caching, or domain state management (e.g. Favorite toggle, Cart items, mutable `StateFlow`) inside Repositories. Domain state synchronization must be handled by dedicated domain state holders (e.g. `FavoriteStore`) or UseCases.

## 3. Presentation & UI Conventions
- **Unidirectional Data Flow (UDF)**:
  - Expose a single `StateFlow<UiState>` from ViewModel.
  - Standard LCE pattern:
    ```kotlin
    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val items: List<ItemUiModel>,
            val userMessages: List<UserMessage>
        ) : UiState
        data class Error(val message: String) : UiState
    }
    ```
  - **No Default Values in `UiState` Properties**: Never provide default parameter values in `UiState` (or its child data classes). The ViewModel must explicitly supply all initial values (e.g. via `MutableStateFlow(UiState(...))` or `stateIn(..., initialValue = ...)`), keeping initial state control strictly within the ViewModel.
- **Avoid API Calls in ViewModel `init` (Prefer Lazy Execution via `SharingStarted.Lazily`)**:
  - Avoid launching coroutines or triggering API calls inside ViewModel `init { }`. Doing so causes premature work upon instantiation, complicates unit testing, and triggers network requests before the UI is actively observing.
  - Prioritize lazy reactive execution using `stateIn(scope = viewModelScope, started = SharingStarted.Lazily, initialValue = ...)`:
    - `SharingStarted.Lazily` initiates data fetching only when the UI starts collecting `uiState`, and reliably retains state across temporary screen exits without unwanted automatic refetches.
    - Reserve `SharingStarted.WhileSubscribed(5_000)` strictly for continuous real-time streams (e.g. WebSockets, location tracking) where upstream work must actively halt when invisible.
  - **Pragmatic Complexity Escalation & Consultation**:
    - If combining multiple interdependent features (e.g. pagination + multi-filtering + optimistic updates + retry triggers) causes reactive Flow transformations (`flatMapLatest`, `combine`) to become excessively convoluted or prone to hidden side-effects:
    - The developer/agent MUST explicitly explain the rationale for the complexity to the user and consult whether to adopt the pragmatic `MutableStateFlow` + explicit function calls / `init { }` pattern instead of forcing an over-engineered reactive pipeline.
- **Flow Error Handling & UDF Conventions**:
  - **No Side-Effects in Flow Producers**: Never mutate external state (e.g., calling `showUserMessage()`) inside Flow builder blocks (`flow { }`, `channelFlow { }`). Errors are state, not side-effects; emit errors downstream as pure data.
  - **Two-Tier Pipeline (Private `Result<T>?` vs Public LCE `UiState`)**:
    - *Private internal stream*: Avoid over-engineering internal LCE models. Use standard `Flow<Result<T>?>` where `null` signals loading, and `Result.success` / `Result.failure` conveys outcome.
    - *Public UI state*: Map `Result<T>?` once inside the `combine` block to an explicit sealed interface `UiState` (Loading, Success, Error).
- **Offload Heavy UiState Mapping via `flowOn(Dispatchers.Default)`**:
  - When combining streams or assembling `UiState` involves CPU-bound computation (e.g., mapping/transforming large collections `items.map { ... }`, sorting, filtering, or heavy currency/date formatting), apply `.flowOn(Dispatchers.Default)` before `stateIn` to prevent main-thread jank.
  - **Avoid Overuse**: Apply only where heavy computational overhead exists. Do NOT add `flowOn(Dispatchers.Default)` to trivial combinations (such as merging simple booleans/strings or basic property assignments), as unnecessary thread hops cause overhead.
- **API Call Concurrency & Cancellation Strategy**:
  When invoking asynchronous API calls from ViewModels or call sites, explicitly decide and enforce one of two concurrency strategies:
  1. **Cancel Previous & Run Latest (Restartable)**:
     - *Use cases*: Search query changes, category/tab switches, pull-to-refresh where newer input supersedes stale requests.
     - *Implementation*: Use Flow `*Latest` operators (`flatMapLatest`, `collectLatest`) or cancel prior jobs explicitly (`job?.cancel(); job = viewModelScope.launch { ... }`).
  2. **Ignore New While Active (Throttle-First / Duplicate Prevention)**:
     - *Use cases*: Pagination next-page loading (`loadMore`), duplicate button taps (submit, buy), or duplicate retry triggers while loading.
     - *Implementation*: Maintain a `Job?` reference and guard using its active state (`if (job?.isActive == true) return`).
- **Compose Rules**:
  - Always provide unique keys in lazy lists: `items(items, key = { it.id })`.
  - Pass lambda callbacks rather than entire ViewModel instances to leaf composables.
  - Never reference Android `View` or `Activity` context inside ViewModels.
- **String Literals & Localization**:
  - Whenever user-facing strings are hardcoded in code (Compose UI, ViewModel, error messages, etc.) instead of string resources, ALWAYS add a `// TODO 다국어 반영` comment adjacent to the string.
  - Example:
    ```kotlin
    Text(text = "확인") // TODO 다국어 반영
    UserMessage(message = "데이터를 불러오지 못했습니다.") // TODO 다국어 반영
    ```
- **One-off UI Events & Effects as State**:
  - Never use `Channel`, `SharedFlow`, or separate `UiEffect` streams for one-off events (Snackbar, Toast, Dialog, Navigation). Follow Google's ["ViewModel: One-off event antipatterns"](https://medium.com/androiddevelopers/viewmodel-one-off-event-antipatterns-16a1da869b95).
  - Model transient effects directly inside `UiState` according to the use case:
    - **Single Popup / Dialog**: If only one instance can be displayed at a time, use a `Boolean` (e.g. `isDialogVisible: Boolean`) or nullable single `UserMessage?`.
    - **Sequential / Queued Events (Snackbar, alerts)**: When events can trigger consecutively, use `List<UserMessage>`:
      ```kotlin
      // com.nlab.androidboot.utils.UserMessage
      data class UserMessage(
          val message: String,
          val id: Long = UUID.randomUUID().mostSignificantBits
      )
      ```
  - The UI observes the state, triggers display (e.g. in `LaunchedEffect`), and notifies the ViewModel (e.g. `onMessageShown(id)` or dismiss callback) to clear/reset the consumed state.
- **Lightweight Navigation for Multi-Screen**:
  - For small flows (2-4 screens), avoid adding heavy `navigation-compose` (`NavHost`/`NavController`) or Multi-Activity.
  - Implement zero-dependency, type-safe navigation using `Screen` + `SaveableStateHolder`:
    1. Define routes: `@Parcelize sealed interface Screen : Parcelable`
    2. Manage backstack as list state: `var backStack by rememberSaveable { mutableStateOf<List<Screen>>(listOf(Screen.Root)) }`
    3. Handle system back press: `BackHandler(enabled = backStack.size > 1) { saveableStateHolder.removeState(currentScreen); backStack = backStack.dropLast(1) }`
    4. Preserve UI and scroll state: `saveableStateHolder.SaveableStateProvider(key = currentScreen) { when (currentScreen) { ... } }`
