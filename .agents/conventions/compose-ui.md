# Compose UI Conventions

Read for tasks that build or change Compose screens, list items, animations, or screen navigation.

## Compose Rules
- Always provide unique keys in lazy lists: `items(items, key = { it.id })`.
- Pass lambda callbacks rather than entire ViewModel instances to leaf composables.
- **Strong Skipping Mode is Assumed ON** (Compose compiler default since Kotlin 2.0.20; never disable it via `featureFlags`/compiler options). Write and review all Compose code under this assumption:
  - Composables with unstable parameters are still skippable. Stable parameters are compared with `equals()`, unstable ones with instance identity (`===`).
  - Lambdas and function references (`viewModel::onClick`) are memoized automatically. Do NOT wrap them in `remember { }` or add `@Stable`/`@Immutable` solely to fix skipping.
  - Do NOT report "unstable parameter" or "lambda not remembered" as a skipping problem in code review. Verify real recomposition with Layout Inspector recomposition counts or Compose compiler reports instead.
  - The real risks are: producing a new, non-equal instance of an unstable type on every emission (e.g. rebuilding a `List` or a class without `equals` each tick), and lambdas capturing values whose instance changes every recomposition. Keep UiModels as plain `data class`es with value-based `equals` so unchanged items stay skippable.
  - Keep `@Immutable` on UiModels only as an explicit contract that they are value types, not as a skipping workaround.
- **Review Checkpoint: Consider `Modifier.Node` for per-item draw/animation state**:
  - When a list item composable holds `remember { Animatable }` / `remember { mutableStateOf }` / `LaunchedEffect` only to drive draw or layout behavior (e.g. a flash/highlight background, a pressed overlay), evaluate whether a custom `Modifier.Node` (`ModifierNodeElement` + `DrawModifierNode` / `LayoutModifierNode`) would be simpler. A node keeps the state and `coroutineScope` inside the modifier, so it adds no composition slots per item, can be reused across items, and reads animated values only in the draw/layout phase.
  - This is a review prompt, not a mandate. For small lists, or when the state is only a few `remember`s and the logic is easy to follow as a composable, prefer the plain composable approach: `Modifier.Node` is a lower-level API with more boilerplate. Apply it when the same draw/animation behavior repeats across many lazy-list items or is reusable as a modifier.
  - State the trade-off when proposing either choice. Do not claim a measurable performance gain without measuring (Layout Inspector, recomposition counts, or Macrobenchmark).

## Lightweight Navigation for Multi-Screen
- For small flows (2-4 screens), avoid adding heavy `navigation-compose` (`NavHost`/`NavController`) or Multi-Activity.
- Implement zero-dependency, type-safe navigation using `Screen` + `SaveableStateHolder`:
  1. Define routes: `@Parcelize sealed interface Screen : Parcelable`
  2. Manage backstack as list state: `var backStack by rememberSaveable { mutableStateOf<List<Screen>>(listOf(Screen.Root)) }`
  3. Handle system back press: `BackHandler(enabled = backStack.size > 1) { saveableStateHolder.removeState(currentScreen); backStack = backStack.dropLast(1) }`
  4. Preserve UI and scroll state: `saveableStateHolder.SaveableStateProvider(key = currentScreen) { when (currentScreen) { ... } }`
