---
name: create-network-datasource
description: Use when creating or updating network DataSource, Retrofit ApiService, kotlinx.serialization response DTO models, and data layer Repository from an API endpoint URL or sample JSON response. Mandatory whenever the user asks to create a datasource, repository, generate API models, or implement Retrofit services for a REST API.
---

# Create Network DataSource & Repository

## Overview
This skill generates complete, production-ready network **DataSource** and **Repository** files for Android, enforcing single-file encapsulation for Retrofit interfaces and Kotlinx Serialization response models, as well as safe `Result`-wrapped data access in the repository layer.

The workflow creates two coordinated files:
1. **DataSource File**: `app/src/main/kotlin/com/nlab/androidboot/network/datasource/{Name}DataSource.kt`
   - **DataSource Class**: Injected `@Singleton` delegating to Retrofit service via `RetrofitFactory`.
   - **ApiService Interface**: `private` Retrofit interface hidden within the same file.
   - **Response DTO Models**: `@Serializable` Kotlin data classes with mandatory `@SerialName` annotations and safe default values.
2. **Repository File**: `app/src/main/kotlin/com/nlab/androidboot/data/{Name}Repository.kt`
   - **Repository Class**: Injected `@Singleton` receiving `{Name}DataSource`.
   - **Failure Handling**: Handles network/api failures safely via `suspendRunCatching` from `com.nlab.androidboot.coroutines` (rethrowing `CancellationException` to preserve coroutine cancellation and logging exceptions).
   - **Direct Return**: Returns the API Response DTO object directly wrapped in `Result`.

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
- **Generate, Verify, and Complete**: Immediately create both DataSource and Repository files, verify via `./gradlew compileDebugKotlin`, and deliver the result.

---

## Why `@SerialName` is Strictly Mandatory
Every property in every `@Serializable` model MUST have an explicit `@SerialName("key_name")` annotation. No exceptions.
1. **R8 / ProGuard Obfuscation Safety**: Release builds minify and obfuscate field names (e.g., `title` becomes `a`). Even with Kotlinx Serialization compiler plugins, missing keep rules or aggressive optimization can cause runtime parsing failures if `@SerialName` is omitted.
2. **Naming Convention Discrepancies**: APIs commonly use `snake_case` (e.g., `created_at`, `repository_url`) or non-identifier keys (e.g., `+1`, `-1`), whereas Kotlin requires `camelCase` identifiers.
3. **Refactoring Safety**: Renaming a Kotlin property during local refactoring will never accidentally break the network API serialization contract.

---

## Mandatory File Structure & Rules

### 1. DataSource File (`app/src/main/kotlin/com/nlab/androidboot/network/datasource/{Name}DataSource.kt`)

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

// Top-level Response model(s)
@Serializable
data class {Name}Response(
    ...
)

