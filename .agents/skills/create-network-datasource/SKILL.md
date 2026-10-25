---
name: create-network-datasource
description: Use when creating or updating network DataSource, Retrofit ApiService, kotlinx.serialization Network models, domain models, and data layer Repository from an API endpoint URL or sample JSON response. Mandatory whenever the user asks to create a datasource, repository, generate API models, or implement Retrofit services for a REST API.
---

# Create Network DataSource & Repository

**Purpose**: Create `network.datasource` (Retrofit ApiService, Network models, DataSource) and `data` (domain models, Repository). Focus only on generation; ignore everything else.

Read the Package Map in `.agents/conventions/core.md` first. Packages: `network.datasource`, `data`.

## Scope
- Produce only the Output Files above. Do not create or edit any other file.

## Input Handling
- Do not ask preliminary questions (names, HTTP method, parameter names) if they can be inferred.
- Derive the PascalCase base name from the domain or resource: `https://api.github.com/repos/...` → `GitHubDataSource` / `GitHubRepository`.
- If only a URL or cURL is given, run `curl -s "{URL}?per_page=1"` to inspect the response.
- Take path variables and query parameters from the URL or spec.
- Generate the files, then verify with `./gradlew compileDebugKotlin`.

## Output Files
1. `network.datasource/{Name}DataSource.kt`: DataSource class, private ApiService, Network models.
2. `data/{Item}.kt`: domain models.
3. `data/{Name}Repository.kt`: Repository class and private `asModel()` mappers.

## Rules

### DataSource
- `@Singleton class {Name}DataSource @Inject constructor(private val retrofitFactory: RetrofitFactory) : {Name}ApiService by retrofitFactory.create("{BaseUrl}/").create()`
- Base URL ends with `/`.
- `private interface {Name}ApiService` lives in the same file. Every endpoint is `suspend`.
- Path variables: `{param}` with `@Path`. Query: `@Query`. Body: `@Body`.
- Return `Network{Item}`, `List<Network{Item}>`, `Network{Item}Page`, or a plain `List<String>` / primitive.

### Network models
- Names start with `Network`. Do not use suffixes.
- Annotate with `@Serializable`. Every property needs `@SerialName("json_key")`, with no exceptions. Without it, R8 obfuscation and snake_case or non-identifier keys break parsing.
- Mirror the API contract. Do not add arbitrary defaults. Use `? = null` only for optional or nullable fields. Entity IDs are `Long`.
- Put nested objects and object arrays in their own `@Serializable` class below the parent, with the `Network` prefix.
- A response that wraps a list with metadata (`products`, `total`, `skip`, `limit`) becomes `Network{Item}Page`.
- Type mapping: string → `String`, ID → `Long`, small integer → `Int`, boolean → `Boolean`, float → `Double`.
- A plain `List<String>` or primitive needs no Network model and no mapping.

### Domain models
- Package `data`. One file per top-level model. Nested models and enums go in the same file.
- Plain names without the `Network` prefix or suffixes. Pure Kotlin: no `@Serializable`, no `@SerialName`, no default values.
- Keep nullability as the API defines it. Do not invent fallback values.
- A closed set of string values becomes an `enum class`. The mapper maps unknown values to an explicit fallback such as `UNKNOWN` instead of crashing.
- **Only fields in use**: include only the fields the requirements need. The source is the user's field list, the `analyze-requirements` output, or the requirement document. Never derive fields from the ViewModel or UiState.
- **Exception**: when no requirement source exists, keep all contract fields and list them in the final message.

### Repository
- `@Singleton class {Name}Repository @Inject constructor(private val {name}DataSource: {Name}DataSource)` in `data`. Inject the DataSource only, never Retrofit or ApiService.
- Methods are `suspend` and return `Result<Domain>` using `suspendRunCatching { ... .asModel() }` from `com.nlab.androidboot.coroutines`. Do not use raw `runCatching`, because it swallows `CancellationException`. Run the mapping inside the block so mapping failures also become `Result.failure`.
- Return domain models only. Never return `Network*` models.
- Mappers are `private fun Network{Item}.asModel()` at the bottom of the file. For lists, use `map(Network{Item}::asModel)`. Do not map in the DataSource, ViewModel, or UI.
- Stay stateless. No business logic, no `MutableStateFlow` or caches, and no state such as favorites or cart. Put those in a domain store or UseCase.

## Workflow
1. Fetch and inspect the sample response (curl).
2. Determine the endpoint: base URL, relative path with placeholders, and function signature.
3. Derive the network model hierarchy: top-level shape, nested classes, and types.
4. Decide the domain fields from the requirements (see Only fields in use).
5. Write the DataSource file.
6. Write the domain model files.
7. Write the Repository file.
8. Run `./gradlew compileDebugKotlin`, then check the rules above.

## Completion
The skill is complete when all of the following hold. Then stop and report. Do not continue with other work.
- The three output files exist at the paths in Output Files, and each follows its section's rules.
- `./gradlew compileDebugKotlin` reports `BUILD SUCCESSFUL`. On failure, fix the cause and rerun until it passes.
- Every rule in the sections above holds for the generated code.

Final report (in Korean, per the global rules):
- Created file paths.
- Domain fields included, and any fields left out.
- Build result.
- Any judgment calls, such as type choices or fields omitted from Network models.

## Example
```kotlin
// network.datasource/GitHubDataSource.kt
@Singleton
class GitHubDataSource @Inject constructor(
    private val retrofitFactory: RetrofitFactory
) : GitHubApiService by retrofitFactory.create("https://api.github.com/").create()

private interface GitHubApiService {
    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(@Path("owner") owner: String, @Path("repo") repo: String): List<NetworkGitHubIssue>
}

@Serializable
data class NetworkGitHubIssue(
    @SerialName("id") val id: Long,
    @SerialName("number") val number: Int,
    @SerialName("title") val title: String
)

// data/GitHubIssue.kt
data class GitHubIssue(val id: Long, val number: Int, val title: String)

// data/GitHubRepository.kt
@Singleton
class GitHubRepository @Inject constructor(
    private val gitHubDataSource: GitHubDataSource
) {
    suspend fun getIssues(owner: String, repo: String): Result<List<GitHubIssue>> = suspendRunCatching {
        gitHubDataSource.getIssues(owner = owner, repo = repo).map(NetworkGitHubIssue::asModel)
    }
}

private fun NetworkGitHubIssue.asModel(): GitHubIssue = GitHubIssue(id = id, number = number, title = title)
```
