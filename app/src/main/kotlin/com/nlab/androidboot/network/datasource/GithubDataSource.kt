package com.nlab.androidboot.network.datasource

import com.nlab.androidboot.network.infra.RetrofitFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Path
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Doohyun
 */
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
    @SerialName("labels_url") val labelsUrl: String,
    @SerialName("comments_url") val commentsUrl: String,
    @SerialName("events_url") val eventsUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("number") val number: Long,
    @SerialName("state") val state: String,
    @SerialName("title") val title: String,
    @SerialName("body") val body: String? = null,
    @SerialName("user") val user: GitHubUser,
    @SerialName("labels") val labels: List<GitHubLabel> = emptyList(),
    @SerialName("assignee") val assignee: GitHubUser? = null,
    @SerialName("assignees") val assignees: List<GitHubUser> = emptyList(),
    @SerialName("milestone") val milestone: GitHubMilestone? = null,
    @SerialName("locked") val locked: Boolean = false,
    @SerialName("active_lock_reason") val activeLockReason: String? = null,
    @SerialName("comments") val comments: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("author_association") val authorAssociation: String,
    @SerialName("sub_issues_summary") val subIssuesSummary: SubIssuesSummary? = null,
    @SerialName("issue_dependencies_summary") val issueDependenciesSummary: IssueDependenciesSummary? = null,
    @SerialName("pull_request") val pullRequest: GitHubPullRequestLinks? = null,
    @SerialName("reactions") val reactions: GitHubReactions? = null,
    @SerialName("timeline_url") val timelineUrl: String,
    @SerialName("performed_via_github_app") val performedViaGithubApp: String? = null,
    @SerialName("state_reason") val stateReason: String? = null,
    @SerialName("pinned_comment") val pinnedComment: String? = null
)

@Serializable
data class GitHubUser(
    @SerialName("login") val login: String,
    @SerialName("id") val id: Long,
    @SerialName("node_id") val nodeId: String,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("gravatar_id") val gravatarId: String? = null,
    @SerialName("url") val url: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("followers_url") val followersUrl: String,
    @SerialName("following_url") val followingUrl: String,
    @SerialName("gists_url") val gistsUrl: String,
    @SerialName("starred_url") val starredUrl: String,
    @SerialName("subscriptions_url") val subscriptionsUrl: String,
    @SerialName("organizations_url") val organizationsUrl: String,
    @SerialName("repos_url") val reposUrl: String,
    @SerialName("events_url") val eventsUrl: String,
    @SerialName("received_events_url") val receivedEventsUrl: String,
    @SerialName("type") val type: String,
    @SerialName("user_view_type") val userViewType: String? = null,
    @SerialName("site_admin") val siteAdmin: Boolean = false
)

@Serializable
data class GitHubLabel(
    @SerialName("id") val id: Long? = null,
    @SerialName("node_id") val nodeId: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("default") val default: Boolean = false
)

@Serializable
data class GitHubMilestone(
    @SerialName("url") val url: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("labels_url") val labelsUrl: String,
    @SerialName("id") val id: Long,
    @SerialName("node_id") val nodeId: String,
    @SerialName("number") val number: Int,
    @SerialName("state") val state: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("creator") val creator: GitHubUser? = null,
    @SerialName("open_issues") val openIssues: Int = 0,
    @SerialName("closed_issues") val closedIssues: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("due_on") val dueOn: String? = null
)

@Serializable
data class GitHubPullRequestLinks(
    @SerialName("url") val url: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("diff_url") val diffUrl: String,
    @SerialName("patch_url") val patchUrl: String,
    @SerialName("merged_at") val mergedAt: String? = null
)

@Serializable
data class SubIssuesSummary(
    @SerialName("total") val total: Int = 0,
    @SerialName("completed") val completed: Int = 0,
    @SerialName("percent_completed") val percentCompleted: Int = 0
)

@Serializable
data class IssueDependenciesSummary(
    @SerialName("blocked_by") val blockedBy: Int = 0,
    @SerialName("total_blocked_by") val totalBlockedBy: Int = 0,
    @SerialName("blocking") val blocking: Int = 0,
    @SerialName("total_blocking") val totalBlocking: Int = 0
)

@Serializable
data class GitHubReactions(
    @SerialName("url") val url: String,
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("+1") val plusOne: Int = 0,
    @SerialName("-1") val minusOne: Int = 0,
    @SerialName("laugh") val laugh: Int = 0,
    @SerialName("hooray") val hooray: Int = 0,
    @SerialName("confused") val confused: Int = 0,
    @SerialName("heart") val heart: Int = 0,
    @SerialName("rocket") val rocket: Int = 0,
    @SerialName("eyes") val eyes: Int = 0
)