// Nested DTO models
@Serializable
data class {Nested}Model(
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
- **Return Type**: Return `{Name}Response` or `List<{Item}Response>`.

#### Response DTO Rules
- **Annotation**: Every model class must be annotated with `@Serializable`.
- **Explicit SerialName**: EVERY property MUST have `@SerialName("json_key")`.
- **Nullability & Safe Defaults**:
  - Nullable fields: Must have `? = null` default value (e.g., `val body: String? = null`).
  - Collections / Lists: Must default to `emptyList()` (e.g., `val labels: List<GitHubLabel> = emptyList()`).
  - Booleans: Must have default boolean value (e.g., `val locked: Boolean = false`).
  - Counters / Numeric stats: Must have default zero (e.g., `val comments: Int = 0`).
  - Non-null ID/key fields: Use `Long` for entity IDs (e.g., `val id: Long`) to prevent 32-bit integer overflow.
- **Nested Models**:
  - Any nested JSON object or array of objects must be extracted into a separate `@Serializable data class` defined below the parent model in the same file.
  - Model names must be descriptive and PascalCase (e.g., `GitHubUser`, `GitHubLabel`).

---

### 2. Repository File (`app/src/main/kotlin/com/nlab/androidboot/data/{Name}Repository.kt`)

A Repository file MUST follow this exact structure:

```kotlin
package com.nlab.androidboot.data

import com.nlab.androidboot.coroutines.suspendRunCatching
import com.nlab.androidboot.network.datasource.{Name}DataSource
import com.nlab.androidboot.network.datasource.{Name}Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class {Name}Repository @Inject constructor(
    private val {name}DataSource: {Name}DataSource
) {
    suspend fun {methodName}(
        ...
    ): Result<{Name}Response> = suspendRunCatching {
        {name}DataSource.{methodName}(...)
    }
}
```

#### Repository Class Rules
- **Package**: `com.nlab.androidboot.data`.
- **Annotation**: Must have `@Singleton`.
- **Constructor Injection**: Must inject `{Name}DataSource` directly (`@Inject constructor(private val {name}DataSource: {Name}DataSource)`).
- **Api Failure Handling with `suspendRunCatching`**:
  - Always use `com.nlab.androidboot.coroutines.suspendRunCatching`.
  - Method return type must be `Result<{Name}Response>` or `Result<List<{Item}Response>>`.
  - `suspendRunCatching` safely rethrows `CancellationException` and logs unhandled exceptions automatically.
- **Return Value**: Directly return the DataSource's Response DTO wrapped in `Result`. Do not introduce premature domain mapping unless explicitly requested.
- **Strictly Stateless (No Business Logic or State Holders)**: Repositories must act strictly as stateless data coordinators delegating to DataSources. Never place business logic, in-memory state caching (e.g. `MutableStateFlow`), or domain state tracking (such as Favorite toggle or Cart management) inside the Repository. Domain state synchronization must be handled by dedicated domain state holders (e.g. `FavoriteStore`) or UseCases.

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

3. **Derive DTO Hierarchy**:
   - Identify top-level response: object vs array of objects (e.g., `List<GitHubIssueResponse>`).
   - Extract child objects into dedicated data classes.
   - Map field types: string -> `String`, integer ID -> `Long`, small integer -> `Int`, boolean -> `Boolean`, float/double -> `Double`.
   - Apply safe defaults (`? = null`, `emptyList()`, `0`, `false`).

4. **Generate DataSource File**:
   - Write to `app/src/main/kotlin/com/nlab/androidboot/network/datasource/{Name}DataSource.kt`.
   - Ensure imports: `RetrofitFactory`, `SerialName`, `Serializable`, `retrofit2.create`, `javax.inject.*`.

5. **Generate Repository File**:
   - Write to `app/src/main/kotlin/com/nlab/androidboot/data/{Name}Repository.kt`.
   - Ensure imports: `{Name}DataSource`, `{Name}Response`, `com.nlab.androidboot.coroutines.suspendRunCatching`, `javax.inject.*`.
   - Implement `suspend fun` returning `Result<{ResponseType}>` using `suspendRunCatching { ... }`.

6. **Verify**:
   - Check Kotlin compilation using `./gradlew compileDebugKotlin`.
   - Verify all rules (private ApiService, `@Singleton`, `@Inject`, `@SerialName` on all fields, defaults, safe Result via `suspendRunCatching` in Repository).

---

## Red Flags - STOP and Fix
- Missing `@SerialName` on any field ("because the field name matches camelCase already").
- Making `ApiService` public or placing it in a separate file.
- Using `class {Name}DataSource` or `class {Name}Repository` without `@Singleton` or `@Inject`.
- Base URL missing the trailing slash `/`.
- Using `Int` for IDs (IDs should be `Long` or `String`).
- Leaving collection fields without `= emptyList()` default.
- Forgetting `suspend` keyword on ApiService or Repository methods.
- Omitting the Repository or putting Repository in the wrong package instead of `com.nlab.androidboot.data`.
- Using raw `runCatching` instead of `suspendRunCatching` from `com.nlab.androidboot.coroutines`.
- Injecting `Retrofit` or `ApiService` into Repository instead of `{Name}DataSource`.
- Adding business logic, mutable state (`StateFlow`), in-memory caches, or domain state tracking (such as Favorite toggle) inside a Repository instead of using a dedicated domain StateHolder/Store.

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
    ): List<GitHubIssueResponse>
}

@Serializable
data class GitHubIssueResponse(
    @SerialName("id") val id: Long,
    @SerialName("node_id") val nodeId: String,
    @SerialName("url") val url: String,
    @SerialName("repository_url") val repositoryUrl: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String? = null,
    @SerialName("user") val user: GitHubUser,
    @SerialName("labels") val labels: List<GitHubLabel> = emptyList(),
    @SerialName("locked") val locked: Boolean = false,
    @SerialName("comments") val comments: Int = 0
)

@Serializable
data class GitHubUser(
    @SerialName("login") val login: String,
    @SerialName("id") val id: Long,
    @SerialName("avatar_url") val avatarUrl: String
)

@Serializable
data class GitHubLabel(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String,
    @SerialName("color") val color: String? = null
)
```

### 2. Repository: `GithubRepository.kt`
```kotlin
package com.nlab.androidboot.data

import com.nlab.androidboot.coroutines.suspendRunCatching
import com.nlab.androidboot.network.datasource.GitHubIssueResponse
import com.nlab.androidboot.network.datasource.GithubDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubRepository @Inject constructor(
    private val githubDataSource: GithubDataSource
) {
    suspend fun getIssues(
        owner: String,
        repo: String
    ): Result<List<GitHubIssueResponse>> = suspendRunCatching {
        githubDataSource.getIssues(owner = owner, repo = repo)
    }
}
```
