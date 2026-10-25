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
- **Review Checkpoint: Consider `Modifier.Node` for draw/layout/pointer behavior that needs its own state**:
  - Applies to any composable, not only list items. When a composable holds `remember { Animatable }` / `remember { mutableStateOf }` / `LaunchedEffect` only to drive a visual or input effect on itself (e.g. a price-change flash background, a shimmer/skeleton loading effect, an animated border or badge pulse, a pressed overlay, a custom clip/shadow), evaluate whether a custom `Modifier.Node` (`ModifierNodeElement` + `DrawModifierNode` / `LayoutModifierNode` / `PointerInputModifierNode`) would be simpler.
  - A node keeps the state and `coroutineScope` inside the modifier, so it adds no composition slots to the host composable, can be reused as `Modifier.xxx()` on any composable, and reads animated values only in the draw/layout phase.
  - This is a review prompt, not a mandate. When the state is only a few `remember`s used in one place and the logic is easy to follow as a composable, prefer the plain composable approach: `Modifier.Node` is a lower-level API with more boilerplate. Apply it when the same effect is reused across several composables, repeats in many lazy-list items, or its state would otherwise force extra recompositions.
  - State the trade-off when proposing either choice. Do not claim a measurable performance gain without measuring (Layout Inspector, recomposition counts, or Macrobenchmark).

## Lightweight Navigation for Multi-Screen
- For small flows (2-4 screens), avoid adding heavy `navigation-compose` (`NavHost`/`NavController`) or Multi-Activity.
- Implement zero-dependency, type-safe navigation using `Screen` + `SaveableStateHolder`:
  1. Define routes: `@Parcelize sealed interface Screen : Parcelable`
  2. Manage backstack as list state: `var backStack by rememberSaveable { mutableStateOf<List<Screen>>(listOf(Screen.Root)) }`
  3. Handle system back press: `BackHandler(enabled = backStack.size > 1) { saveableStateHolder.removeState(currentScreen); backStack = backStack.dropLast(1) }`
  4. Preserve UI and scroll state: `saveableStateHolder.SaveableStateProvider(key = currentScreen) { when (currentScreen) { ... } }`
