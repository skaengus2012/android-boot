---
name: create-network-datasource
description: Use when creating or updating network DataSource, Retrofit ApiService, kotlinx.serialization Network models, domain models, and data layer Repository from an API endpoint URL or sample JSON response. Mandatory whenever the user asks to create a datasource, repository, generate API models, or implement Retrofit services for a REST API.
---

# Create Network DataSource & Repository

Before generating, read the Package Map in `.agents/conventions/core.md`; packages below are named from it (`network.datasource`, `data`).

## Overview
This skill generates complete, production-ready network **DataSource**, **domain model** and **Repository** files for Android, enforcing single-file encapsulation for Retrofit interfaces and Kotlinx Serialization network models, and safe `Result`-wrapped data access that returns domain models.

The workflow creates three kinds of files:
1. **DataSource File**: `{Name}DataSource.kt` in `network.datasource`
   - **DataSource Class**: Injected `@Singleton` delegating to Retrofit service via `RetrofitFactory`.
   - **ApiService Interface**: `private` Retrofit interface hidden within the same file.
   - **Network Models**: `@Serializable` Kotlin data classes named `Network{Item}` with mandatory `@SerialName` annotations.
2. **Domain Model Files**: `{Item}.kt` in `data`
   - Plain Kotlin data classes (and enums) that the ViewModel layer consumes. They know nothing about the network.
3. **Repository File**: `{Name}Repository.kt` in `data`
   - **Repository Class**: Injected `@Singleton` receiving `{Name}DataSource`.
   - **Failure Handling**: Handles network/api failures safely via `suspendRunCatching` from `com.nlab.androidboot.coroutines` (rethrowing `CancellationException` to preserve coroutine cancellation and logging exceptions).
   - **Mapping**: Converts `Network{Item}` to the domain model with `private` extension functions in the same file, and returns domain models wrapped in `Result`.

---

## When to Use
- When given an API endpoint URL (e.g., `https://api.github.com/repos/{owner}/{repo}/issues`) to implement network/data layer components.
- When generating Retrofit request/response models from sample JSON data or API specifications.
- When creating network DataSource and data layer Repository conforming to the project conventions.

---

## Zero-Friction Input Principle
When the user provides an API endpoint URL, cURL command, or API specification / JSON:
- **Do NOT ask preliminary questions** (such as DataSource/Repository name, HTTP method, or parameter names) if they can be reasonably inferred.
- **Infer Base Name Automatically**: Derive PascalCase name from the domain or resource path (e.g., `https://api.github.com/repos/...` -> `GithubDataSource` & `GithubRepository`, `https://newsapi.org/v2/top-headlines` -> `NewsDataSource` & `NewsRepository`).
- **Fetch Sample Data Automatically**: If only an endpoint URL or cURL is provided, immediately run `curl -s "{URL}"` to inspect the response structure.
- **Infer Endpoint & Parameters Automatically**: Extract path variables (e.g., `{id}`, `{owner}`) and query parameters directly from the URL or spec.
- **Generate, Verify, and Complete**: Immediately create the DataSource, domain model and Repository files, verify via `./gradlew compileDebugKotlin`, and deliver the result.

---

## Why `@SerialName` is Strictly Mandatory
Every property in every `@Serializable` model MUST have an explicit `@SerialName("key_name")` annotation. No exceptions.
1. **R8 / ProGuard Obfuscation Safety**: Release builds minify and obfuscate field names (e.g., `title` becomes `a`). Even with Kotlinx Serialization compiler plugins, missing keep rules or aggressive optimization can cause runtime parsing failures if `@SerialName` is omitted.
2. **Naming Convention Discrepancies**: APIs commonly use `snake_case` (e.g., `created_at`, `repository_url`) or non-identifier keys (e.g., `+1`, `-1`), whereas Kotlin requires `camelCase` identifiers.
3. **Refactoring Safety**: Renaming a Kotlin property during local refactoring will never accidentally break the network API serialization contract.

---

## Mandatory File Structure & Rules

### 1. DataSource File (`{Name}DataSource.kt` in `network.datasource`)

A DataSource file MUST follow this exact sequence:

```kotlin
package com.nlab.androidboot.network.datasource

import com.nlab.androidboot.network.infra.RetrofitFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.create
import retrofit2.http.GET // and other HTTP methods as needed
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class {Name}DataSource @Inject constructor(
    private val retrofitFactory: RetrofitFactory
) : {Name}ApiService by retrofitFactory.create("{BaseUrl}/").create()

private interface {Name}ApiService {
    // Suspend functions with Retrofit annotations
}

// Top-level Network model(s)
@Serializable
data class Network{Item}(
    ...
)

// Nested Network models
@Serializable
data class Network{Nested}(
    ...
)
```

