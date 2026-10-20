---
name: create-network-datasource
description: Use when creating or updating network DataSource, Retrofit ApiService, and kotlinx.serialization response DTO models from an API endpoint URL or sample JSON response. Mandatory whenever the user asks to create a datasource, generate API models, or implement Retrofit services for a REST API.
---

# Create Network DataSource

## Overview
This skill generates complete, production-ready network DataSource files for Android, enforcing single-file encapsulation for Retrofit interfaces and Kotlinx Serialization response models.

A DataSource file lives in `app/src/main/kotlin/com/nlab/androidboot/network/datasource/{Name}DataSource.kt` and encapsulates:
1. **DataSource Class**: Injected `@Singleton` delegating to Retrofit service via `RetrofitFactory`.
2. **ApiService Interface**: `private` Retrofit interface hidden within the same file.
3. **Response DTO Models**: `@Serializable` Kotlin data classes with mandatory `@SerialName` annotations and safe default values.

---

## When to Use
- When given an API endpoint URL (e.g., `https://api.github.com/repos/{owner}/{repo}/issues`) to implement a DataSource.
- When generating Retrofit request/response models from sample JSON data or API specifications.
- When creating network layer components conforming to the project's DataSource conventions.

---

## Zero-Friction Input Principle
When the user provides an API endpoint URL, cURL command, or API specification / JSON:
- **Do NOT ask preliminary questions** (such as DataSource name, HTTP method, or parameter names) if they can be reasonably inferred.
- **Infer DataSource Name Automatically**: Derive PascalCase name from the domain or resource path (e.g., `https://api.github.com/repos/...` -> `GithubDataSource`, `https://newsapi.org/v2/top-headlines` -> `NewsDataSource`, `https://api.weatherapi.com/...` -> `WeatherDataSource`).
- **Fetch Sample Data Automatically**: If only an endpoint URL or cURL is provided, immediately run `curl -s "{URL}"` to inspect the response structure.
- **Infer Endpoint & Parameters Automatically**: Extract path variables (e.g., `{id}`, `{owner}`) and query parameters directly from the URL or spec.
- **Generate, Verify, and Complete**: Immediately create the file, verify via `./gradlew compileDebugKotlin`, and deliver the result.


---

## Why `@SerialName` is Strictly Mandatory
Every property in every `@Serializable` model MUST have an explicit `@SerialName("key_name")` annotation. No exceptions.
1. **R8 / ProGuard Obfuscation Safety**: Release builds minify and obfuscate field names (e.g., `title` becomes `a`). Even with Kotlinx Serialization compiler plugins, missing keep rules or aggressive optimization can cause runtime parsing failures if `@SerialName` is omitted.
2. **Naming Convention Discrepancies**: APIs commonly use `snake_case` (e.g., `created_at`, `repository_url`) or non-identifier keys (e.g., `+1`, `-1`), whereas Kotlin requires `camelCase` identifiers.
3. **Refactoring Safety**: Renaming a Kotlin property during local refactoring will never accidentally break the network API serialization contract.

---

## Mandatory File Structure & Rules

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

### 1. DataSource Class Rules
- **Annotation**: Must have `@Singleton`.
- **Constructor Injection**: Must use `@Inject constructor(private val retrofitFactory: RetrofitFactory)`.
- **Interface Delegation**: Must implement and delegate to `{Name}ApiService` via:
  ```kotlin
  : {Name}ApiService by retrofitFactory.create("{BaseUrl}/").create()
  ```
- **Base URL**: Must always end with a trailing slash `/` (Retrofit requirement). Extract domain and path prefixes properly (e.g., `https://api.github.com/`).

### 2. ApiService Interface Rules
- **Visibility**: Must be `private interface {Name}ApiService`. Never expose ApiService outside the DataSource file.
- **Async Functions**: All endpoints must be declared as `suspend fun`.
- **Parameterization**:
  - Variable URL segments must be replaced with `{param}` in the HTTP path and annotated with `@Path("param")`.
  - Query parameters must use `@Query("key")`.
  - Request bodies must use `@Body`.
- **Return Type**: Return `{Name}Response` or `List<{Item}Response>`.

### 3. Response DTO Rules
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
   - Function signature and HTTP method (`@GET suspend fun getIssue(...)`)

3. **Derive DTO Hierarchy**:
   - Identify top-level response: object vs array of objects (e.g., `List<GitHubIssueResponse>`).
   - Extract child objects into dedicated data classes.
   - Map field types: string -> `String`, integer ID -> `Long`, small integer -> `Int`, boolean -> `Boolean`, float/double -> `Double`.
   - Apply safe defaults (`? = null`, `emptyList()`, `0`, `false`).

4. **Generate File**:
   - Write to `app/src/main/kotlin/com/nlab/androidboot/network/datasource/{Name}DataSource.kt`.
   - Ensure imports: `RetrofitFactory`, `SerialName`, `Serializable`, `retrofit2.create`, `javax.inject.*`.

5. **Verify**:
   - Check Kotlin compilation using `./gradlew compileDebugKotlin`.
   - Verify all rules (private ApiService, `@Singleton`, `@Inject`, `@SerialName` on all fields, defaults).

---

## Red Flags - STOP and Fix
- Missing `@SerialName` on any field ("because the field name matches camelCase already").
- Making `ApiService` public or placing it in a separate file.
- Using `class {Name}DataSource` without `@Singleton` or `@Inject`.
- Base URL missing the trailing slash `/`.
- Using `Int` for IDs (IDs should be `Long` or `String`).
- Leaving collection fields without `= emptyList()` default.
- Forgetting `suspend` keyword on ApiService methods.

---

## Canonical Reference Example
See [GithubDataSource.kt](file:///Users/doohyunnam/Git-Repository/android-boot/app/src/main/kotlin/com/nlab/androidboot/network/datasource/GithubDataSource.kt):
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
    suspend fun getIssue(
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
    // ...
)
```
