# ViewModel & State Conventions

Read for tasks that add or change a ViewModel, UiState, Flow pipelines, API-call timing, or one-off UI events.

- **Unidirectional Data Flow (UDF)**:
  - Expose a single `StateFlow<UiState>` from ViewModel.
  - Keep the flow strictly one-way: user action → ViewModel function / input flow → state (reducer) → deterministic mapping to `UiState` → UI. State changes only where the state is held; the mapping step only derives `UiState` from it. Anything with a generated identity (e.g. `UserMessage.id`) therefore belongs in the held state, not in the mapping, otherwise every emission produces a new identity.
    - If keeping this flow makes the logic excessively convoluted, the developer/agent MUST explain why and propose the pragmatic `MutableStateFlow` + explicit function calls pattern to the user (see "Pragmatic Complexity Escalation") instead of breaking the flow silently.
  - Standard LCE pattern:
    ```kotlin
    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val items: List<ItemUiModel>,
            val userMessage: UserMessage? // or List<UserMessage> when messages must be shown in order; see "One-off UI Events"
        ) : UiState
        data class Error(val message: String) : UiState
    }
    ```
  - **Single-File Start**:
    - Start with one file, `{Name}ViewModel.kt`. Put the ViewModel first, then everything it uses at the bottom of the same file: `UiState`, `UiModel`s, enums, and formatter/mapping logic.
    - When the file becomes complex (e.g. many `UiModel`s, or formatter/mapping code that outweighs the ViewModel logic), the developer/agent MUST propose splitting it to the user, with the reason, instead of splitting on their own.
    - UiModels carry meaning (enums, plain values, formatted text), not Compose UI types such as `Color` or `Dp`; the UI maps them to colors and sizes. Annotations such as `@Immutable` are fine.
  - **No Default Values in `UiState` Properties**: Never provide default parameter values in `UiState` (or its child data classes). The ViewModel must explicitly supply all initial values (e.g. via `MutableStateFlow(UiState(...))` or `stateIn(..., initialValue = ...)`), keeping initial state control strictly within the ViewModel.
    - **Exception: `UserMessage.id`**: The `id` default of `UserMessage` (`UUID.randomUUID().mostSignificantBits`) is allowed. It is a generated identity for each message, not an initial value the ViewModel chooses, so it does not conflict with this rule.
- Never reference Android `View` or `Activity` context inside ViewModels.
- ViewModels and UiStates consume domain models from the `data` package returned by Repositories. Never import `network.datasource.Network*` models there (see `data-network.md`).
- **Avoid API Calls in ViewModel `init` (Prefer Lazy Execution via `SharingStarted.Lazily`)**:
  - Avoid launching coroutines or triggering API calls inside ViewModel `init { }`. Doing so causes premature work upon instantiation, complicates unit testing, and triggers network requests before the UI is actively observing.
  - Prioritize lazy reactive execution using `stateIn(scope = viewModelScope, started = SharingStarted.Lazily, initialValue = ...)`:
    - `SharingStarted.Lazily` initiates data fetching only when the UI starts collecting `uiState`, and reliably retains state across temporary screen exits without unwanted automatic refetches.
    - Reserve `SharingStarted.WhileSubscribed(5_000)` strictly for continuous real-time streams (e.g. WebSockets, location tracking) where upstream work must actively halt when invisible.
  - **Pragmatic Complexity Escalation & Consultation**:
    - If combining multiple interdependent features (e.g. pagination + multi-filtering + optimistic updates + retry triggers) causes reactive Flow transformations (`flatMapLatest`, `combine`) to become excessively convoluted or prone to hidden side-effects:
    - The developer/agent MUST explicitly explain the rationale for the complexity to the user and consult whether to adopt the pragmatic `MutableStateFlow` + explicit function calls / `init { }` pattern instead of forcing an over-engineered reactive pipeline.
- **Flow Error Handling & UDF Conventions**:
  - **No Side-Effects in Flow Producers**: Never mutate external state (e.g., calling `showUserMessage()`) inside Flow builder blocks (`flow { }`, `channelFlow { }`) or operators (`onEach`, `map`, `transform`). Errors are state, not side-effects; emit errors downstream as pure data.
    - The operator may run once per collector and again on each re-subscription (e.g. under `WhileSubscribed`), so state changed there is applied a variable number of times and splits the source of truth.
    - Derive state that depends on previous values or user actions with `scan` (reducer). Merging user-action inputs into the same reducer with `merge` is within normal complexity:
      ```kotlin
      merge(
          sourceResults().map(Event::Loaded),
          shownMessageId.filterNotNull().map(Event::MessageShown)
      ).scan(State.Initial) { state, event -> state.reduce(event) }
      ```
    - Keep user actions as `MutableStateFlow` inputs whenever possible: the ViewModel function just sets the value and the pipeline reads it.
    - If a `StateFlow` input becomes convoluted (e.g. the same action must be delivered repeatedly, or losing intermediate values to conflation matters), the developer/agent MUST explain why and propose `Channel`/`SharedFlow` to the user before using it. The ban on them in "One-off UI Events" applies to ViewModel → UI events, not to this UI → ViewModel input.
    - Observation-only side-effects that do not change any state (logging, analytics) are allowed in `onEach`/`catch`.
    - **Complexity Escalation**: If the reducer approach becomes excessively convoluted (e.g. many event types or interdependent inputs), the developer/agent MUST explain why and consult the user about falling back to the pragmatic `MutableStateFlow` + explicit function calls pattern (see "Pragmatic Complexity Escalation"). Never mutate state in `onEach` silently.
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
- **One-off UI Events & Effects as State**:
  - Never use `Channel`, `SharedFlow`, or separate `UiEffect` streams for one-off events (Snackbar, Toast, Dialog, Navigation). Follow Google's ["ViewModel: One-off event antipatterns"](https://medium.com/androiddevelopers/viewmodel-one-off-event-antipatterns-16a1da869b95).
  - Model transient effects directly inside `UiState` by the nature of the message; how it is presented (snackbar, dialog, bottom sheet, ...) is a UI decision and does not affect this choice:
    - **Single message**: If a message only needs to be shown one at a time, use a `Boolean` flag or nullable single `UserMessage?`.
    - **Sequential messages**: If every message must be shown in order and none may be dropped, use `List<UserMessage>`:
      ```kotlin
      // com.nlab.androidboot.utils.UserMessage
      data class UserMessage(
          val message: String,
          val id: Long = UUID.randomUUID().mostSignificantBits
      )
      ```
  - The UI observes the state, triggers display (e.g. in `LaunchedEffect`), and notifies the ViewModel (e.g. `onMessageShown(id)` or dismiss callback) once it is gone, whatever the reason (timeout, action, or dismiss), to clear/reset the consumed state.