#### DataSource Class Rules
- **Annotation**: Must have `@Singleton`.
- **Constructor Injection**: Must use `@Inject constructor(private val retrofitFactory: RetrofitFactory)`.
- **Interface Delegation**: Must implement and delegate to `{Name}ApiService` via:
  ```kotlin
  : {Name}ApiService by retrofitFactory.create("{BaseUrl}/").create()
  ```
- **Base URL**: Must always end with a trailing slash `/` (Retrofit requirement). Extract domain and path prefixes properly (e.g., `https://api.github.com/`).

#### ApiService Interface Rules
- **Visibility**: Must be `private interface {Name}ApiService`. Never expose ApiService outside the DataSource file.
- **Async Functions**: All endpoints must be declared as `suspend fun`.
- **Parameterization**:
  - Variable URL segments must be replaced with `{param}` in the HTTP path and annotated with `@Path("param")`.
  - Query parameters must use `@Query("key")`.
  - Request bodies must use `@Body`.
- **Return Type**: Return a `Network{Item}`, a `List<Network{Item}>`, a `Network{Item}Page` (see Network Model Rules), or a plain `List<String>` / primitive when the response has no object structure.

#### Network Model Rules
- **Mandatory `Network` Prefix**: Every network (serialization) model MUST start with the `Network` prefix (e.g., `NetworkProduct`, `NetworkGitHubIssue`, `NetworkGitHubUser`). Never use `Response`, `Resource`, `Dto` or other suffixes/prefixes.
- **Annotation**: Every model class must be annotated with `@Serializable`.
- **Explicit SerialName**: EVERY property MUST have `@SerialName("json_key")`.
- **Avoid Default Values**:
  - Avoid assigning arbitrary default values (e.g. `= emptyList()`, `= 0`, `= false`, `= ""`). Model properties strictly according to the API contract.
  - Use nullable types with optional defaults (`? = null`) only when the field is optional or nullable in the API specification.
  - Non-null ID/key fields: Use `Long` for entity IDs (e.g., `val id: Long`) to prevent 32-bit integer overflow.
- **Nested Models**:
  - Any nested JSON object or array of objects must be extracted into a separate `@Serializable data class` defined below the parent model in the same file.
  - Names are descriptive PascalCase with the `Network` prefix (e.g., `NetworkGitHubUser`, `NetworkGitHubLabel`).
- **Envelope / Paging Wrappers**: A response that wraps a list with metadata (e.g. `products`, `total`, `skip`, `limit`) is modeled as `Network{Item}Page` (e.g., `NetworkProductPage`) and mapped to a domain `{Item}Page`.
- **No Model for Plain Values**: A response that is just `List<String>` or a primitive needs no Network model and no mapping. Return it as is.

---

### 2. Domain Model Files (`{Item}.kt` in `data`)

```kotlin
package com.nlab.androidboot.data

data class {Item}(
    val id: Long,
    val title: String,
    ...
)
```

#### Domain Model Rules
- **Package**: `data`. One file per top-level model; nested models and related enums live in the same file.
- **Naming**: Plain names without `Network`, `Resource`, `Response` or `Dto` (e.g., `Product`, `ProductPage`, `GitHubIssue`).
- **Pure Kotlin**: No `@Serializable` / `@SerialName`, no Android or framework dependencies, no default values.
- **Preserve Nullability**: A nullable API field stays nullable (`String?`). Do not invent fallback values.
- **Closed Value Sets Become Enums**: A string field with a fixed set of values (e.g. `"RISE" | "FALL" | "EVEN"`) becomes an `enum class`. The mapper converts unknown strings to an explicit fallback constant (e.g. `UNKNOWN`) instead of crashing.
- **Only Fields in Use**: Include the fields the app needs. Do not copy every API field into the domain model.

---

### 3. Repository File (`{Name}Repository.kt` in `data`)

A Repository file MUST follow this exact structure:

```kotlin
package com.nlab.androidboot.data

import com.nlab.androidboot.coroutines.suspendRunCatching
import com.nlab.androidboot.network.datasource.{Name}DataSource
import com.nlab.androidboot.network.datasource.Network{Item}
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class {Name}Repository @Inject constructor(
    private val {name}DataSource: {Name}DataSource
) {
    suspend fun {methodName}(
        ...
    ): Result<{Item}> = suspendRunCatching {
        {name}DataSource.{methodName}(...).asModel()
    }
}

private fun Network{Item}.asModel(): {Item} = {Item}(
    ...
)
```

#### Repository Class Rules
- **Package**: `data`.
- **Annotation**: Must have `@Singleton`.
- **Constructor Injection**: Must inject `{Name}DataSource` directly (`@Inject constructor(private val {name}DataSource: {Name}DataSource)`).
- **Api Failure Handling with `suspendRunCatching`**:
  - In suspend functions, always use `com.nlab.androidboot.coroutines.suspendRunCatching` instead of raw `runCatching` to prevent swallowing `CancellationException`.
  - Run the mapping inside the `suspendRunCatching` block so mapping failures are captured as `Result.failure` too.
  - `suspendRunCatching` safely rethrows `CancellationException` and logs unhandled exceptions automatically.
- **Return Value**: Return domain models (`Result<{Item}>`, `Result<List<{Item}>>`, `Result<{Item}Page>`), never `Network*` models. Plain `List<String>` / primitive responses are returned as is.
- **Mapping Location**: Mappers are `private` extension functions named `asModel()` at the bottom of the Repository file. Do not put mapping in the DataSource, the ViewModel, or the UI layer. For lists use `map(Network{Item}::asModel)`.
- **Strictly Stateless (No Business Logic or State Holders)**: Repositories must act strictly as stateless data coordinators delegating to DataSources. Mapping field-for-field is allowed; never place business logic, in-memory state caching (e.g. `MutableStateFlow`), or domain state tracking (such as Favorite toggle or Cart management) inside the Repository. Domain state synchronization must be handled by dedicated domain state holders (e.g. `FavoriteStore` in `domain`) or UseCases.

---

## Step-by-Step Workflow

1. **Obtain and Inspect Sample Data**:
   - If an endpoint URL is provided (e.g., `https://api.github.com/repos/JakeWharton/hugo/issues`), fetch a sample response using curl:
     ```bash
     curl -s "{URL}?per_page=1"
     ```
   - Analyze JSON fields, data types, nullability, nested structures, and array types.

2. **Determine Endpoint Configuration**:
   - Base URL (e.g., `https://api.github.com/`)
   - Relative path with placeholders (e.g., `repos/{owner}/{repo}/issues`)
   - Function signature and HTTP method (`@GET suspend fun getIssues(...)`)

3. **Derive Network and Domain Model Hierarchy**:
   - Identify top-level response: object vs array of objects vs paging envelope (e.g., `List<NetworkGitHubIssue>`).
   - Extract child objects into dedicated `Network*` data classes.
   - Map field types: string -> `String`, integer ID -> `Long`, small integer -> `Int`, boolean -> `Boolean`, float/double -> `Double`.
   - Model types strictly according to the API specification without arbitrary default values.
   - Define the matching domain models with only the fields the app uses; turn fixed-value strings into enums.

4. **Generate DataSource File**:
   - Write to `{Name}DataSource.kt` in `network.datasource`.
   - Ensure imports: `RetrofitFactory`, `SerialName`, `Serializable`, `retrofit2.create`, `javax.inject.*`.

5. **Generate Domain Model Files**:
   - Write to `{Item}.kt` in `data`.

6. **Generate Repository File**:
   - Write to `{Name}Repository.kt` in `data`.
   - Ensure imports: `{Name}DataSource`, `Network{Item}`, the domain models, `com.nlab.androidboot.coroutines.suspendRunCatching`, `javax.inject.*`.
   - Implement `suspend fun` returning `Result<{DomainType}>` using `suspendRunCatching { ... .asModel() }` with private `asModel()` extension functions.

7. **Verify**:
   - Check Kotlin compilation using `./gradlew compileDebugKotlin`.
   - Verify all rules (private ApiService, `@Singleton`, `@Inject`, `Network` prefix, `@SerialName` on all network fields, domain models free of serialization annotations, Repository returns only domain models via `suspendRunCatching`).

---

## Red Flags - STOP and Fix
- Naming network models without the `Network` prefix, or using `Response` / `Resource` / `Dto` names.
- Returning a `Network*` model from a Repository, or importing `network.datasource.Network*` in a ViewModel, UiState or composable.
- Putting `@Serializable` / `@SerialName` on a domain model, or defining domain models inside the DataSource file.
- Mapping in the ViewModel or UI layer instead of the Repository's private `asModel()` functions.
- Assigning arbitrary default values to non-optional fields (e.g. `= emptyList()`, `= 0`, `= false`).
- Missing `@SerialName` on any network field ("because the field name matches camelCase already").
- Making `ApiService` public or placing it in a separate file.
- Using `class {Name}DataSource` or `class {Name}Repository` without `@Singleton` or `@Inject`.
- Base URL missing the trailing slash `/`.
- Using `Int` for IDs (IDs should be `Long` or `String`).
- Forgetting `suspend` keyword on ApiService or Repository methods.
- Omitting the Repository or putting Repository in the wrong package instead of `data`.
- Using raw `runCatching` instead of `suspendRunCatching` inside suspend functions (which swallows `CancellationException`).
- Injecting `Retrofit` or `ApiService` into Repository instead of `{Name}DataSource`.
- Adding business logic, mutable state (`StateFlow`), in-memory caches, or domain state tracking (such as Favorite toggle) inside a Repository instead of using a dedicated domain StateHolder/Store.
- Crashing on an unknown enum string in a mapper instead of mapping it to an explicit fallback.

---

## Canonical Reference Examples

### 1. DataSource: `GithubDataSource.kt`
```kotlin
package com.nlab.androidboot.network.datasource

import com.nlab.androidboot.network.infra.RetrofitFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Path
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubDataSource @Inject constructor(
    private val retrofitFactory: RetrofitFactory
) : GithubApiService by retrofitFactory.create("https://api.github.com/").create()

private interface GithubApiService {
    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<NetworkGitHubIssue>
}

@Serializable
data class NetworkGitHubIssue(
    @SerialName("id") val id: Long,
    @SerialName("node_id") val nodeId: String,
    @SerialName("url") val url: String,
    @SerialName("repository_url") val repositoryUrl: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String? = null,
    @SerialName("user") val user: NetworkGitHubUser,
    @SerialName("labels") val labels: List<NetworkGitHubLabel>,
    @SerialName("locked") val locked: Boolean,
    @SerialName("comments") val comments: Int
)

@Serializable
data class NetworkGitHubUser(
    @SerialName("login") val login: String,
    @SerialName("id") val id: Long,
    @SerialName("avatar_url") val avatarUrl: String
)

@Serializable
data class NetworkGitHubLabel(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String,
    @SerialName("color") val color: String? = null
)
```

### 2. Domain Model: `model/GitHubIssue.kt`
```kotlin
package com.nlab.androidboot.data

data class GitHubIssue(
    val id: Long,
    val title: String,
    val body: String?,
    val user: GitHubUser,
    val labels: List<GitHubLabel>,
    val commentCount: Int
)

data class GitHubUser(
    val login: String,
    val id: Long,
    val avatarUrl: String
)

data class GitHubLabel(
    val id: Long?,
    val name: String,
    val color: String?
)
```

### 3. Repository: `GithubRepository.kt`
```kotlin
package com.nlab.androidboot.data

import com.nlab.androidboot.coroutines.suspendRunCatching
import com.nlab.androidboot.network.datasource.GithubDataSource
import com.nlab.androidboot.network.datasource.NetworkGitHubIssue
import com.nlab.androidboot.network.datasource.NetworkGitHubLabel
import com.nlab.androidboot.network.datasource.NetworkGitHubUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubRepository @Inject constructor(
    private val githubDataSource: GithubDataSource
) {
    suspend fun getIssues(
        owner: String,
        repo: String
    ): Result<List<GitHubIssue>> = suspendRunCatching {
        githubDataSource.getIssues(owner = owner, repo = repo).map(NetworkGitHubIssue::asModel)
    }
}

private fun NetworkGitHubIssue.asModel(): GitHubIssue = GitHubIssue(
    id = id,
    title = title,
    body = body,
    user = user.asModel(),
    labels = labels.map(NetworkGitHubLabel::asModel),
    commentCount = comments
)

private fun NetworkGitHubUser.asModel(): GitHubUser = GitHubUser(login = login, id = id, avatarUrl = avatarUrl)

private fun NetworkGitHubLabel.asModel(): GitHubLabel = GitHubLabel(id = id, name = name, color = color)
```

### 4. Paging Envelope (reference)
```kotlin
// network/datasource/ProductDataSource.kt
@Serializable
data class NetworkProductPage(
    @SerialName("products") val products: List<NetworkProduct>,
    @SerialName("total") val total: Int,
    @SerialName("skip") val skip: Int,
    @SerialName("limit") val limit: Int
)

// model/ProductPage.kt
data class ProductPage(val products: List<Product>, val total: Int, val skip: Int, val limit: Int)

// data/ProductRepository.kt
suspend fun getProducts(limit: Int, skip: Int): Result<ProductPage> = suspendRunCatching {
    productDataSource.getProducts(limit = limit, skip = skip).asModel()
}

private fun NetworkProductPage.asModel(): ProductPage = ProductPage(
    products = products.map(NetworkProduct::asModel),
    total = total,
    skip = skip,
    limit = limit
)
```
